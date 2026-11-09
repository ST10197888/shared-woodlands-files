process.env.JWT_SECRET = 'test-secret-test-secret-test-secret-123456';

jest.mock('dotenv', () => ({
    config: jest.fn()
}));

jest.mock('@supabase/supabase-js', () => ({
    createClient: jest.fn()
}));

const mockAdmin = { id: 'admin-1', email: 'admin@test.com', role: 'Admin', branch: null, active: true };

jest.mock('../auth', () => () => {
    const isAdmin = (u) => !!u && u.role === 'Admin';
    const isManager = (u) => !!u && String(u.role || '').startsWith('Manager');
    const requireAuth = (req, res, next) => {
        if (!req.headers.authorization) return res.status(401).json({ error: 'Authentication required.' });
        req.user = mockAdmin;
        next();
    };
    return {
        signToken: () => 'test-token',
        requireAuth,
        optionalAuth: (req, res, next) => { if (req.headers.authorization) req.user = mockAdmin; next(); },
        requireRole: (...roles) => [requireAuth, (req, res, next) =>
            roles.some(r => (r === 'Manager' ? isManager(req.user) : req.user.role === r))
                ? next()
                : res.status(403).json({ error: 'You do not have permission to do that.' })],
        isAdmin,
        isManager,
        isStaff: (u) => isAdmin(u) || isManager(u),
        branchOf: (u) => (u && u.branch) || null,
        audit: () => {}
    };
});

const AUTH = ['Authorization', 'Bearer test-token'];

const request = require('supertest');
const { createClient } = require('@supabase/supabase-js');

let app;
let query;
let result;

function createQuery() {
    query = {};
    for (const method of ['select', 'order', 'eq', 'ilike', 'insert', 'update', 'delete', 'single']) {
        query[method] = jest.fn(() => query);
    }
    query.then = (resolve, reject) => Promise.resolve(result).then(resolve, reject);
    return query;
}

const supabase = {
    from: jest.fn(createQuery),
    auth: {
        signInWithPassword: jest.fn(),
        admin: {
            createUser: jest.fn()
        }
    }
};

let helpers;

beforeAll(() => {
    createClient.mockReturnValue(supabase);
    app = require('../server');
    helpers = app;
});

beforeEach(() => {
    result = { data: [], error: null };
    supabase.from.mockClear();
    supabase.auth.signInWithPassword.mockResolvedValue({
        data: { user: null },
        error: { message: 'Invalid credentials' }
    });
    supabase.auth.admin.createUser.mockResolvedValue({
        data: { user: null },
        error: { message: 'Registration failed' }
    });
});

describe('API endpoints', () => {
    test.each([
        '/api/products',
        '/api/branches',
        '/api/services',
        '/api/testimonials',
        '/api/faqs',
        '/api/homepage-assets'
    ])('GET %s returns an array', async (path) => {
        const response = await request(app).get(path);

        expect(response.status).toBe(200);
        expect(Array.isArray(response.body)).toBe(true);
    });

    test('GET unknown endpoint returns 404', async () => {
        const response = await request(app).get('/api/does-not-exist');

        expect(response.status).toBe(404);
    });

        test('GET /api/branches/invalid-id rejects an invalid numeric ID', async () => {
        const response = await request(app).get('/api/branches/invalid-id');
        expect(response.status).toBe(400);
    });

    test('GET /api/products/string-id returns 404 if DB returns null', async () => {
        // Mock the database saying "not found" for a string ID
        result = { data: null, error: { code: 'PGRST116' } };
        const response = await request(app).get('/api/products/kitch-item');
        expect(response.status).toBe(404);
    });
});

describe('API request validation', () => {
    test.each([
        ['products', '/api/products'],
        ['branches', '/api/branches'],
        ['services', '/api/services'],
        ['testimonials', '/api/testimonials'],
        ['faqs', '/api/faqs'],
        ['quote requests', '/api/quote-requests']
    ])('POST %s rejects an empty body', async (_resource, path) => {
        const response = await request(app).post(path).set(...AUTH).send({});

        expect(response.status).toBe(400);
    });

    test.each([
        ['products', '/api/products/999999'],
        ['branches', '/api/branches/999999'],
        ['services', '/api/services/999999'],
        ['testimonials', '/api/testimonials/999999'],
        ['faqs', '/api/faqs/999999'],
        ['quote requests', '/api/quote-requests/999999']
    ])('PUT %s rejects an empty body', async (_resource, path) => {
        const response = await request(app).put(path).set(...AUTH).send({});

        expect(response.status).toBe(400);
    });

        test.each([
        '/api/branches/invalid-id',
        '/api/services/invalid-id',
        '/api/testimonials/invalid-id',
        '/api/faqs/invalid-id',
        '/api/quote-requests/invalid-id'
    ])('DELETE %s rejects an invalid ID', async (path) => {
        const response = await request(app).delete(path).set(...AUTH);

        expect(response.status).toBe(400);
    });

    test('DELETE /api/products/invalid-id accepts string IDs (returns 500 on DB error)', async () => {
        // Product IDs are strings within the database. The route passes the string to the DB.
        //the mock DB doesn't have this ID, so it returns an error (500).
        const response = await request(app).delete('/api/products/invalid-id').set(...AUTH);
        expect(response.status).toBe(500);
    });

    test('safeParse handles arrays, invalid JSON, and empty values', () => {
        expect(helpers.safeParse('["A", "B"]')).toEqual(['A', 'B']);
        expect(helpers.safeParse('not valid json')).toEqual([]);
        expect(helpers.safeParse(['A', 'B'])).toEqual(['A', 'B']);
        expect(helpers.safeParse(null)).toEqual([]);
        expect(helpers.safeParse('')).toEqual([]);
    });

    test('validateRequestBody accepts a non-empty object', () => {
        const req = { body: { name: 'Test' } };
        const res = {
            status: jest.fn().mockReturnThis(),
            json: jest.fn()
        };

        expect(helpers.validateRequestBody(req, res)).toBe(true);
        expect(res.status).not.toHaveBeenCalled();
        expect(res.json).not.toHaveBeenCalled();
    });

    test.each([
        [{ body: {} }, 'Request body cannot be empty'],
        [{}, 'Request body must be a valid JSON object'],
        [{ body: [] }, 'Request body must be a valid JSON object']
    ])('validateRequestBody rejects malformed bodies', (req, message) => {
        const res = {
            status: jest.fn().mockReturnThis(),
            json: jest.fn()
        };

        expect(helpers.validateRequestBody(req, res)).toBe(false);
        expect(res.status).toHaveBeenCalledWith(400);
        expect(res.json).toHaveBeenCalledWith({ error: message });
    });
});

describe('API query and database behavior', () => {
    test('GET /api/products parses JSON array fields and orders newest first', async () => {
        result = {
            data: [{
                id: 1,
                gallery: '["front.jpg"]',
                features: 'invalid JSON',
                finishes: null
            }],
            error: null
        };

        const response = await request(app).get('/api/products');

        expect(response.status).toBe(200);
        expect(response.body).toEqual([{
            id: 1,
            gallery: ['front.jpg'],
            features: [],
            finishes: []
        }]);
        expect(supabase.from).toHaveBeenCalledWith('products');
        expect(query.order).toHaveBeenCalledWith('created_at', { ascending: false });
    });

    test('GET /api/quote-requests applies branch filter for admins', async () => {
        const response = await request(app)
            .get('/api/quote-requests')
            .set(...AUTH)
            .query({ email: 'user_%@example.com', branch: 'North' });

        expect(response.status).toBe(200);

        // Admin path: branch is honoured, email is not used as a filter.
        expect(query.eq).toHaveBeenCalledWith('branch', 'North');
        expect(query.ilike).not.toHaveBeenCalled();
        expect(query.order).toHaveBeenCalledWith('created_at', { ascending: false });
    });

    test('POST /api/products creates a product and returns 201', async () => {
        const product = { name: 'Oak desk', price: 250 };
        result = { data: [{ id: 7, ...product }], error: null };

    const response = await request(app).post('/api/products').set(...AUTH).send(product);

        expect(response.status).toBe(201);
        expect(response.body).toEqual([{ id: 7, ...product }]);
        expect(query.insert).toHaveBeenCalledWith([product]);
    });

    test('GET /api/services returns database errors as 500 responses', async () => {
        result = { data: null, error: { message: 'Database unavailable' } };

        const response = await request(app).get('/api/services');

        expect(response.status).toBe(500);
        expect(response.body).toEqual({ error: 'Database unavailable' });
    });
});

describe('Authentication endpoints', () => {
    test('POST /api/auth/login rejects invalid credentials', async () => {
        const response = await request(app)
            .post('/api/auth/login')
            .send({ email: 'qa-invalid-user@example.com', password: 'wrong-password' });

        expect(response.status).toBe(401);
        expect(response.body).toHaveProperty('error');
    });

    test('POST /api/auth/register returns a client error when registration fails', async () => {
        const response = await request(app)
            .post('/api/auth/register')
            .send({ fullName: '', email: 'invalid-email', password: '', phone: '' });

        expect(response.status).toBe(400);
        expect(response.body).toHaveProperty('error');
    });
});