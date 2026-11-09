const crypto = require('crypto');

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_TTL_MS = 60 * 1000;
const REQUEST_TTL_MS = 2 * 60 * 1000;
const MAX_REQUESTS_TOTAL = 1000;
const MAX_REQUESTS_PER_IP = 20;
const MAX_CODES_PER_USER = 5;

const INVALID_CODE = 'This code is invalid or has expired.';
const TOO_MANY = 'Too many attempts. Wait a minute and try again.';

module.exports = function registerHandoffRoutes(app, supabase, auth) {
    const codes = new Map();
    const requests = new Map();
    const requestsByUserCode = new Map();

    const sha = (value) => crypto.createHash('sha256').update(String(value)).digest('hex');

    function randomCode(length) {
        const bytes = crypto.randomBytes(length);
        let out = '';
        for (let i = 0; i < length; i++) out += ALPHABET[bytes[i] % ALPHABET.length];
        return out;
    }

    function normalise(value) {
        return String(value || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    }

    function makeLimiter(max, windowMs) {
        const hits = new Map();

        const allow = (key) => {
            const now = Date.now();
            const recent = (hits.get(key) || []).filter((t) => now - t < windowMs);
            if (recent.length >= max) {
                hits.set(key, recent);
                return false;
            }
            recent.push(now);
            hits.set(key, recent);
            return true;
        };

        allow.sweep = () => {
            const now = Date.now();
            for (const [key, list] of hits) {
                const recent = list.filter((t) => now - t < windowMs);
                if (recent.length === 0) hits.delete(key);
                else hits.set(key, recent);
            }
        };

        return allow;
    }

    const createLimit = makeLimiter(10, 60 * 1000);
    const redeemLimit = makeLimiter(10, 60 * 1000);
    const requestLimit = makeLimiter(30, 60 * 1000);
    const pollLimit = makeLimiter(90, 60 * 1000);
    const approveLimit = makeLimiter(10, 60 * 1000);

    function describeAgent(userAgent) {
        const ua = String(userAgent || '');
        const browser =
            /Edg\//.test(ua) ? 'Edge' :
            /OPR\//.test(ua) ? 'Opera' :
            /Chrome\//.test(ua) ? 'Chrome' :
            /Firefox\//.test(ua) ? 'Firefox' :
            /Safari\//.test(ua) ? 'Safari' : 'A web browser';
        const system =
            /Windows/.test(ua) ? 'Windows' :
            /Android/.test(ua) ? 'Android' :
            /iPhone|iPad/.test(ua) ? 'iOS' :
            /Mac OS X/.test(ua) ? 'macOS' :
            /Linux/.test(ua) ? 'Linux' : 'an unknown system';
        return `${browser} on ${system}`;
    }

    function maskIp(ip) {
        const value = String(ip || '');
        if (value.includes('.')) return value.split('.').slice(0, 2).join('.') + '.x.x';
        if (value.includes(':')) return value.split(':').slice(0, 2).join(':') + ':x';
        return 'unknown';
    }

    async function loadActiveProfile(userId) {
        const { data, error } = await supabase
            .from('app_users')
            .select('*')
            .eq('id', userId)
            .maybeSingle();

        if (error) return { error: true };
        if (!data || data.active === false) return { profile: null };
        return { profile: data };
    }

    function dropRequest(id) {
        const entry = requests.get(id);
        if (entry) requestsByUserCode.delete(entry.userCodeHash);
        requests.delete(id);
    }

    function sweep() {
        const now = Date.now();
        for (const [key, entry] of codes) if (entry.expiresAt <= now) codes.delete(key);
        for (const [id, entry] of requests) if (entry.expiresAt <= now) dropRequest(id);
        [createLimit, redeemLimit, requestLimit, pollLimit, approveLimit].forEach((l) => l.sweep());
    }

    setInterval(sweep, 30 * 1000).unref();

    // Web -> phone: signed-in user asks for a short-lived one-time code
    app.post('/api/auth/handoff', auth.requireAuth, (req, res) => {
        if (!createLimit(req.user.id)) {
            auth.audit('handoff_rate_limited', req, { action: 'create' });
            return res.status(429).json({ error: TOO_MANY });
        }

        sweep();

        let active = 0;
        for (const entry of codes.values()) if (entry.userId === req.user.id) active++;
        if (active >= MAX_CODES_PER_USER) {
            return res.status(429).json({ error: TOO_MANY });
        }

        const code = randomCode(10);
        codes.set(sha(code), { userId: req.user.id, expiresAt: Date.now() + CODE_TTL_MS });

        auth.audit('handoff_code_created', req);
        res.json({ code, expires_in: CODE_TTL_MS / 1000 });
    });

    // Web -> phone: the phone swaps the code for a normal login token
    app.post('/api/auth/handoff/redeem', async (req, res) => {
        if (!redeemLimit(req.ip)) {
            auth.audit('handoff_rate_limited', req, { action: 'redeem' });
            return res.status(429).json({ error: TOO_MANY });
        }

        try {
            const key = sha(normalise(req.body && req.body.code));
            const entry = codes.get(key);
            codes.delete(key);

            if (!entry || entry.expiresAt <= Date.now()) {
                auth.audit('handoff_redeem_failed', req);
                return res.status(400).json({ error: INVALID_CODE });
            }

            const result = await loadActiveProfile(entry.userId);
            if (result.error) return res.status(503).json({ error: 'Could not verify your account. Try again.' });
            if (!result.profile) {
                auth.audit('handoff_redeem_failed', req, { reason: 'account_unavailable' });
                return res.status(400).json({ error: INVALID_CODE });
            }

            auth.audit('handoff_redeemed', req, { target: result.profile.id });
            res.json({ user: result.profile, token: auth.signToken(result.profile) });
        } catch (e) {
            res.status(500).json({ error: 'Sign-in failed.' });
        }
    });

    // Phone -> web: the web page starts a request and shows the short code
    app.post('/api/auth/handoff/request', (req, res) => {
        if (!requestLimit(req.ip)) {
            auth.audit('handoff_rate_limited', req, { action: 'request' });
            return res.status(429).json({ error: TOO_MANY });
        }

        sweep();

        let fromIp = 0;
        for (const entry of requests.values()) if (entry.ip === req.ip) fromIp++;
        if (requests.size >= MAX_REQUESTS_TOTAL || fromIp >= MAX_REQUESTS_PER_IP) {
            return res.status(429).json({ error: TOO_MANY });
        }

        const body = req.body || {};
        const reportedAgent = typeof body.agent === 'string' && body.agent ? body.agent.slice(0, 300) : req.headers['user-agent'];
        const reportedIp = typeof body.ip === 'string' && body.ip ? body.ip.slice(0, 64) : req.ip;

        const requestId = crypto.randomBytes(24).toString('base64url');
        const userCode = randomCode(8);
        const userCodeHash = sha(userCode);

        requests.set(requestId, {
            userCodeHash,
            status: 'pending',
            userId: null,
            ip: req.ip,
            displayIp: reportedIp,
            agent: describeAgent(reportedAgent),
            createdAt: Date.now(),
            expiresAt: Date.now() + REQUEST_TTL_MS
        });
        requestsByUserCode.set(userCodeHash, requestId);

        res.json({ request_id: requestId, user_code: userCode, expires_in: REQUEST_TTL_MS / 1000 });
    });

    // Phone -> web: the web page checks whether the phone has approved
    app.get('/api/auth/handoff/request/:id', async (req, res) => {
        try {
            const id = String(req.params.id || '');
            const entry = requests.get(id);

            if (!entry || entry.expiresAt <= Date.now()) {
                if (entry) dropRequest(id);
                return res.status(410).json({ status: 'expired' });
            }

            if (!pollLimit(id)) return res.status(429).json({ error: TOO_MANY });

            if (entry.status === 'pending') return res.json({ status: 'pending' });

            if (entry.status === 'denied') {
                dropRequest(id);
                return res.json({ status: 'denied' });
            }

            dropRequest(id);

            const result = await loadActiveProfile(entry.userId);
            if (result.error) return res.status(503).json({ error: 'Could not verify the account. Try again.' });
            if (!result.profile) return res.status(410).json({ status: 'expired' });

            auth.audit('handoff_web_signin', req, { target: result.profile.id });
            res.json({ status: 'approved', user: result.profile, token: auth.signToken(result.profile) });
        } catch (e) {
            res.status(500).json({ error: 'Sign-in failed.' });
        }
    });

    // Phone -> web: the signed-in phone looks up who is asking before approving
    app.post('/api/auth/handoff/inspect', auth.requireAuth, (req, res) => {
        if (!approveLimit(req.user.id)) {
            auth.audit('handoff_rate_limited', req, { action: 'inspect' });
            return res.status(429).json({ error: TOO_MANY });
        }

        const id = requestsByUserCode.get(sha(normalise(req.body && req.body.user_code)));
        const entry = id ? requests.get(id) : null;

        if (!entry || entry.status !== 'pending' || entry.expiresAt <= Date.now()) {
            return res.status(400).json({ error: INVALID_CODE });
        }

        res.json({
            device: entry.agent,
            ip: maskIp(entry.displayIp),
            requested_seconds_ago: Math.round((Date.now() - entry.createdAt) / 1000)
        });
    });

    // Phone -> web: the signed-in phone approves or denies the request
    app.post('/api/auth/handoff/approve', auth.requireAuth, (req, res) => {
        if (!approveLimit(req.user.id)) {
            auth.audit('handoff_rate_limited', req, { action: 'approve' });
            return res.status(429).json({ error: TOO_MANY });
        }

        const id = requestsByUserCode.get(sha(normalise(req.body && req.body.user_code)));
        const entry = id ? requests.get(id) : null;

        if (!entry || entry.status !== 'pending' || entry.expiresAt <= Date.now()) {
            auth.audit('handoff_approve_failed', req);
            return res.status(400).json({ error: INVALID_CODE });
        }

        const approve = !(req.body && req.body.approve === false);

        entry.status = approve ? 'approved' : 'denied';
        entry.userId = req.user.id;
        requestsByUserCode.delete(entry.userCodeHash);

        auth.audit(approve ? 'handoff_approved' : 'handoff_denied', req, { device: entry.agent });
        res.json({ status: entry.status });
    });
};
