require('dotenv').config();

const express = require('express');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');
const rateLimit = require('express-rate-limit');                            // PATCH 1

const {
    sendCustomerQuoteReceivedEmail,
    sendAdminNewQuoteEmail,
    sendBranchManagerNewQuoteEmail,
    sendCustomerStatusEmail,
    sendWelcomeEmail,
    sendAdminNewRegistrationEmail
} = require('./emailService');

const app = express();
app.set('trust proxy', 1);

// MIDDLEWARE


app.use(cors());
app.use(express.json({ limit: '15mb' }));


// RATE LIMITING
// Applied to the anonymous, email-sending endpoints. Authenticated routes
// rely on JWT verification + role checks for abuse control.

const publicLimiter = rateLimit({                                           // PATCH 2
    windowMs: 60 * 1000,
    max: 5,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many requests. Please wait a minute and try again.' }
});

const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 20,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many attempts. Please wait and try again.' }
});

// HEALTH CHECK
// Registered before any auth middleware so Render/monitors can hit it freely.

app.get('/api/health', (req, res) => {                                      // PATCH 3
    res.json({
        ok: true,
        uptime: Math.round(process.uptime()),
        time: new Date().toISOString()
    });
});


// SUPABASE INITIALIZATION


const clientOptions = {
    auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false
    }
};

const supabase = createClient(
    process.env.SUPABASE_URL,
    process.env.SUPABASE_KEY,
    clientOptions
);

function createAuthClient() {
    return createClient(
        process.env.SUPABASE_URL,
        process.env.SUPABASE_KEY,
        clientOptions
    );
}

// JWT helper (needs JWT_SECRET, 32+ chars, in the environment)
const auth = require('./auth')(supabase);
const { signToken } = auth;

// Role guards reused throughout this file.
const { requireAuth, optionalAuth, requireRole, isAdmin, isManager, isStaff, branchOf, audit } = auth;
const adminOnly = requireRole('Admin');
const staffOnly = requireRole('Admin', 'Manager');   // Admin or any "Manager (...)"

// In-app notifications (registers /api/notifications routes)
const notificationService = require('./notificationRoutes')(app, supabase, auth);
const quoteNotifications = require('./quoteNotifications')(
    supabase,
    notificationService.createNotification
);

// Secured routes (admin/manager checks, audit logging)
require('./deletionRoutes')(app, supabase, auth);
require('./productDeletionRoutes')(app, supabase, auth, notificationService);
require('./handoffRoutes')(app, supabase, auth);
// emailChangeRoutes.js is intentionally not registered here yet.
// The file is complete and uses the same middleware, but the routes are
// not exposed until a later update.


// HELPER FUNCTIONS
// (Defined before the routes that reference them.)


// Parse JSON strings into arrays.

function safeParse(value) {
    if (Array.isArray(value)) {
        return value;
    }

    if (typeof value !== 'string' || !value.trim()) {
        return [];
    }

    try {
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

// Validate request bodies.

function validateRequestBody(req, res) {
    if (
        !req.body ||
        typeof req.body !== 'object' ||
        Array.isArray(req.body)
    ) {
        res.status(400).json({
            error: 'Request body must be a valid JSON object'
        });

        return false;
    }

    if (Object.keys(req.body).length === 0) {
        res.status(400).json({
            error: 'Request body cannot be empty'
        });

        return false;
    }

    return true;
}

// Middleware for request body validation.

function requireValidRequestBody(req, res, next) {
    if (!validateRequestBody(req, res)) {
        return;
    }

    next();
}

// Validate numeric database IDs.

function isValidNumericId(id) {
    return /^\d+$/.test(String(id));
}

// Validate UUID database IDs.

function isValidUuid(id) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        String(id)
    );
}


// AUTHENTICATION ROUTES


// LOGIN

app.post('/api/auth/login', authLimiter, async (req, res) => {              // PATCH 4
    const { email, password } = req.body || {};

    try {
        const { data: authData, error: authError } =
            await createAuthClient().auth.signInWithPassword({
                email,
                password
            });

        if (authError || !authData.user) {
            return res.status(401).json({
                error: 'Invalid email or password.'
            });
        }

        const { data: profile, error: profileError } = await supabase
            .from('app_users')
            .select('*')
            .eq('id', authData.user.id)
            .single();

        if (profileError || !profile) {
            return res.status(404).json({
                error: 'User profile not found.'
            });
        }

        if (!profile.active) {
            return res.status(403).json({
                error: 'This account is currently inactive.'
            });
        }

        res.json({ user: profile, token: signToken(profile) });

    } catch (ex) {
        res.status(500).json({ error: ex.message });
    }
});

// REGISTER

app.post('/api/auth/register', authLimiter, async (req, res) => {           // PATCH 4
    const { fullName, email, password, phone } = req.body || {};

    try {
        const { data: authData, error: authError } =
            await supabase.auth.admin.createUser({
                email,
                password,
                email_confirm: true
            });

        if (authError || !authData.user) {
            return res.status(400).json({
                error: authError?.message || 'Registration failed.'
            });
        }

        const newUser = {
            id: authData.user.id,
            full_name: fullName,
            email,
            phone: phone || '',
            role: 'Customer',
            branch: null,
            active: true
        };

        const { error: profileError } = await supabase
            .from('app_users')
            .insert([newUser]);

        if (profileError) {
            // PATCH 5 — roll back the Auth user so the email is not
            // permanently stuck in Supabase Auth without a profile.
            const { error: rollbackError } =
                await supabase.auth.admin.deleteUser(authData.user.id);

            if (rollbackError) {
                console.error(
                    'CRITICAL: Could not roll back Auth user after profile insert failure:',
                    rollbackError.message,
                    'auth_user_id:', authData.user.id
                );
            }

            return res.status(500).json({
                error: 'Registration could not be completed. Please try again.'
            });
        }

        res.status(201).json({
            message: 'Registration successful.'
        });

        sendWelcomeEmail(newUser)
            .catch(err => console.error('Welcome email error:', err));

        sendAdminNewRegistrationEmail(supabase, newUser)
            .catch(err => console.error('Admin registration email error:', err));

    } catch (ex) {
        res.status(500).json({ error: ex.message });
    }
});


// APP USERS


// Admins see everyone; everyone else sees only their own record.
app.get('/api/app-users', requireAuth, async (req, res) => {
    let query = supabase.from('app_users').select('*').order('full_name');

    if (!isAdmin(req.user)) {
        query = query.eq('id', req.user.id);
    } else if (typeof req.query.email === 'string' && req.query.email.trim()) {
        const escaped = req.query.email.trim().replace(/[\\%_]/g, '\\$&');
        query = query.ilike('email', escaped);
    }

    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

// Users may edit their own name/phone. Only Admins may touch role/branch/active.
app.put('/api/app-users/:id', requireAuth, requireValidRequestBody, async (req, res) => {
    const admin = isAdmin(req.user);

    if (!admin && req.params.id !== req.user.id) {
        audit('forbidden', req, { needed: 'self_or_admin' });
        return res.status(403).json({ error: 'You do not have permission to do that.' });
    }

    const ALLOWED = admin
        ? ['full_name', 'phone', 'role', 'branch', 'active']
        : ['full_name', 'phone'];

    const update = {};
    for (const k of ALLOWED) if (k in req.body) update[k] = req.body[k];

    if (Object.keys(update).length === 0) {
        return res.status(400).json({ error: 'No updatable fields supplied.' });
    }

    if (admin && req.params.id === req.user.id && ('role' in update || 'active' in update)) {
        return res.status(400).json({ error: 'You cannot change your own role or active status.' });
    }

    const { data, error } = await supabase
        .from('app_users').update(update).eq('id', req.params.id).select();

    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.delete('/api/app-users/:id', adminOnly, async (req, res) => {
    if (req.params.id === req.user.id) {
        return res.status(400).json({ error: 'You cannot delete your own account.' });
    }

    const { data, error } = await supabase
        .from('app_users').delete().eq('id', req.params.id).select();

    if (error) return res.status(500).json({ error: error.message });
    res.json({ message: 'User deleted', data });
});


// CONTACTS


app.get('/api/contacts', optionalAuth, async (req, res) => {
    const { data, error } = await supabase
        .from('app_users')
        .select('id, full_name, email, phone, role, branch, active')
        .eq('active', true)
        .order('full_name');

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    const viewerIsStaff = isStaff(req.user);   // from the token, not the query string

    const contacts = data.filter(u =>
        String(u.role).startsWith('Manager') ||
        (viewerIsStaff && u.role === 'Admin')
    );

    res.json(contacts);
});


// PRODUCTS


app.get('/api/products', async (req, res) => {
    const { data, error } = await supabase
        .from('products')
        .select('*')
        .order('created_at', { ascending: false });

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    const cleaned = (data || []).map(p => ({
        ...p,
        gallery: safeParse(p.gallery),
        features: safeParse(p.features),
        finishes: safeParse(p.finishes)
    }));

    res.json(cleaned);
});

app.get('/api/products/:id', async (req, res) => {
    // Product IDs are strings (e.g. "modern-kitchen-suite"). only reject if the ID is empty.
    if (!req.params.id || String(req.params.id).trim() === '') {
        return res.status(404).json({
            error: 'Product not found'
        });
    }

    // PATCH 6 — explicit column list; do not leak internal audit columns.
    const { data, error } = await supabase
        .from('products')
        .select('id, category, title, tagline, description, image, gallery, features, finishes, lead_time, tag, price, is_from_price')
        .eq('id', req.params.id)
        .single();

    if (error || !data) {
        return res.status(404).json({
            error: 'Product not found'
        });
    }

    const cleaned = {
        ...data,
        gallery: safeParse(data.gallery),
        features: safeParse(data.features),
        finishes: safeParse(data.finishes)
    };

    res.json(cleaned);
});

app.post('/api/products', staffOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('products')
        .insert([req.body])
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.status(201).json(data);
});

app.put('/api/products/:id', staffOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('products')
        .update(req.body)
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

// DELETE /api/products/:id is served by productDeletionRoutes.js (admin only; managers file a request)


// BRANCHES


app.get('/api/branches', async (req, res) => {
    const { data, error } = await supabase
        .from('branches')
        .select('*')
        .order('name');

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.get('/api/branches/:id', async (req, res) => {
    if (!isValidNumericId(req.params.id)) {
        return res.status(400).json({
            error: 'Invalid branch ID'
        });
    }

    const { data, error } = await supabase
        .from('branches')
        .select('*')
        .eq('id', req.params.id)
        .single();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.post('/api/branches', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('branches')
        .insert([req.body])
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.status(201).json(data);
});

app.put('/api/branches/:id', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('branches')
        .update(req.body)
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.delete('/api/branches/:id', adminOnly, async (req, res) => {
    if (!isValidNumericId(req.params.id)) {
        return res.status(400).json({
            error: 'Invalid branch ID'
        });
    }

    const { data, error } = await supabase
        .from('branches')
        .delete()
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json({ message: 'Branch deleted', data });
});

// BRANCH IMAGE UPLOAD

app.put('/api/branches/:id/image', adminOnly, async (req, res) => {
    if (!isValidNumericId(req.params.id)) {
        return res.status(400).json({
            error: 'Invalid branch ID'
        });
    }

    try {
        const { data, contentType } = req.body || {};

        // PATCH 7 — pre-decode size check.
        if (typeof data !== 'string' || data.length === 0) {
            return res.status(400).json({
                error: 'Image data is required'
            });
        }

        // 8 MB decoded ≈ 10.7 MB base64; allow a small margin.
        // Rejecting here avoids allocating tens of MB per request.
        if (data.length > 11 * 1024 * 1024) {
            return res.status(400).json({
                error: 'Image must be under 8 MB'
            });
        }

        const buffer = Buffer.from(data, 'base64');

        if (buffer.length === 0 || buffer.length > 8 * 1024 * 1024) {
            return res.status(400).json({
                error: 'Image must be under 8 MB'
            });
        }

        const type = contentType === 'image/png'
            ? 'image/png'
            : 'image/jpeg';

        const ext = type === 'image/png' ? 'png' : 'jpg';

        const path = `branches/${req.params.id}-${Date.now()}.${ext}`;

        const up = await supabase.storage
            .from('woodlands-assets')
            .upload(path, buffer, {
                contentType: type,
                upsert: true
            });

        if (up.error) {
            return res.status(500).json({
                error: up.error.message
            });
        }

        const { data: pub } = supabase.storage
            .from('woodlands-assets')
            .getPublicUrl(path);

        const { error } = await supabase
            .from('branches')
            .update({ image: pub.publicUrl })
            .eq('id', req.params.id);

        if (error) {
            return res.status(500).json({ error: error.message });
        }

        res.json({ image: pub.publicUrl });

    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});


// SERVICES


app.get('/api/services', async (req, res) => {
    const { data, error } = await supabase
        .from('services')
        .select('*')
        .order('name');

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.post('/api/services', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('services')
        .insert([req.body])
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.status(201).json(data);
});

app.put('/api/services/:id', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('services')
        .update(req.body)
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.delete('/api/services/:id', adminOnly, async (req, res) => {
    if (!isValidNumericId(req.params.id)) {
        return res.status(400).json({
            error: 'Invalid service ID'
        });
    }

    const { data, error } = await supabase
        .from('services')
        .delete()
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json({ message: 'Service deleted', data });
});


// TESTIMONIALS


app.get('/api/testimonials', async (req, res) => {
    const { data, error } = await supabase
        .from('testimonials')
        .select('*')
        .order('created_at', { ascending: false });

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.post('/api/testimonials', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('testimonials')
        .insert([req.body])
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.status(201).json(data);
});

app.put('/api/testimonials/:id', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('testimonials')
        .update(req.body)
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.delete('/api/testimonials/:id', adminOnly, async (req, res) => {
    if (!isValidNumericId(req.params.id)) {
        return res.status(400).json({
            error: 'Invalid testimonial ID'
        });
    }

    const { data, error } = await supabase
        .from('testimonials')
        .delete()
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json({ message: 'Testimonial deleted', data });
});


// FAQS


app.get('/api/faqs', async (req, res) => {
    const { data, error } = await supabase
        .from('faqs')
        .select('*')
        .order('category');

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.post('/api/faqs', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('faqs')
        .insert([req.body])
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.status(201).json(data);
});

app.put('/api/faqs/:id', adminOnly, requireValidRequestBody, async (req, res) => {
    const { data, error } = await supabase
        .from('faqs')
        .update(req.body)
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});

app.delete('/api/faqs/:id', adminOnly, async (req, res) => {
    if (!isValidNumericId(req.params.id)) {
        return res.status(400).json({
            error: 'Invalid FAQ ID'
        });
    }

    const { data, error } = await supabase
        .from('faqs')
        .delete()
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json({ message: 'FAQ deleted', data });
});


// HOMEPAGE ASSETS


app.get('/api/homepage-assets', async (req, res) => {
    const { data, error } = await supabase
        .from('homepage_assets')
        .select('*')
        .order('display_order');

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json(data);
});


// QUOTE REQUESTS


// GET: admins see all, managers see their branch, customers see only their own.
app.get('/api/quote-requests', requireAuth, async (req, res) => {
    let query = supabase
        .from('quote_requests').select('*').order('created_at', { ascending: false });

    if (isAdmin(req.user)) {
        if (typeof req.query.branch === 'string' && req.query.branch.trim()) {
            query = query.eq('branch', req.query.branch.trim());
        }
    } else if (isManager(req.user)) {
        query = query.eq('branch', branchOf(req.user));
    } else {
        query = query.ilike('email', String(req.user.email).replace(/[\\%_]/g, '\\$&'));
    }

    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

// POST stays public — customers submit quotes as guests.
app.post('/api/quote-requests', publicLimiter, requireValidRequestBody, async (req, res) => {  // PATCH 8
    const { data, error } = await supabase
        .from('quote_requests')
        .insert([req.body])
        .select();

    if (error) {
        // Preserve duplicate quote protection.
        if (error.code === '23505') {
            const { data: existing } = await supabase
                .from('quote_requests')
                .select('*')
                .eq('quote_code', req.body.quote_code)
                .eq('email', req.body.email);

            return res.status(200).json(existing || []);
        }

        return res.status(500).json({ error: error.message });
    }

    const quote = data && data[0];

    if (quote) {
        sendCustomerQuoteReceivedEmail(quote)
            .catch(err => console.error('Customer quote email error:', err));

        sendAdminNewQuoteEmail(supabase, quote)
            .catch(err => console.error('Admin quote email error:', err));

        sendBranchManagerNewQuoteEmail(supabase, quote)
            .catch(err => console.error('Manager quote email error:', err));

        quoteNotifications.notifyNewQuote(quote);
    }

    res.status(201).json(data);
});

// PUT: staff only. Managers may only modify quotes in their own branch.
app.put('/api/quote-requests/:id', staffOnly, requireValidRequestBody, async (req, res) => {
    if (!isValidUuid(req.params.id)) {
        return res.status(400).json({ error: 'Invalid quote request ID' });
    }

    const { data: before } = await supabase
        .from('quote_requests')
        .select('status, branch')
        .eq('id', req.params.id)
        .single();

    if (!before) return res.status(404).json({ error: 'Quote request not found' });

    if (!isAdmin(req.user) && before.branch !== branchOf(req.user)) {
        audit('forbidden', req, { needed: 'own_branch' });
        return res.status(403).json({ error: 'You do not have permission to do that.' });
    }

    const { data, error } = await supabase
        .from('quote_requests')
        .update(req.body)
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    const quote = data && data[0];

    if (
        quote &&
        req.body.status &&
        req.body.status !== before.status
    ) {
        sendCustomerStatusEmail(quote, before.status, quote.status)
            .catch(err => console.error('Status email error:', err));

        quoteNotifications.notifyQuoteStatus(quote, before.status, quote.status);
    }

    res.json(data);
});

// DELETE: admin only.
app.delete('/api/quote-requests/:id', adminOnly, async (req, res) => {
    if (!isValidUuid(req.params.id)) {
        return res.status(400).json({ error: 'Invalid quote request ID' });
    }

    const { data, error } = await supabase
        .from('quote_requests')
        .delete()
        .eq('id', req.params.id)
        .select();

    if (error) {
        return res.status(500).json({ error: error.message });
    }

    res.json({ message: 'Quote request deleted', data });
});


// EXPORT EXPRESS APP FOR TESTING


module.exports = app;

app.safeParse = safeParse;
app.validateRequestBody = validateRequestBody;


// START SERVER


const PORT = process.env.PORT || 5000;

// Only start listening when this file is run directly.
// Jest/Supertest can import the app without starting a server.

// BACKUP SERVICE - Wired ONLY when the file is run directly (not during Jest).

if (require.main === module) {
    let backupFailed = false;                                               // PATCH 9

    if (process.env.BACKUP_ENABLED !== 'false') {
        try {
            const createBackupService = require('./backupService');
            const backupService = createBackupService(app, supabase, auth);

            backupService.start()
                .catch(err => console.error('[backup] failed to start:', err.message));
        } catch (err) {
            console.error('[backup] could not load backup service:', err.message);
            backupFailed = true;
        }
    } else {
        console.log('[backup] disabled via BACKUP_ENABLED=false');
    }

    app.listen(PORT, () => {
        console.log(`Node API running on http://localhost:${PORT}`);

        if (backupFailed) {
            console.error('[backup] exiting because the backup service could not load.');
            // Give the listen callback a tick to flush logs before exit.
            setTimeout(() => process.exit(1), 100);
        }
    });
}