
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

module.exports = function registerEmailChangeRoutes(
    app,
    supabase,
    auth,
    notificationService
) {
    const { requireAuth, requireRole } = auth;

    const createNotification =
        notificationService?.createNotification;

    function dbError(res, error, context) {
        console.error(
            `${context}:`,
            error?.message || error
        );

        return res.status(500).json({
            error: 'Something went wrong. Please try again.'
        });
    }

    // Notifications must never prevent an email-change operation.
    async function notifyUser({
        userId,
        actorId = null,
        type,
        title,
        message,
        data = {}
    }) {
        if (!createNotification) {
            console.error(
                'Email change notification service is unavailable.'
            );
            return;
        }

        try {
            await createNotification({
                user_id: userId,
                actor_id: actorId,
                type,
                title,
                message,
                data,
                action_required: false
            });
        } catch (error) {
            console.error(
                'Email change notification failed:',
                error?.message || error
            );
        }
    }

    // Safely return a request to pending after a recoverable failure.
    async function resetRequest(requestId) {
        const { error } = await supabase
            .from('email_change_requests')
            .update({ status: 'pending' })
            .eq('id', requestId)
            .eq('status', 'processing');

        if (error) {
            console.error(
                'CRITICAL: Could not reset email change request:',
                error.message
            );
        }

        return !error;
    }

    // Admin creates a request. The user's email is not changed here.
    app.post(
        '/api/email-change-requests',
        requireRole('Admin'),
        async (req, res) => {
            try {
                const userId = String(
                    req.body?.user_id || ''
                ).trim();

                const requestedEmail = String(
                    req.body?.requested_email || ''
                ).trim().toLowerCase();

                if (
                    !userId ||
                    !EMAIL_RE.test(requestedEmail)
                ) {
                    return res.status(400).json({
                        error: 'A valid user ID and requested email are required.'
                    });
                }

                const { data: target, error: targetError } =
                    await supabase
                        .from('app_users')
                        .select('id, email, full_name')
                        .eq('id', userId)
                        .maybeSingle();

                if (targetError) {
                    return dbError(
                        res,
                        targetError,
                        'Email change target lookup'
                    );
                }

                if (!target) {
                    return res.status(404).json({
                        error: 'User account not found.'
                    });
                }

                const currentEmail = String(
                    target.email || ''
                ).trim().toLowerCase();

                if (currentEmail === requestedEmail) {
                    return res.status(400).json({
                        error: 'The requested email is already on this account.'
                    });
                }

                // Check whether another profile owns the requested email.
                const { data: existing, error: existingError } =
                    await supabase
                        .from('app_users')
                        .select('id')
                        .ilike('email', requestedEmail)
                        .neq('id', userId)
                        .limit(1);

                if (existingError) {
                    return dbError(
                        res,
                        existingError,
                        'Email availability check'
                    );
                }

                if (existing?.length) {
                    return res.status(409).json({
                        error: 'That email address is already assigned to another account.'
                    });
                }

                // Check for an existing pending request.
                const { data: pending, error: pendingError } =
                    await supabase
                        .from('email_change_requests')
                        .select('id')
                        .eq('user_id', userId)
                        .eq('status', 'pending')
                        .limit(1)
                        .maybeSingle();

                if (pendingError) {
                    return dbError(
                        res,
                        pendingError,
                        'Pending email request lookup'
                    );
                }

                if (pending) {
                    return res.status(409).json({
                        error: 'This user already has a pending email change request.'
                    });
                }

                const { data: request, error: insertError } =
                    await supabase
                        .from('email_change_requests')
                        .insert([{
                            user_id: userId,
                            requested_by: req.user.id,
                            current_email: currentEmail,
                            requested_email: requestedEmail,
                            status: 'pending'
                        }])
                        .select(
                            'id, user_id, current_email, requested_email, status, created_at'
                        )
                        .single();

                if (insertError) {
                    if (insertError.code === '23505') {
                        return res.status(409).json({
                            error: 'This user already has a pending email change request.'
                        });
                    }

                    return dbError(
                        res,
                        insertError,
                        'Email change request creation'
                    );
                }

                auth.audit('email_change_requested', req, {
                    target_user: userId,
                    request_id: request.id
                });

                // Notify the account holder.
                await notifyUser({
                    userId,
                    actorId: req.user.id,
                    type: 'email_change_requested',
                    title: 'Email change requested',
                    message:
                        `An administrator has requested to change your email address to ${requestedEmail}. Sign in to review and approve or reject this request.`,
                    data: {
                        request_id: request.id
                    }
                });

                return res.status(201).json({
                    message: 'Email change request created. The user must sign in and approve it.',
                    request
                });
            } catch (error) {
                console.error(
                    'Email change request error:',
                    error?.message || error
                );

                return res.status(500).json({
                    error: 'Unable to create the email change request.'
                });
            }
        }
    );

    // A signed-in user can retrieve only their own pending request.
    app.get(
        '/api/email-change-requests/me',
        requireAuth,
        async (req, res) => {
            try {
                const { data, error } = await supabase
                    .from('email_change_requests')
                    .select(
                        'id, current_email, requested_email, status, created_at'
                    )
                    .eq('user_id', req.user.id)
                    .eq('status', 'pending')
                    .order('created_at', { ascending: false })
                    .limit(1);

                if (error) {
                    return dbError(
                        res,
                        error,
                        'Pending email request retrieval'
                    );
                }

                return res.json(data?.[0] || null);
            } catch (error) {
                console.error(
                    'Pending email request error:',
                    error?.message || error
                );

                return res.status(500).json({
                    error: 'Unable to retrieve your email change request.'
                });
            }
        }
    );

    // Only the account holder can accept or reject their own request.
    app.post(
        '/api/email-change-requests/:id/decision',
        requireAuth,
        async (req, res) => {
            const decision = String(
                req.body?.decision || ''
            ).trim().toLowerCase();

            if (!['accept', 'reject'].includes(decision)) {
                return res.status(400).json({
                    error: 'Decision must be accept or reject.'
                });
            }

            try {
                // Claim the request. This prevents two simultaneous decisions.
                const claimUpdate = decision === 'accept'
                    ? { status: 'processing' }
                    : {
                        status: 'rejected',
                        decided_at: new Date().toISOString()
                    };

                const { data: claimed, error: claimError } =
                    await supabase
                        .from('email_change_requests')
                        .update(claimUpdate)
                        .eq('id', req.params.id)
                        .eq('user_id', req.user.id)
                        .eq('status', 'pending')
                        .select('*')
                        .maybeSingle();

                if (claimError) {
                    return dbError(
                        res,
                        claimError,
                        'Email change decision claim'
                    );
                }

                if (!claimed) {
                    return res.status(404).json({
                        error: 'No pending email change request was found for your account.'
                    });
                }

                // Rejection does not change either email record.
                if (decision === 'reject') {
                    auth.audit('email_change_rejected', req, {
                        request_id: claimed.id
                    });

                    await notifyUser({
                        userId: claimed.requested_by,
                        actorId: req.user.id,
                        type: 'email_change_rejected',
                        title: 'Email change rejected',
                        message:
                            'The user rejected your requested email address change.',
                        data: {
                            request_id: claimed.id,
                            target_user_id: req.user.id
                        }
                    });

                    return res.json({
                        message: 'Email change request rejected. Your email address was not changed.'
                    });
                }

                // Confirm that the profile still has the original email.
                const { data: profile, error: profileError } =
                    await supabase
                        .from('app_users')
                        .select('id, email')
                        .eq('id', req.user.id)
                        .maybeSingle();

                if (profileError || !profile) {
                    await resetRequest(claimed.id);

                    if (profileError) {
                        return dbError(
                            res,
                            profileError,
                            'Email change profile lookup'
                        );
                    }

                    return res.status(404).json({
                        error: 'Your account profile could not be found.'
                    });
                }

                if (
                    String(profile.email || '').trim().toLowerCase() !==
                    String(claimed.current_email || '').trim().toLowerCase()
                ) {
                    const { error: cancelError } = await supabase
                        .from('email_change_requests')
                        .update({
                            status: 'cancelled',
                            decided_at: new Date().toISOString()
                        })
                        .eq('id', claimed.id)
                        .eq('status', 'processing');

                    if (cancelError) {
                        console.error(
                            'Could not cancel outdated email request:',
                            cancelError.message
                        );
                    }

                    auth.audit('email_change_cancelled', req, {
                        request_id: claimed.id,
                        reason: 'current_email_changed'
                    });

                    await notifyUser({
                        userId: claimed.requested_by,
                        actorId: req.user.id,
                        type: 'email_change_cancelled',
                        title: 'Email change request cancelled',
                        message:
                            'The account email changed before the request was approved. Please submit a new request if the change is still required.',
                        data: {
                            request_id: claimed.id,
                            target_user_id: req.user.id
                        }
                    });

                    return res.status(409).json({
                        error: 'Your account email has changed since this request was created. Ask an administrator to submit a new request.'
                    });
                }

                // Update Supabase Auth first.
                // The new email is deliberately marked as confirmed.
                const { data: authUpdate, error: authUpdateError } =
                    await supabase.auth.admin.updateUserById(
                        req.user.id,
                        {
                            email: claimed.requested_email,
                            email_confirm: true
                        }
                    );

                if (authUpdateError || !authUpdate?.user) {
                    await resetRequest(claimed.id);

                    auth.audit('email_change_auth_update_failed', req, {
                        request_id: claimed.id
                    });

                    console.error(
                        'Supabase Auth email update failed:',
                        authUpdateError?.message || 'No user returned.'
                    );

                    return res.status(409).json({
                        error: 'Supabase could not update that email address. It may already be in use. Your existing email remains unchanged.'
                    });
                }

                // Keep the app profile synchronised with Supabase Auth.
                const {
                    data: updatedProfile,
                    error: profileUpdateError
                } = await supabase
                    .from('app_users')
                    .update({
                        email: claimed.requested_email
                    })
                    .eq('id', req.user.id)
                    .select('id')
                    .maybeSingle();

                if (profileUpdateError || !updatedProfile) {
                    const rollbackResult =
                        await supabase.auth.admin.updateUserById(
                            req.user.id,
                            {
                                email: claimed.current_email,
                                email_confirm: true
                            }
                        );

                    if (rollbackResult.error || !rollbackResult.data?.user) {
                        console.error(
                            'CRITICAL: Auth email rollback failed:',
                            rollbackResult.error?.message ||
                            'No user returned.'
                        );

                        auth.audit('email_change_rollback_failed', req, {
                            request_id: claimed.id
                        });

                        await notifyUser({
                            userId: claimed.requested_by,
                            actorId: req.user.id,
                            type: 'email_change_attention_required',
                            title: 'Email change requires attention',
                            message:
                                'An email change could not be synchronised correctly. Administrator intervention is required.',
                            data: {
                                request_id: claimed.id,
                                target_user_id: req.user.id
                            }
                        });

                        // Keep the request processing for reconciliation.
                        return res.status(500).json({
                            error: 'The email update needs administrator attention. Do not retry this request; contact support.'
                        });
                    }

                    await resetRequest(claimed.id);

                    auth.audit('email_change_profile_update_failed', req, {
                        request_id: claimed.id
                    });

                    const updateError = profileUpdateError ||
                        new Error(
                            'The app user profile was not updated.'
                        );

                    return dbError(
                        res,
                        updateError,
                        'Email change profile update'
                    );
                }

                // Both Auth and app_users have now been updated.
                const { error: finishError } = await supabase
                    .from('email_change_requests')
                    .update({
                        status: 'accepted',
                        decided_at: new Date().toISOString()
                    })
                    .eq('id', claimed.id)
                    .eq('status', 'processing');

                if (finishError) {
                    console.error(
                        'Email changed but request finalisation failed:',
                        finishError.message
                    );

                    auth.audit('email_change_finalisation_failed', req, {
                        request_id: claimed.id
                    });

                    await notifyUser({
                        userId: claimed.requested_by,
                        actorId: req.user.id,
                        type: 'email_change_attention_required',
                        title: 'Email change requires attention',
                        message:
                            'The account email was updated, but the request status could not be finalised. Administrator reconciliation is required.',
                        data: {
                            request_id: claimed.id,
                            target_user_id: req.user.id
                        }
                    });

                    return res.status(500).json({
                        error: 'Your email was changed, but the request status could not be finalised. Contact support before retrying.'
                    });
                }

                auth.audit('email_change_accepted', req, {
                    request_id: claimed.id
                });

                // Notify the administrator who created the request.
                await notifyUser({
                    userId: claimed.requested_by,
                    actorId: req.user.id,
                    type: 'email_change_accepted',
                    title: 'Email change approved',
                    message:
                        `The user approved the email change. Their email address is now ${claimed.requested_email}.`,
                    data: {
                        request_id: claimed.id,
                        target_user_id: req.user.id
                    }
                });

                return res.json({
                    message: 'Your email address has been updated and confirmed. Please sign in again using your new email address.'
                });
            } catch (error) {
                console.error(
                    'Email change decision error:',
                    error?.message || error
                );

                return res.status(500).json({
                    error: 'Unable to process the email change decision.'
                });
            }
        }
    );
};
