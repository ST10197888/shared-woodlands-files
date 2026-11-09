
const { randomUUID } = require('crypto');

const UUID_RE =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const ACTION_STATUSES = [
    'Pending',
    'Accepted',
    'Declined',
    'Completed',
    'Expired'
];

const MAX_NOTIFICATIONS = 50;

function validUuid(value) {
    return typeof value === 'string' && UUID_RE.test(value);
}

function databaseError(res, error) {
    console.error('Notification database error:', error?.message || error);

    return res.status(500).json({
        error: 'Could not process your notification request.'
    });
}

module.exports = function registerNotificationRoutes(app, supabase, auth) {
    const { requireAuth } = auth;

    // GET: Notifications belonging only to the signed-in user.
    app.get('/api/notifications', requireAuth, async (req, res) => {
        try {
            let query = supabase
                .from('notifications')
                .select('*')
                .eq('user_id', req.user.id)
                .order('created_at', { ascending: false })
                .limit(MAX_NOTIFICATIONS);

            if (req.query.unread_only === 'true') {
                query = query.eq('is_read', false);
            } else if (
                req.query.unread_only !== undefined &&
                req.query.unread_only !== 'false'
            ) {
                return res.status(400).json({
                    error: 'Invalid unread_only value.'
                });
            }

            const { data, error } = await query;

            if (error) return databaseError(res, error);

            return res.json(data || []);
        } catch (error) {
            return databaseError(res, error);
        }
    });

    // GET: Unread notification count for the signed-in user.
    app.get(
        '/api/notifications/unread-count',
        requireAuth,
        async (req, res) => {
            try {
                const { count, error } = await supabase
                    .from('notifications')
                    .select('id', { count: 'exact', head: true })
                    .eq('user_id', req.user.id)
                    .eq('is_read', false);

                if (error) return databaseError(res, error);

                return res.json({ unread_count: count || 0 });
            } catch (error) {
                return databaseError(res, error);
            }
        }
    );

    // PATCH: Mark one of the signed-in user's notifications as read.
    app.patch(
        '/api/notifications/:id/read',
        requireAuth,
        async (req, res) => {
            if (!validUuid(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid notification ID.'
                });
            }

            try {
                const { data, error } = await supabase
                    .from('notifications')
                    .update({
                        is_read: true,
                        read_at: new Date().toISOString()
                    })
                    .eq('id', req.params.id)
                    .eq('user_id', req.user.id)
                    .select('id, is_read, read_at')
                    .maybeSingle();

                if (error) return databaseError(res, error);

                if (!data) {
                    return res.status(404).json({
                        error: 'Notification not found.'
                    });
                }

                return res.json(data);
            } catch (error) {
                return databaseError(res, error);
            }
        }
    );

    // PATCH: Mark all unread notifications belonging to the user as read.
    app.patch(
        '/api/notifications/read-all',
        requireAuth,
        async (req, res) => {
            try {
                const { data, error } = await supabase
                    .from('notifications')
                    .update({
                        is_read: true,
                        read_at: new Date().toISOString()
                    })
                    .eq('user_id', req.user.id)
                    .eq('is_read', false)
                    .select('id');

                if (error) return databaseError(res, error);

                return res.json({
                    message: 'Notifications marked as read.',
                    updated_count: data?.length || 0
                });
            } catch (error) {
                return databaseError(res, error);
            }
        }
    );

    // DELETE: Dismiss one of the signed-in user's notifications.
    app.delete(
        '/api/notifications/:id',
        requireAuth,
        async (req, res) => {
            if (!validUuid(req.params.id)) {
                return res.status(400).json({
                    error: 'Invalid notification ID.'
                });
            }

            try {
                const { data, error } = await supabase
                    .from('notifications')
                    .delete()
                    .eq('id', req.params.id)
                    .eq('user_id', req.user.id)
                    .select('id')
                    .maybeSingle();

                if (error) return databaseError(res, error);

                if (!data) {
                    return res.status(404).json({
                        error: 'Notification not found.'
                    });
                }

                return res.json({
                    message: 'Notification dismissed.'
                });
            } catch (error) {
                return databaseError(res, error);
            }
        }
    );

    // Internal server-side helper.
    // This is not exposed as a public API endpoint.
    async function createNotification({
        user_id,
        actor_id = null,
        type,
        title,
        message,
        data = {},
        action_required = false,
        action_status = 'None',
        expires_at = null
    }) {
        if (!validUuid(user_id)) {
            throw new Error('A valid notification recipient is required.');
        }

        if (actor_id !== null && !validUuid(actor_id)) {
            throw new Error('Invalid notification actor.');
        }

        if (
            typeof type !== 'string' ||
            !type.trim() ||
            type.length > 60
        ) {
            throw new Error('Invalid notification type.');
        }

        if (
            typeof title !== 'string' ||
            !title.trim() ||
            title.length > 120
        ) {
            throw new Error('Invalid notification title.');
        }

        if (
            typeof message !== 'string' ||
            !message.trim() ||
            message.length > 1000
        ) {
            throw new Error('Invalid notification message.');
        }

        if (
            !data ||
            typeof data !== 'object' ||
            Array.isArray(data)
        ) {
            throw new Error('Notification data must be an object.');
        }

        if (typeof action_required !== 'boolean') {
            throw new Error('Invalid action_required value.');
        }

        if (action_required) {
            if (!ACTION_STATUSES.includes(action_status)) {
                throw new Error('Invalid notification action status.');
            }
        } else {
            action_status = 'None';
        }

        if (
            expires_at !== null &&
            (
                typeof expires_at !== 'string' ||
                Number.isNaN(Date.parse(expires_at))
            )
        ) {
            throw new Error('Invalid notification expiry date.');
        }

        const notification = {
            id: randomUUID(),
            user_id,
            actor_id,
            type: type.trim(),
            title: title.trim(),
            message: message.trim(),
            data,
            action_required,
            action_status,
            is_read: false,
            read_at: null,
            expires_at
        };

        const { data: saved, error } = await supabase
            .from('notifications')
            .insert([notification])
            .select()
            .single();

        if (error) {
            console.error('Notification creation failed:', error.message);
            throw new Error('Could not create notification.');
        }

        return saved;
    }

    return { createNotification };
};
