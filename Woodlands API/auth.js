// auth.js - JWT login tokens + role-based access control for the Woodlands API
const jwt = require('jsonwebtoken');

const SECRET = process.env.JWT_SECRET;
if (!SECRET || SECRET.length < 32) {
    console.error('FATAL: JWT_SECRET is missing or shorter than 32 characters. Add it to .env / Render environment variables.');
    process.exit(1);
}

const EXPIRES_IN = process.env.JWT_EXPIRES_IN || '8h';
const ISSUER = 'woodlands-api';
const AUDIENCE = 'woodlands-clients';

const isAdmin = (u) => !!u && u.role === 'Admin';
const isManager = (u) => !!u && String(u.role || '').startsWith('Manager');
const isStaff = (u) => isAdmin(u) || isManager(u);

// "Manager (Soweto)" -> "Soweto". Prefers the branch stored on the profile.
function branchOf(u) {
    if (!u) return null;
    if (u.branch && String(u.branch).trim()) return String(u.branch).trim();
    const m = /^Manager \((.+)\)$/.exec(String(u.role || ''));
    return m ? m[1] : null;
}

function signToken(profile) {
    return jwt.sign(
        { email: profile.email, role: profile.role, branch: profile.branch || null },
        SECRET,
        { subject: String(profile.id), expiresIn: EXPIRES_IN, issuer: ISSUER, audience: AUDIENCE, algorithm: 'HS256' }
    );
}

// Security event log (shows up in Render logs - useful evidence for the demo/report)
function audit(event, req, extra = {}) {
    console.warn(JSON.stringify({
        type: 'security',
        event,
        at: new Date().toISOString(),
        method: req.method,
        path: req.originalUrl.split('?')[0],
        ip: req.ip,
        user: req.user ? req.user.id : null,
        ...extra
    }));
}

module.exports = function createAuth(supabase) {

    // Reads the Bearer token and loads the CURRENT profile from the database, so a deactivated,
    // deleted or demoted account loses access immediately instead of when the token expires.
    async function resolveUser(req) {
        const header = req.headers.authorization || '';
        if (!header.startsWith('Bearer ')) return { status: 'none' };
        let payload;
        try {
            payload = jwt.verify(header.slice(7), SECRET, { algorithms: ['HS256'], issuer: ISSUER, audience: AUDIENCE });
        } catch (e) {
            return { status: 'invalid', reason: e.name };
        }
        const { data: profile, error } = await supabase
            .from('app_users')
            .select('id, email, full_name, role, branch, active')
            .eq('id', payload.sub)
            .maybeSingle();
        if (error) return { status: 'error' };
        if (!profile || profile.active === false) return { status: 'invalid', reason: 'AccountUnavailable' };
        return { status: 'ok', user: profile };
    }

    // Must be signed in
    async function requireAuth(req, res, next) {
        try {
            const r = await resolveUser(req);
            if (r.status === 'ok') { req.user = r.user; return next(); }
            if (r.status === 'error') return res.status(503).json({ error: 'Could not verify your session. Try again.' });
            audit('auth_rejected', req, { reason: r.reason || 'no_token' });
            return res.status(401).json({ error: 'Authentication required.' });
        } catch (e) {
            return res.status(500).json({ error: 'Authentication failed.' });
        }
    }

    // Signed in is optional (public routes that behave differently for staff). A bad token = anonymous.
    async function optionalAuth(req, res, next) {
        try {
            const r = await resolveUser(req);
            if (r.status === 'ok') req.user = r.user;
        } catch (e) { /* treat as anonymous */ }
        next();
    }

    // Must be signed in AND hold one of the roles. 'Manager' matches any "Manager (<branch>)".
    function requireRole(...roles) {
        return [requireAuth, (req, res, next) => {
            const ok = roles.some(r => r === 'Manager' ? isManager(req.user) : req.user.role === r);
            if (!ok) {
                audit('forbidden', req, { role: req.user.role, needed: roles.join(',') });
                return res.status(403).json({ error: 'You do not have permission to do that.' });
            }
            next();
        }];
    }

    return { signToken, requireAuth, optionalAuth, requireRole, isAdmin, isManager, isStaff, branchOf, audit };
};