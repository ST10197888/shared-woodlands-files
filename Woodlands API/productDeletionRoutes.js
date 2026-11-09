const UUID_RE =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const REQUEST_STATUSES = [
    'Pending',
    'Approved',
    'Rejected',
    'Cancelled'
];

module.exports = function registerProductDeletionRoutes(
    app,
    supabase,
    auth,
    notificationService
) {
    const { requireRole, isAdmin, isManager } = auth;

    if (
        !notificationService ||
        typeof notificationService.createNotification !== 'function'
    ) {
        throw new Error(
            'Product deletion routes require the notification service.'
        );
    }

    const { createNotification } = notificationService;

    function dbError(res, error) {
        console.error(
            'Product deletion database error:',
            error?.message || error
        );

        return res.status(500).json({
            error: 'Something went wrong. Please try again.'
        });
    }

    function validUuid(value) {
        return typeof value === 'string' && UUID_RE.test(value);
    }

    // Product IDs in the database are numeric (bigint / serial).
    // Reject anything else before it reaches the database.
    function isValidNumericId(value) {
        return /^\d+$/.test(String(value));
    }

    // Update only the actionable Admin notifications.
    // Informational Manager notifications are left untouched.
    async function updateAdminNotifications(requestId, status) {
        const { error } = await supabase
            .from('notifications')
            .update({
                action_required: false,
                action_status: status
            })
            .eq('type', 'product_deletion_request')
            .eq('action_required', true)
            .contains('data', {
                request_id: requestId
            });

        if (error) {
            console.error(
                'Could not update Admin deletion notifications:',
                error.message
            );

            return false;
        }

        return true;
    }

    async function notifyRequester(request, admin, approved, adminNote) {
        if (!request.requested_by) return;

        const title = approved
            ? 'Product deletion approved'
            : 'Product deletion rejected';

        let message = approved
            ? `Your request to delete "${request.product_title}" was approved.`
            : `Your request to delete "${request.product_title}" was rejected.`;

        if (adminNote) {
            message += ` Admin note: ${adminNote}`;
        }

        try {
            await createNotification({
                user_id: request.requested_by,
                actor_id: admin.id,
                type: 'product_deletion_result',
                title,
                message,
                data: {
                    request_id: request.id,
                    product_id: request.product_id,
                    product_title: request.product_title,
                    status: approved ? 'Approved' : 'Rejected'
                }
            });
        } catch (error) {
            console.error(
                'Could not notify the requesting Manager:',
                error.message
            );
        }
    }

    // Claim a pending request before processing it.
    // The status condition prevents two Admins from processing it simultaneously.
    async function claimRequest(request, admin, adminNote) {
        const { data, error } = await supabase
            .from('product_deletion_requests')
            .update({
                status: 'Approved',
                admin_note: adminNote || null,
                resolved_at: new Date().toISOString(),
                resolved_by: admin.id
            })
            .eq('id', request.id)
            .eq('status', 'Pending')
            .select()
            .maybeSingle();

        if (error) return { error };

        if (!data) {
            return { conflict: true };
        }

        return { request: data };
    }

    // Restore a claimed request if the actual product deletion fails.
    async function resetClaimedRequest(requestId) {
        const { error } = await supabase
            .from('product_deletion_requests')
            .update({
                status: 'Pending',
                admin_note: null,
                resolved_at: null,
                resolved_by: null
            })
            .eq('id', requestId)
            .eq('status', 'Approved');

        if (error) {
            console.error(
                'Could not reset product deletion request:',
                error.message
            );
        }
    }

    // The only function that physically deletes a product.
    // It is called by Admin approval or direct Admin deletion.
    async function deleteProductAsAdmin(
        productId,
        admin,
        req,
        pendingRequest = null,
        adminNote = ''
    ) {
        let approvedRequest = null;

        if (pendingRequest) {
            const claim = await claimRequest(
                pendingRequest,
                admin,
                adminNote
            );

            if (claim.error) {
                return { error: claim.error };
            }

            if (claim.conflict) {
                return { conflict: true };
            }

            approvedRequest = claim.request;
        }

        const { data: deleted, error: deleteError } = await supabase
            .from('products')
            .delete()
            .eq('id', productId)
            .select();

        if (deleteError) {
            if (approvedRequest) {
                await resetClaimedRequest(approvedRequest.id);
            }

            return { error: deleteError };
        }

        if (!deleted || deleted.length === 0) {
            if (approvedRequest) {
                await resetClaimedRequest(approvedRequest.id);
            }

            return { notFound: true };
        }

        if (approvedRequest) {
            await updateAdminNotifications(
                approvedRequest.id,
                'Accepted'
            );

            await notifyRequester(
                approvedRequest,
                admin,
                true,
                adminNote
            );

            auth.audit('product_deletion_approved', req, {
                request_id: approvedRequest.id,
                product_id: String(productId)
            });
        }

        auth.audit('product_deleted', req, {
            product_id: String(productId),
            approved_request: approvedRequest?.id || null
        });

        return {
            product: deleted[0],
            request: approvedRequest
        };
    }


    // MANAGER DELETION REQUEST
    //
    // Managers cannot physically delete shared catalogue products.
    // They can submit a request for Admin approval.

    app.post(
        '/api/products/:id/deletion-requests',
        requireRole('Manager'),
        async (req, res) => {
            const body = req.body || {};

            if (
                body.reason !== undefined &&
                typeof body.reason !== 'string'
            ) {
                return res.status(400).json({
                    error: 'The deletion reason must be text.'
                });
            }

            const reason = typeof body.reason === 'string'
                ? body.reason.trim().slice(0, 1000)
                : '';

            const { data: product, error: productError } = await supabase
                .from('products')
                .select('*')
                .eq('id', req.params.id)
                .maybeSingle();

            if (productError) return dbError(res, productError);

            if (!product) {
                return res.status(404).json({
                    error: 'Product not found.'
                });
            }

            const { data: existing, error: existingError } = await supabase
                .from('product_deletion_requests')
                .select('id')
                .eq('product_id', String(product.id))
                .eq('status', 'Pending')
                .maybeSingle();

            if (existingError) return dbError(res, existingError);

            if (existing) {
                return res.status(409).json({
                    error: 'A deletion request is already pending for this product.'
                });
            }

            // Every active Admin and every other active Manager is notified.
            const { data: staff, error: staffError } = await supabase
                .from('app_users')
                .select('id, full_name, role')
                .eq('active', true);

            if (staffError) return dbError(res, staffError);

            const recipients = (staff || []).filter(user =>
                String(user.id) !== String(req.user.id) &&
                (
                    user.role === 'Admin' ||
                    isManager(user)
                )
            );

            const admins = recipients.filter(user =>
                user.role === 'Admin'
            );

            if (admins.length === 0) {
                return res.status(503).json({
                    error: 'No active administrator is available to review deletion requests.'
                });
            }

            const productTitle = product.title || String(product.id);

            const { data: request, error: requestError } = await supabase
                .from('product_deletion_requests')
                .insert([{
                    product_id: String(product.id),
                    product_title: productTitle,
                    product_snapshot: product,
                    requested_by: req.user.id,
                    requested_by_name: req.user.full_name || '',
                    requested_by_email: req.user.email || '',
                    reason: reason || null
                }])
                .select()
                .single();

            if (requestError) {
                if (requestError.code === '23505') {
                    return res.status(409).json({
                        error: 'A deletion request is already pending for this product.'
                    });
                }

                return dbError(res, requestError);
            }

            // Track created notifications so a failed batch can be cleaned up.
            const notificationIds = [];

            try {
                for (const recipient of recipients) {
                    const adminRecipient = recipient.role === 'Admin';

                    const notification = await createNotification({
                        user_id: recipient.id,
                        actor_id: req.user.id,
                        type: 'product_deletion_request',
                        title: 'Product deletion request',
                        message:
                            `${req.user.full_name || req.user.email} ` +
                            `requested deletion of "${productTitle}".`,
                        data: {
                            request_id: request.id,
                            product_id: String(product.id),
                            product_title: productTitle,
                            requested_by: req.user.id,
                            requested_by_name: req.user.full_name || ''
                        },
                        action_required: adminRecipient,
                        action_status: adminRecipient ? 'Pending' : 'None'
                    });

                    notificationIds.push(notification.id);
                }
            } catch (notificationError) {
                console.error(
                    'Product deletion notification creation failed:',
                    notificationError.message
                );

                // Remove any notifications already created for this request.
                if (notificationIds.length > 0) {
                    const { error: cleanupError } = await supabase
                        .from('notifications')
                        .delete()
                        .in('id', notificationIds);

                    if (cleanupError) {
                        console.error(
                            'Notification cleanup failed:',
                            cleanupError.message
                        );
                    }
                }

                // Do not leave a pending request without its notifications.
                const { error: requestCleanupError } = await supabase
                    .from('product_deletion_requests')
                    .delete()
                    .eq('id', request.id);

                if (requestCleanupError) {
                    console.error(
                        'Deletion request cleanup failed:',
                        requestCleanupError.message
                    );
                }

                return res.status(500).json({
                    error: 'The deletion request could not be submitted. Please try again.'
                });
            }

            auth.audit('product_deletion_requested', req, {
                request_id: request.id,
                product_id: String(product.id),
                notified_users: recipients.length
            });

            return res.status(201).json({
                message: 'Deletion request submitted. An administrator must approve it.',
                request
            });
        }
    );


    // REQUEST HISTORY
    //
    // Admins can see all requests.
    // Managers can see only the requests they submitted.

    app.get(
        '/api/product-deletion-requests',
        requireRole('Admin', 'Manager'),
        async (req, res) => {
            let query = supabase
                .from('product_deletion_requests')
                .select('*')
                .order('created_at', { ascending: false });

            if (!isAdmin(req.user)) {
                query = query.eq('requested_by', req.user.id);
            }

            if (typeof req.query.status === 'string' && req.query.status.trim()) {
                const status = req.query.status.trim();

                if (!REQUEST_STATUSES.includes(status)) {
                    return res.status(400).json({
                        error: 'Invalid deletion request status.'
                    });
                }

                query = query.eq('status', status);
            }

            const { data, error } = await query;

            if (error) return dbError(res, error);

            return res.json(data || []);
        }
    );


    // ADMIN APPROVAL
    //
    // A successful approval physically deletes the product.

    app.post(
        '/api/product-deletion-requests/:id/approve',
        requireRole('Admin'),
        async (req, res) => {
            if (!validUuid(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid deletion request ID.'
                });
            }

            const adminNote = typeof req.body?.admin_note === 'string'
                ? req.body.admin_note.trim().slice(0, 500)
                : '';

            const { data: request, error: requestError } = await supabase
                .from('product_deletion_requests')
                .select('*')
                .eq('id', req.params.id)
                .eq('status', 'Pending')
                .maybeSingle();

            if (requestError) return dbError(res, requestError);

            if (!request) {
                return res.status(404).json({
                    error: 'No pending deletion request was found.'
                });
            }

            const result = await deleteProductAsAdmin(
                request.product_id,
                req.user,
                req,
                request,
                adminNote
            );

            if (result.error) return dbError(res, result.error);

            if (result.conflict) {
                return res.status(409).json({
                    error: 'This deletion request has already been processed.'
                });
            }

            if (result.notFound) {
                return res.status(404).json({
                    error: 'The product no longer exists. The request remains pending for review.'
                });
            }

            return res.json({
                message: 'Product deletion approved.',
                request: result.request,
                product: result.product
            });
        }
    );


    // ADMIN REJECTION
    //
    // The product is left unchanged.

    app.post(
        '/api/product-deletion-requests/:id/reject',
        requireRole('Admin'),
        async (req, res) => {
            if (!validUuid(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid deletion request ID.'
                });
            }

            const adminNote = typeof req.body?.admin_note === 'string'
                ? req.body.admin_note.trim().slice(0, 500)
                : '';

            const { data: rejected, error } = await supabase
                .from('product_deletion_requests')
                .update({
                    status: 'Rejected',
                    admin_note: adminNote || null,
                    resolved_at: new Date().toISOString(),
                    resolved_by: req.user.id
                })
                .eq('id', req.params.id)
                .eq('status', 'Pending')
                .select()
                .maybeSingle();

            if (error) return dbError(res, error);

            if (!rejected) {
                return res.status(409).json({
                    error: 'This deletion request has already been processed.'
                });
            }

            await updateAdminNotifications(
                rejected.id,
                'Declined'
            );

            await notifyRequester(
                rejected,
                req.user,
                false,
                adminNote
            );

            auth.audit('product_deletion_rejected', req, {
                request_id: rejected.id,
                product_id: rejected.product_id
            });

            return res.json({
                message: 'Product deletion request rejected.',
                request: rejected
            });
        }
    );


    // DIRECT ADMIN DELETION
    //
    // Admins do not need approval.
    // If a request is already pending, direct deletion also resolves it.

    app.delete(
        '/api/products/:id',
        requireRole('Admin'),
                async (req, res) => {
            // Product IDs are strings, not numbers.
            if (!req.params.id || String(req.params.id).trim() === '') {
                return res.status(400).json({
                    error: 'Invalid product ID.'
                });
            }

            const { data: pendingRequest, error: requestError } = await supabase
                .from('product_deletion_requests')
                .select('*')
                .eq('product_id', String(req.params.id))
                .eq('status', 'Pending')
                .maybeSingle();

            if (requestError) return dbError(res, requestError);

            const result = await deleteProductAsAdmin(
                req.params.id,
                req.user,
                req,
                pendingRequest || null,
                pendingRequest
                    ? 'Deleted directly by an administrator.'
                    : ''
            );

            if (result.error) return dbError(res, result.error);

            if (result.conflict) {
                return res.status(409).json({
                    error: 'The pending deletion request is already being processed.'
                });
            }

            if (result.notFound) {
                return res.status(404).json({
                    error: 'Product not found.'
                });
            }

            return res.json({
                message: 'Product deleted.',
                data: result.product
            });
        }
    );
};