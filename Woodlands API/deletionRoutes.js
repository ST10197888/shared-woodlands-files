
const crypto = require('crypto');
const { sendRestoreCodeEmail } = require('./emailService');

const CODE_MINUTES = 15;
const MAX_ATTEMPTS = 5;
const RESEND_SECONDS = 60;
const PROCESSING_TIMEOUT_MINUTES = 15;

const DELETION_STATUSES = [
    'Pending',
    'Processing',
    'Approved',
    'Rejected',
    'Cancelled'
];

const UUID_REGEX =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function normaliseEmail(value) {
    return typeof value === 'string'
        ? value.trim().toLowerCase()
        : '';
}

function escapeLike(value) {
    return value.replace(/[\\%_]/g, '\\$&');
}

function hashCode(code, email) {
    return crypto
        .createHash('sha256')
        .update(`${email}:${code}`)
        .digest('hex');
}

function sameHash(a, b) {
    const left = Buffer.from(String(a || ''));
    const right = Buffer.from(String(b || ''));

    return left.length === right.length &&
        crypto.timingSafeEqual(left, right);
}

module.exports = function registerDeletionRoutes(app, supabase, auth) {
    const { requireAuth, requireRole, isAdmin } = auth;

    function dbError(res, error, context = 'Database error') {
        console.error(`${context}:`, error?.message || error);

        return res.status(500).json({
            error: 'Something went wrong. Please try again.'
        });
    }

    function validRequestId(id) {
        return typeof id === 'string' && UUID_REGEX.test(id);
    }

    function audit(event, req, details = {}) {
        try {
            if (typeof auth.audit === 'function') {
                auth.audit(event, req, details);
            }
        } catch (error) {
            console.error(
                `Audit logging failed (${event}):`,
                error.message
            );
        }
    }

    async function releaseProcessing(id) {
        const { error } = await supabase
            .from('deletion_requests')
            .update({
                status: 'Pending',
                processing_started_at: null
            })
            .eq('id', id)
            .eq('status', 'Processing');

        if (error) {
            console.error(
                'Could not release account deletion processing lock:',
                error.message
            );

            return false;
        }

        return true;
    }

    async function failProcessing(
        res,
        id,
        statusCode,
        message,
        error = null,
        context = 'Account deletion',
        req = null
    ) {
        await releaseProcessing(id);

        if (req) {
            audit('account_deletion_failed', req, {
                request_id: id,
                reason: context
            });
        }

        if (error) {
            return dbError(res, error, context);
        }

        return res.status(statusCode).json({ error: message });
    }

    async function claimDeletionRequest(id) {
        const now = new Date();
        const nowIso = now.toISOString();

        const { data: pending, error: pendingError } = await supabase
            .from('deletion_requests')
            .update({
                status: 'Processing',
                processing_started_at: nowIso
            })
            .eq('id', id)
            .eq('status', 'Pending')
            .select('*')
            .maybeSingle();

        if (pendingError) {
            return { error: pendingError };
        }

        if (pending) {
            return { request: pending };
        }

        const { data: existing, error: existingError } = await supabase
            .from('deletion_requests')
            .select('*')
            .eq('id', id)
            .maybeSingle();

        if (existingError) {
            return { error: existingError };
        }

        if (!existing) {
            return { status: 404 };
        }

        if (existing.status !== 'Processing') {
            return { status: 409 };
        }

        const timeoutMs = PROCESSING_TIMEOUT_MINUTES * 60 * 1000;
        const startedAt = existing.processing_started_at
            ? new Date(existing.processing_started_at).getTime()
            : 0;

        if (startedAt && now.getTime() - startedAt < timeoutMs) {
            return { status: 409 };
        }

        const staleBefore = new Date(
            now.getTime() - timeoutMs
        ).toISOString();

        let recoveryQuery = supabase
            .from('deletion_requests')
            .update({
                processing_started_at: nowIso
            })
            .eq('id', id)
            .eq('status', 'Processing');

        if (existing.processing_started_at) {
            recoveryQuery = recoveryQuery.lt(
                'processing_started_at',
                staleBefore
            );
        } else {
            recoveryQuery = recoveryQuery.is(
                'processing_started_at',
                null
            );
        }

        const { data: recovered, error: recoveryError } =
            await recoveryQuery
                .select('*')
                .maybeSingle();

        if (recoveryError) {
            return { error: recoveryError };
        }

        if (!recovered) {
            return { status: 409 };
        }

        return { request: recovered };
    }

    async function resolveRequest(id, status, fields = {}) {
        return supabase
            .from('deletion_requests')
            .update({
                status,
                resolved_at: new Date().toISOString(),
                processing_started_at: null,
                ...fields
            })
            .eq('id', id)
            .eq('status', 'Pending')
            .select();
    }

    // Create an account deletion request.
    app.post('/api/deletion-requests', requireAuth, async (req, res) => {
        try {
            const body = req.body || {};
            const me = req.user;

            if (
                body.keep_data !== undefined &&
                typeof body.keep_data !== 'boolean'
            ) {
                return res.status(400).json({
                    error: 'keep_data must be true or false.'
                });
            }

            const email = normaliseEmail(me.email);

            if (!email || !me.id) {
                return res.status(400).json({
                    error: 'Your account information is incomplete.'
                });
            }

            let keepData;

            if (typeof body.keep_data === 'boolean') {
                keepData = body.keep_data;
            } else {
                const { data: profile, error: profileError } =
                    await supabase
                        .from('app_users')
                        .select('keep_data_on_delete')
                        .eq('id', me.id)
                        .maybeSingle();

                if (profileError) {
                    return dbError(
                        res,
                        profileError,
                        'Deletion preference lookup'
                    );
                }

                keepData = !!profile?.keep_data_on_delete;
            }

            const message = typeof body.message === 'string'
                ? body.message.trim().slice(0, 1000)
                : '';

            const { data, error } = await supabase
                .from('deletion_requests')
                .insert([{
                    user_id: me.id,
                    email,
                    full_name: me.full_name || '',
                    role: me.role || '',
                    keep_data: keepData,
                    message: message || null,
                    archive_saved: false
                }])
                .select();

            if (error) {
                if (error.code === '23505') {
                    return res.status(409).json({
                        error: 'A deletion request is already pending for this account.'
                    });
                }

                return dbError(
                    res,
                    error,
                    'Deletion request creation'
                );
            }

            audit('account_deletion_requested', req, {
                request_id: data?.[0]?.id,
                keep_data: keepData
            });

            return res.status(201).json(data);
        } catch (error) {
            console.error('Deletion request error:', error.message);

            return res.status(500).json({
                error: 'Unable to create the deletion request.'
            });
        }
    });

    // Admins see all requests. Other users see their own.
    app.get('/api/deletion-requests', requireAuth, async (req, res) => {
        let query = supabase
            .from('deletion_requests')
            .select('*')
            .order('created_at', { ascending: false });

        if (typeof req.query.status === 'string' && req.query.status.trim()) {
            const status = req.query.status.trim();

            if (!DELETION_STATUSES.includes(status)) {
                return res.status(400).json({
                    error: 'Invalid deletion request status.'
                });
            }

            query = query.eq('status', status);
        }

        if (isAdmin(req.user)) {
            if (
                typeof req.query.user_id === 'string' &&
                req.query.user_id.trim()
            ) {
                if (!validRequestId(req.query.user_id.trim())) {
                    return res.status(400).json({
                        error: 'Invalid user ID.'
                    });
                }

                query = query.eq('user_id', req.query.user_id.trim());
            }
        } else {
            query = query.eq('user_id', req.user.id);
        }

        const { data, error } = await query;

        if (error) {
            return dbError(res, error, 'Deletion request retrieval');
        }

        return res.json(data || []);
    });

    // Reject a deletion request.
    app.put(
        '/api/deletion-requests/:id/reject',
        requireRole('Admin'),
        async (req, res) => {
            if (!validRequestId(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid deletion request ID.'
                });
            }

            const note = typeof req.body?.admin_note === 'string'
                ? req.body.admin_note.trim().slice(0, 500)
                : '';

            const { data, error } = await resolveRequest(
                req.params.id,
                'Rejected',
                {
                    admin_note: note || null,
                    resolved_by: req.user.email
                }
            );

            if (error) {
                return dbError(res, error, 'Deletion request rejection');
            }

            if (!data || data.length === 0) {
                return res.status(404).json({
                    error: 'No pending request found.'
                });
            }

            audit('account_deletion_rejected', req, {
                request_id: req.params.id
            });

            return res.json(data);
        }
    );

    // The request owner or an Admin can cancel a pending request.
    app.put(
        '/api/deletion-requests/:id/cancel',
        requireAuth,
        async (req, res) => {
            if (!validRequestId(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid deletion request ID.'
                });
            }

            const { data: existing, error: findError } = await supabase
                .from('deletion_requests')
                .select('user_id, status')
                .eq('id', req.params.id)
                .maybeSingle();

            if (findError) {
                return dbError(
                    res,
                    findError,
                    'Deletion request lookup'
                );
            }

            if (!existing) {
                return res.status(404).json({
                    error: 'Deletion request not found.'
                });
            }

            if (
                !isAdmin(req.user) &&
                String(existing.user_id) !== String(req.user.id)
            ) {
                audit('forbidden', req, {
                    reason: 'cancel_other_users_request'
                });

                return res.status(403).json({
                    error: 'You can only cancel your own request.'
                });
            }

            if (existing.status !== 'Pending') {
                return res.status(409).json({
                    error: 'Only pending deletion requests can be cancelled.'
                });
            }

            const { data, error } = await resolveRequest(
                req.params.id,
                'Cancelled'
            );

            if (error) {
                return dbError(
                    res,
                    error,
                    'Deletion request cancellation'
                );
            }

            if (!data || data.length === 0) {
                return res.status(409).json({
                    error: 'The request could not be cancelled because its status changed.'
                });
            }

            audit('account_deletion_cancelled', req, {
                request_id: req.params.id
            });

            return res.json(data);
        }
    );

    // Admin approval: claim, archive, remove data, remove Auth user, finalise.
    app.post(
        '/api/deletion-requests/:id/approve',
        requireRole('Admin'),
        async (req, res) => {
            if (!validRequestId(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid deletion request ID.'
                });
            }

            const claim = await claimDeletionRequest(req.params.id);

            if (claim.error) {
                return dbError(
                    res,
                    claim.error,
                    'Deletion request claim'
                );
            }

            if (!claim.request) {
                if (claim.status === 404) {
                    return res.status(404).json({
                        error: 'Deletion request not found.'
                    });
                }

                return res.status(409).json({
                    error: 'This request is already being processed or is no longer pending.'
                });
            }

            const request = claim.request;

            try {
                const { data: user, error: userError } = await supabase
                    .from('app_users')
                    .select('*')
                    .eq('id', request.user_id)
                    .maybeSingle();

                if (userError) {
                    return failProcessing(
                        res,
                        request.id,
                        500,
                        null,
                        userError,
                        'Deletion profile lookup',
                        req
                    );
                }

                if (user?.role === 'Admin') {
                    return failProcessing(
                        res,
                        request.id,
                        400,
                        'Administrator accounts cannot be deleted.',
                        null,
                        'Administrator deletion blocked',
                        req
                    );
                }

                const email = normaliseEmail(request.email);

                if (!email) {
                    return failProcessing(
                        res,
                        request.id,
                        400,
                        'The deletion request does not contain a valid email address.',
                        null,
                        'Invalid deletion email',
                        req
                    );
                }

                // Archive the account before deleting its data.
                if (request.keep_data && request.archive_saved !== true) {
                    const { data: existingArchive, error: existingArchiveError } =
                        await supabase
                            .from('account_archive')
                            .select('data')
                            .eq('email', email)
                            .maybeSingle();

                    if (existingArchiveError) {
                        return failProcessing(
                            res,
                            request.id,
                            500,
                            null,
                            existingArchiveError,
                            'Existing archive lookup',
                            req
                        );
                    }

                    if (!user && existingArchive) {
                        // Preserve an archive from an interrupted earlier attempt.
                        const { error: savedFlagError } = await supabase
                            .from('deletion_requests')
                            .update({ archive_saved: true })
                            .eq('id', request.id)
                            .eq('status', 'Processing');

                        if (savedFlagError) {
                            return failProcessing(
                                res,
                                request.id,
                                500,
                                null,
                                savedFlagError,
                                'Archive recovery marker update',
                                req
                            );
                        }
                    } else if (!user && !existingArchive) {
                        return failProcessing(
                            res,
                            request.id,
                            409,
                            'The account no longer exists and no saved archive could be found. An administrator must review this request.',
                            null,
                            'Missing account archive during recovery',
                            req
                        );
                    } else {
                        const { data: quotes, error: quotesError } =
                            await supabase
                                .from('quote_requests')
                                .select('*')
                                .ilike('email', escapeLike(email));

                        if (quotesError) {
                            return failProcessing(
                                res,
                                request.id,
                                500,
                                null,
                                quotesError,
                                'Quote archive lookup',
                                req
                            );
                        }

                        const oldQuotes = Array.isArray(existingArchive?.data?.quotes)
                            ? existingArchive.data.quotes
                            : [];

                        const currentQuotes = Array.isArray(quotes)
                            ? quotes
                            : [];

                        const quoteMap = new Map();

                        for (const quote of oldQuotes) {
                            if (quote && typeof quote === 'object') {
                                const key = quote.id
                                    ? String(quote.id)
                                    : JSON.stringify(quote);

                                quoteMap.set(key, quote);
                            }
                        }

                        for (const quote of currentQuotes) {
                            if (quote && typeof quote === 'object') {
                                const key = quote.id
                                    ? String(quote.id)
                                    : JSON.stringify(quote);

                                quoteMap.set(key, quote);
                            }
                        }

                        const archivedAt = new Date().toISOString();

                        const profileSnapshot = user
                            ? {
                                full_name: user.full_name,
                                phone: user.phone,
                                role: user.role,
                                branch: user.branch,
                                created_at: user.created_at
                            }
                            : existingArchive?.data?.profile || null;

                        const archive = {
                            email,
                            data: {
                                archived_at: archivedAt,
                                profile: profileSnapshot,
                                quotes: Array.from(quoteMap.values())
                            },
                            archived_at: archivedAt,
                            code_hash: null,
                            code_expires_at: null,
                            code_sent_at: null,
                            code_attempts: 0
                        };

                        const { error: archiveError } = await supabase
                            .from('account_archive')
                            .upsert([archive], { onConflict: 'email' });

                        if (archiveError) {
                            return failProcessing(
                                res,
                                request.id,
                                500,
                                null,
                                archiveError,
                                'Account archive creation',
                                req
                            );
                        }

                        const { error: savedFlagError } = await supabase
                            .from('deletion_requests')
                            .update({ archive_saved: true })
                            .eq('id', request.id)
                            .eq('status', 'Processing');

                        if (savedFlagError) {
                            return failProcessing(
                                res,
                                request.id,
                                500,
                                null,
                                savedFlagError,
                                'Archive marker update',
                                req
                            );
                        }
                    }
                }

                // Remove quote records after archiving.
                const {
                    count: quotesRemoved,
                    error: quoteDeleteError
                } = await supabase
                    .from('quote_requests')
                    .delete({ count: 'exact' })
                    .ilike('email', escapeLike(email));

                if (quoteDeleteError) {
                    return failProcessing(
                        res,
                        request.id,
                        500,
                        null,
                        quoteDeleteError,
                        'Quote deletion',
                        req
                    );
                }

                // Remove email-change records initiated by this account.
                // requested_by has NO ACTION foreign keys.
                const emailChangeTables = [
                    'email_change_requests',
                    'pending_email_changes'
                ];

                for (const table of emailChangeTables) {
                    const { error: emailChangeDeleteError } = await supabase
                        .from(table)
                        .delete()
                        .eq('requested_by', request.user_id);

                    if (emailChangeDeleteError) {
                        return failProcessing(
                            res,
                            request.id,
                            500,
                            null,
                            emailChangeDeleteError,
                            `${table} cleanup`,
                            req
                        );
                    }
                }

                // Other dependent records use their database FK rules:
                // services/products: created_by becomes NULL
                // notifications: user's notifications cascade; actor_id becomes NULL
                // email-change records where user_id matches cascade
                // product deletion history: requester/resolver becomes NULL

                if (user) {
                    const { error: profileDeleteError } = await supabase
                        .from('app_users')
                        .delete()
                        .eq('id', request.user_id);

                    if (profileDeleteError) {
                        return failProcessing(
                            res,
                            request.id,
                            500,
                            null,
                            profileDeleteError,
                            'Account profile deletion',
                            req
                        );
                    }
                }

                // Delete the Auth account last.
                const { error: authError } =
                    await supabase.auth.admin.deleteUser(request.user_id);

                if (
                    authError &&
                    !/not found|does not exist/i.test(authError.message || '')
                ) {
                    console.error(
                        'Auth delete error:',
                        authError.message
                    );

                    return failProcessing(
                        res,
                        request.id,
                        500,
                        'The login could not be removed. Please try again.',
                        null,
                        'Auth account deletion',
                        req
                    );
                }

                // If the user did not want their data retained, remove any
                // older archive only after account deletion has succeeded.
                // If this fails, the request remains recoverable and can retry.
                if (request.keep_data !== true) {
                    const { error: archiveCleanupError } = await supabase
                        .from('account_archive')
                        .delete()
                        .eq('email', email);

                    if (archiveCleanupError) {
                        return failProcessing(
                            res,
                            request.id,
                            500,
                            null,
                            archiveCleanupError,
                            'Old account archive cleanup',
                            req
                        );
                    }
                }

                const { data: completed, error: resolveError } =
                    await supabase
                        .from('deletion_requests')
                        .update({
                            status: 'Approved',
                            resolved_by: req.user.email,
                            resolved_at: new Date().toISOString(),
                            processing_started_at: null
                        })
                        .eq('id', request.id)
                        .eq('status', 'Processing')
                        .select('id')
                        .maybeSingle();

                if (resolveError) {
                    return failProcessing(
                        res,
                        request.id,
                        500,
                        null,
                        resolveError,
                        'Deletion request finalisation',
                        req
                    );
                }

                if (!completed) {
                    audit('account_deletion_failed', req, {
                        request_id: request.id,
                        reason: 'Account deleted but request finalisation failed'
                    });

                    return res.status(409).json({
                        error: 'The account was deleted, but the request status could not be finalised. An administrator should review this request.'
                    });
                }

                audit('account_deletion_approved', req, {
                    request_id: request.id,
                    deleted_user_id: request.user_id,
                    archived: request.keep_data === true,
                    quotes_removed: quotesRemoved || 0
                });

                return res.json({
                    message: 'Account deleted.',
                    archived: request.keep_data === true,
                    quotes_removed: quotesRemoved || 0
                });
            } catch (error) {
                console.error(
                    'Approve deletion error:',
                    error.message
                );

                await releaseProcessing(request.id);

                audit('account_deletion_failed', req, {
                    request_id: request.id,
                    reason: error.message
                });

                return res.status(500).json({
                    error: 'Something went wrong. Please try again.'
                });
            }
        }
    );

    // Check whether the signed-in user's email has an available archive.
    app.get(
        '/api/account-archive/available',
        requireAuth,
        async (req, res) => {
            const email = normaliseEmail(req.user.email);

            if (!email) {
                return res.json({ available: false });
            }

            const { data, error } = await supabase
                .from('account_archive')
                .select('id')
                .eq('email', email)
                .maybeSingle();

            if (error) {
                return dbError(
                    res,
                    error,
                    'Account archive availability lookup'
                );
            }

            return res.json({ available: !!data });
        }
    );

    // Send a one-time recovery code.
    app.post(
        '/api/account-archive/request-code',
        requireAuth,
        async (req, res) => {
            try {
                const email = normaliseEmail(req.user.email);

                if (!email) {
                    return res.status(400).json({
                        error: 'Email is required.'
                    });
                }

                const { data: archive, error } = await supabase
                    .from('account_archive')
                    .select('id, code_sent_at')
                    .eq('email', email)
                    .maybeSingle();

                if (error) {
                    return dbError(
                        res,
                        error,
                        'Account archive lookup'
                    );
                }

                if (!archive) {
                    return res.status(404).json({
                        error: 'No saved data was found for this email.'
                    });
                }

                if (
                    archive.code_sent_at &&
                    Date.now() - new Date(archive.code_sent_at).getTime() <
                    RESEND_SECONDS * 1000
                ) {
                    return res.status(429).json({
                        error: 'Please wait a minute before requesting another code.'
                    });
                }

                const code = String(
                    crypto.randomInt(100000, 1000000)
                );

                const now = new Date();

                const { error: updateError } = await supabase
                    .from('account_archive')
                    .update({
                        code_hash: hashCode(code, email),
                        code_expires_at: new Date(
                            now.getTime() + CODE_MINUTES * 60 * 1000
                        ).toISOString(),
                        code_sent_at: now.toISOString(),
                        code_attempts: 0
                    })
                    .eq('id', archive.id);

                if (updateError) {
                    return dbError(
                        res,
                        updateError,
                        'Recovery code update'
                    );
                }

                const sent = await sendRestoreCodeEmail(
                    email,
                    code,
                    CODE_MINUTES
                );

                if (!sent || !sent.success) {
                    await supabase
                        .from('account_archive')
                        .update({
                            code_hash: null,
                            code_expires_at: null,
                            code_sent_at: null,
                            code_attempts: 0
                        })
                        .eq('id', archive.id)
                        .eq('code_hash', hashCode(code, email));

                    return res.status(502).json({
                        error: 'The email could not be sent. Please try again later.'
                    });
                }

                return res.json({ message: 'Code sent.' });
            } catch (error) {
                console.error(
                    'Request code error:',
                    error.message
                );

                return res.status(500).json({
                    error: 'Something went wrong. Please try again.'
                });
            }
        }
    );

    // Restore archived quote data and the saved phone number.
    app.post(
        '/api/account-archive/restore',
        requireAuth,
        async (req, res) => {
            try {
                const body = req.body || {};
                const code = typeof body.code === 'string'
                    ? body.code.trim()
                    : '';

                const userId = req.user.id;
                const email = normaliseEmail(req.user.email);

                if (!/^\d{6}$/.test(code)) {
                    return res.status(400).json({
                        error: 'Enter the six-digit recovery code.'
                    });
                }

                const { data: user, error: userError } = await supabase
                    .from('app_users')
                    .select('*')
                    .eq('id', userId)
                    .maybeSingle();

                if (userError) {
                    return dbError(
                        res,
                        userError,
                        'Restore profile lookup'
                    );
                }

                if (
                    !user ||
                    normaliseEmail(user.email) !== email
                ) {
                    return res.status(403).json({
                        error: 'This account does not match the saved data.'
                    });
                }

                const { data: archive, error: archiveError } =
                    await supabase
                        .from('account_archive')
                        .select('*')
                        .eq('email', email)
                        .maybeSingle();

                if (archiveError) {
                    return dbError(
                        res,
                        archiveError,
                        'Restore archive lookup'
                    );
                }

                if (!archive) {
                    return res.status(404).json({
                        error: 'No saved data was found for this email.'
                    });
                }

                const now = new Date();

                if (
                    !archive.code_hash ||
                    !archive.code_expires_at ||
                    new Date(archive.code_expires_at) <= now
                ) {
                    return res.status(400).json({
                        error: 'Your code has expired. Request a new one.'
                    });
                }

                if (archive.code_attempts >= MAX_ATTEMPTS) {
                    return res.status(429).json({
                        error: 'Too many attempts. Request a new code.'
                    });
                }

                const suppliedHash = hashCode(code, email);

                if (!sameHash(archive.code_hash, suppliedHash)) {
                    // Match both the current hash and attempt count so an old
                    // request cannot increment attempts for a newly issued code.
                    const { data: updatedAttempts, error: attemptError } =
                        await supabase
                            .from('account_archive')
                            .update({
                                code_attempts: archive.code_attempts + 1
                            })
                            .eq('id', archive.id)
                            .eq('code_hash', archive.code_hash)
                            .eq('code_attempts', archive.code_attempts)
                            .gt('code_expires_at', now.toISOString())
                            .select('code_attempts')
                            .maybeSingle();

                    if (attemptError) {
                        return dbError(
                            res,
                            attemptError,
                            'Recovery attempt update'
                        );
                    }

                    if (!updatedAttempts) {
                        return res.status(409).json({
                            error: 'The recovery code changed or was used in another attempt. Request a new code if necessary.'
                        });
                    }

                    return res.status(400).json({
                        error: 'That code is not correct.'
                    });
                }

                // Atomically consume the correct code before restoring anything.
                // Only one concurrent request can successfully claim this code.
                const { data: claimedCode, error: claimCodeError } =
                    await supabase
                        .from('account_archive')
                        .update({
                            code_hash: null,
                            code_expires_at: null,
                            code_sent_at: null,
                            code_attempts: 0
                        })
                        .eq('id', archive.id)
                        .eq('code_hash', archive.code_hash)
                        .eq('code_attempts', archive.code_attempts)
                        .gt('code_expires_at', now.toISOString())
                        .select('id')
                        .maybeSingle();

                if (claimCodeError) {
                    return dbError(
                        res,
                        claimCodeError,
                        'Recovery code claim'
                    );
                }

                if (!claimedCode) {
                    return res.status(409).json({
                        error: 'This recovery code has already been used or replaced. Request a new code.'
                    });
                }

                const saved = archive.data &&
                    typeof archive.data === 'object'
                    ? archive.data
                    : {};

                const savedQuotes = Array.isArray(saved.quotes)
                    ? saved.quotes
                    : [];

                // Associate restored quotes with the current account.
                const restoredQuotes = savedQuotes
                    .filter(quote =>
                        quote &&
                        typeof quote === 'object' &&
                        !Array.isArray(quote)
                    )
                    .map(quote => ({
                        ...quote,
                        created_by: userId
                    }));

                if (restoredQuotes.length > 0) {
                    const { error: quoteRestoreError } = await supabase
                        .from('quote_requests')
                        .upsert(restoredQuotes, {
                            onConflict: 'id',
                            ignoreDuplicates: true
                        });

                    if (quoteRestoreError) {
                        return dbError(
                            res,
                            quoteRestoreError,
                            'Quote restoration'
                        );
                    }
                }

                const savedPhone = saved.profile &&
                    typeof saved.profile.phone === 'string'
                    ? saved.profile.phone
                    : null;

                const restoredPhone = !!savedPhone && !user.phone;

                if (restoredPhone) {
                    const { error: phoneError } = await supabase
                        .from('app_users')
                        .update({ phone: savedPhone })
                        .eq('id', userId);

                    if (phoneError) {
                        return dbError(
                            res,
                            phoneError,
                            'Phone restoration'
                        );
                    }
                }

                const { error: removeError } = await supabase
                    .from('account_archive')
                    .delete()
                    .eq('id', archive.id);

                if (removeError) {
                    return dbError(
                        res,
                        removeError,
                        'Restored archive cleanup'
                    );
                }

                audit('account_archive_restored', req, {
                    restored_quotes: restoredQuotes.length,
                    restored_phone: restoredPhone
                });

                return res.json({
                    message: 'Data restored.',
                    restored: {
                        quotes: restoredQuotes.length,
                        phone: restoredPhone
                    }
                });
            } catch (error) {
                console.error(
                    'Restore error:',
                    error.message
                );

                return res.status(500).json({
                    error: 'Something went wrong. Please try again.'
                });
            }
        }
    );
};
