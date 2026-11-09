
const cron = require('node-cron');
const zlib = require('zlib');
const crypto = require('crypto');
const { promisify } = require('util');

const gzip = promisify(zlib.gzip);

const BUCKET =
    process.env.BACKUP_BUCKET || 'woodlands-backups';

const RETENTION = Math.max(
    1,
    parseInt(process.env.BACKUP_RETENTION, 10) || 14
);

const MAX_AGE_HOURS = Math.max(
    1,
    parseInt(process.env.BACKUP_MAX_AGE_HOURS, 10) || 26
);

const PREFIX = 'woodlands-backup-';
const PAGE = 1000;
const BATCH_SIZE = 500;
const DELETE_BATCH_SIZE = 100;

const DEFAULT_CRON =
    process.env.BACKUP_CRON || '0 12 * * *';

const DEFAULT_TZ =
    process.env.BACKUP_TIMEZONE || 'Africa/Johannesburg';

const SETTINGS_FILE = 'backup-settings.json';

const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)$/;

// Parents first for restoration.
// Reverse order for rollback deletion.
//
// Confirmed database relationships:
// email_change_requests -> app_users
// pending_email_changes -> app_users
// notifications -> app_users
// product_deletion_requests -> app_users
// products -> app_users
// quote_requests -> app_users
// services -> app_users
//
// No other foreign-key dependencies were returned
// by the supplied database schema query.
const TABLES = [
    'app_users',
    'branches',
    'products',
    'services',
    'faqs',
    'testimonials',
    'homepage_assets',
    'quote_requests',
    'deletion_requests',
    'account_archive',
    'email_change_requests',
    'pending_email_changes',
    'notifications',
    'product_deletion_requests'
];

// Never preserve or restore active one-time recovery credentials.
const STRIP_COLUMNS = {
    account_archive: [
        'code_hash',
        'code_expires_at',
        'code_sent_at',
        'code_attempts'
    ]
};

function isPlainObject(value) {
    return value !== null &&
        typeof value === 'object' &&
        !Array.isArray(value);
}

function cleanRows(table, rows) {
    const strip = STRIP_COLUMNS[table] || [];

    return rows.map(row => {
        const clean = { ...row };

        for (const column of strip) {
            delete clean[column];
        }

        return clean;
    });
}

function validateSnapshotRows(table, rows) {
    if (!Array.isArray(rows)) {
        throw new Error(
            `Snapshot table "${table}" must contain an array.`
        );
    }

    const seenIds = new Set();

    rows.forEach((row, index) => {
        if (!isPlainObject(row)) {
            throw new Error(
                `Invalid row at index ${index} in table "${table}".`
            );
        }

        if (
            row.id === undefined ||
            row.id === null ||
            (typeof row.id !== 'string' && typeof row.id !== 'number') ||
            (typeof row.id === 'number' && !Number.isFinite(row.id))
        ) {
            throw new Error(
                `Invalid ID at index ${index} in table "${table}".`
            );
        }

        const id = String(row.id);

        if (seenIds.has(id)) {
            throw new Error(
                `Duplicate ID "${id}" in table "${table}".`
            );
        }

        seenIds.add(id);
    });

    return rows;
}

module.exports = function createBackupService(app, supabase, auth) {
    let running = false;
    let last = null;
    let task = null;

    let settings = {
        enabled: process.env.BACKUP_ENABLED !== 'false',
        cron: DEFAULT_CRON
    };

    function cronToTime(expression) {
        const match =
            /^(\d{1,2}) (\d{1,2}) \* \* \*$/.exec(expression);

        return match
            ? `${match[2].padStart(2, '0')}:${match[1].padStart(2, '0')}`
            : null;
    }

    async function ensureBucket() {
        const { data, error } =
            await supabase.storage.listBuckets();

        if (error) {
            throw new Error(
                'Cannot list storage buckets: ' + error.message
            );
        }

        const exists = (data || []).some(
            bucket => bucket.name === BUCKET
        );

        if (!exists) {
            const created = await supabase.storage.createBucket(
                BUCKET,
                { public: false }
            );

            if (created.error) {
                throw new Error(
                    'Cannot create backup bucket: ' +
                    created.error.message
                );
            }

            console.log(
                `[backup] created private bucket "${BUCKET}"`
            );
        }
    }

    async function loadSettings() {
        try {
            await ensureBucket();

            const { data, error } =
                await supabase.storage
                    .from(BUCKET)
                    .download(SETTINGS_FILE);

            if (error || !data) {
                return;
            }

            const saved = JSON.parse(await data.text());

            if (typeof saved.enabled === 'boolean') {
                settings.enabled = saved.enabled;
            }

            if (
                typeof saved.cron === 'string' &&
                cron.validate(saved.cron)
            ) {
                settings.cron = saved.cron;
            }
        } catch (error) {
            console.error(
                '[backup] could not load settings; using defaults:',
                error.message
            );
        }
    }

    async function saveSettings(updatedBy) {
        const body = Buffer.from(JSON.stringify({
            ...settings,
            updated_by: updatedBy,
            updated_at: new Date().toISOString()
        }));

        const { error } = await supabase.storage
            .from(BUCKET)
            .upload(SETTINGS_FILE, body, {
                contentType: 'application/json',
                upsert: true
            });

        if (error) {
            throw new Error(
                'Could not save settings: ' + error.message
            );
        }
    }

    function applySchedule() {
        if (task) {
            task.stop();
            task = null;
        }

        if (!settings.enabled) {
            console.log('[backup] scheduler disabled');
            return;
        }

        if (!cron.validate(settings.cron)) {
            console.error(
                `[backup] invalid schedule "${settings.cron}"`
            );
            return;
        }

        try {
            task = cron.schedule(
                settings.cron,
                () => {
                    runBackup('schedule').catch(error => {
                        console.error(
                            '[backup] scheduled backup error:',
                            error.message
                        );
                    });
                },
                { timezone: DEFAULT_TZ }
            );

            console.log(
                `[backup] scheduled "${settings.cron}" ` +
                `(${DEFAULT_TZ}), keeping ${RETENTION} backups`
            );
        } catch (error) {
            task = null;

            console.error(
                '[backup] could not apply schedule:',
                error.message
            );
        }
    }

    async function exportTable(table) {
        const rows = [];

        for (let from = 0; ; from += PAGE) {
            const { data, error } = await supabase
                .from(table)
                .select('*')
                .order('id', { ascending: true })
                .range(from, from + PAGE - 1);

            if (error) {
                throw new Error(error.message);
            }

            const pageRows = data || [];

            rows.push(...pageRows);

            if (pageRows.length < PAGE) {
                break;
            }
        }

        return cleanRows(table, rows);
    }

    async function listBackups() {
        const backups = [];

        for (let offset = 0; ; offset += PAGE) {
            const { data, error } = await supabase.storage
                .from(BUCKET)
                .list('', {
                    limit: PAGE,
                    offset,
                    sortBy: {
                        column: 'created_at',
                        order: 'desc'
                    }
                });

            if (error) {
                throw new Error(
                    'Could not list backups: ' + error.message
                );
            }

            const files = data || [];

            backups.push(
                ...files
                    .filter(file =>
                        file.name &&
                        file.name.startsWith(PREFIX)
                    )
                    .map(file => ({
                        name: file.name,
                        size: file.metadata?.size || null,
                        created_at: file.created_at
                    }))
            );

            if (files.length < PAGE) {
                break;
            }
        }

        return backups;
    }

    async function prune() {
        const all = await listBackups();

        const old = all
            .slice(RETENTION)
            .map(file => file.name);

        if (old.length > 0) {
            const { error } = await supabase.storage
                .from(BUCKET)
                .remove(old);

            if (error) {
                console.error(
                    '[backup] prune failed:',
                    error.message
                );

                return 0;
            }

            console.log(
                `[backup] removed ${old.length} old backup(s)`
            );
        }

        return old.length;
    }

    // Normal backups acquire their own lock.
    // A safety backup during rollback reuses the import lock.
    async function runBackup(
        trigger = 'schedule',
        { lockAlreadyHeld = false } = {}
    ) {
        if (running && !lockAlreadyHeld) {
            return {
                ok: false,
                skipped: true,
                reason: 'A backup or import is already running.'
            };
        }

        if (lockAlreadyHeld && !running) {
            return {
                ok: false,
                skipped: true,
                reason: 'The expected import lock is not held.'
            };
        }

        if (!lockAlreadyHeld) {
            running = true;
        }

        const startedAt = new Date();

        try {
            await ensureBucket();

            const selected = TABLES;
            const data = {};
            const counts = {};
            const errors = {};

            for (const table of selected) {
                try {
                    data[table] = await exportTable(table);

                    validateSnapshotRows(table, data[table]);

                    counts[table] = data[table].length;
                } catch (error) {
                    errors[table] = error.message;
                }
            }

            // Never upload a backup that silently omits failed tables.
            if (Object.keys(errors).length > 0) {
                throw Object.assign(
                    new Error(
                        'Backup aborted because one or more tables could not be exported.'
                    ),
                    { tableErrors: errors }
                );
            }

            const meta = {
                version: 2,
                created_at: startedAt.toISOString(),
                trigger,
                tables: TABLES,
                counts
            };

            const contents = Buffer.from(
                JSON.stringify({ meta, data })
            );

            const file = await gzip(contents);

            const timestamp = startedAt.toISOString()
                .replace(/[:.]/g, '-');

            const randomSuffix = crypto
                .randomBytes(4)
                .toString('hex');

            const name =
                `${PREFIX}${timestamp}-${randomSuffix}.json.gz`;

            const { error: uploadError } =
                await supabase.storage
                    .from(BUCKET)
                    .upload(name, file, {
                        contentType: 'application/gzip',
                        upsert: false
                    });

            if (uploadError) {
                throw new Error(
                    'Upload failed: ' + uploadError.message
                );
            }

            const pruned = await prune();

            last = {
                ok: true,
                name,
                trigger,
                at: startedAt.toISOString(),
                bytes: file.length,
                counts,
                pruned
            };

            console.log(
                `[backup] OK ${name}; ` +
                `tables=${JSON.stringify(counts)}`
            );

            return last;
        } catch (error) {
            last = {
                ok: false,
                trigger,
                at: startedAt.toISOString(),
                error: error.message,
                table_errors: error.tableErrors || {}
            };

            console.error(
                '[backup] FAILED:',
                error.message
            );

            return last;
        } finally {
            if (!lockAlreadyHeld) {
                running = false;
            }
        }
    }

    // Admin-only backup listing.
    app.get(
        '/api/admin/backups',
        auth.requireRole('Admin'),
        async (req, res) => {
            try {
                return res.json({
                    bucket: BUCKET,
                    retention: RETENTION,
                    enabled: settings.enabled,
                    schedule: settings.cron,
                    time: cronToTime(settings.cron),
                    timezone: DEFAULT_TZ,
                    last_run: last,
                    backups: await listBackups()
                });
            } catch (error) {
                return res.status(500).json({
                    error: error.message
                });
            }
        }
    );

    // Run a manual backup.
    app.post(
        '/api/admin/backups/run',
        auth.requireRole('Admin'),
        async (req, res) => {
            const result = await runBackup(
                'manual:' + (req.user?.email || 'Admin')
            );

            const status = result.ok
                ? 201
                : result.skipped
                    ? 409
                    : 500;

            return res.status(status).json(result);
        }
    );

    // Change the daily backup time or enable/disable the scheduler.
    app.put(
        '/api/admin/backups/schedule',
        auth.requireRole('Admin'),
        async (req, res) => {
            const body = req.body || {};

            const has = key =>
                Object.prototype.hasOwnProperty.call(body, key);

            if (!has('time') && !has('enabled')) {
                return res.status(400).json({
                    error: 'Nothing to update.'
                });
            }

            const next = { ...settings };

            if (has('enabled')) {
                if (typeof body.enabled !== 'boolean') {
                    return res.status(400).json({
                        error: 'enabled must be true or false.'
                    });
                }

                next.enabled = body.enabled;
            }

            if (has('time')) {
                const match = TIME_RE.exec(String(body.time));

                if (!match) {
                    return res.status(400).json({
                        error: 'time must be 24-hour HH:MM, e.g. "12:00".'
                    });
                }

                next.cron =
                    `${parseInt(match[2], 10)} ` +
                    `${parseInt(match[1], 10)} * * *`;
            }

            const previous = settings;

            try {
                settings = next;
                await saveSettings(req.user?.email || 'Admin');
            } catch (error) {
                settings = previous;

                return res.status(500).json({
                    error: error.message
                });
            }

            applySchedule();

            auth.audit('backup_schedule_changed', req, {
                cron: settings.cron,
                enabled: settings.enabled
            });

            return res.json({
                enabled: settings.enabled,
                schedule: settings.cron,
                time: cronToTime(settings.cron),
                timezone: DEFAULT_TZ
            });
        }
    );

    async function liveIds(table) {
        const ids = [];

        for (let from = 0; ; from += PAGE) {
            const { data, error } = await supabase
                .from(table)
                .select('id')
                .order('id', { ascending: true })
                .range(from, from + PAGE - 1);

            if (error) {
                throw new Error(
                    `Could not retrieve IDs from "${table}": ${error.message}`
                );
            }

            const rows = data || [];

            ids.push(
                ...rows.map(row => String(row.id))
            );

            if (rows.length < PAGE) {
                break;
            }
        }

        return ids;
    }

    async function liveAdminIds() {
        const ids = new Set();

        for (let from = 0; ; from += PAGE) {
            const { data, error } = await supabase
                .from('app_users')
                .select('id')
                .ilike('role', 'admin')
                .order('id', { ascending: true })
                .range(from, from + PAGE - 1);

            if (error) {
                throw new Error(
                    'Could not retrieve existing Admin accounts: ' +
                    error.message
                );
            }

            const rows = data || [];

            for (const user of rows) {
                ids.add(String(user.id));
            }

            if (rows.length < PAGE) {
                break;
            }
        }

        return ids;
    }

   // Export current data. Admin only.
app.get(
    '/api/admin/export',
    auth.requireRole('Admin'),
    async (req, res) => {
        if (running) {
            return res.status(409).json({
                error: 'A backup or import is already running. Please try again later.'
            });
        }

        running = true;

        try {
            const only =
                typeof req.query.table === 'string' &&
                req.query.table.trim()
                    ? req.query.table.trim()
                    : null;

            if (only && !TABLES.includes(only)) {
                return res.status(400).json({
                    error: 'Unknown table.'
                });
            }

            const selected = only ? [only] : TABLES;
            const data = {};
            const counts = {};
            const errors = {};

            for (const table of selected) {
                try {
                    data[table] = await exportTable(table);

                    validateSnapshotRows(table, data[table]);

                    counts[table] = data[table].length;
                } catch (error) {
                    errors[table] = error.message;
                }
            }

            if (Object.keys(errors).length > 0) {
                return res.status(500).json({
                    error: 'Export failed. No complete export was produced.',
                    table_errors: errors
                });
            }

            auth.audit('backup_export', req, {
                tables: selected.join(',')
            });

            return res.json({
                counts,
                data
            });
        } catch (error) {
            console.error('[backup] export failed:', error.message);

            return res.status(500).json({
                error: 'The export could not be completed.'
            });
        } finally {
            running = false;
        }
    }
);
    // Import / restore / rollback.
    app.post(
        '/api/admin/import',
        auth.requireRole('Admin'),
        async (req, res) => {
            const body = req.body || {};

            const {
                data,
                mode,
                table,
                dryRun,
                confirm
            } = body;

            if (!isPlainObject(data)) {
                return res.status(400).json({
                    error: 'A valid snapshot data object is required.'
                });
            }

            if (!['restore', 'rollback'].includes(mode)) {
                return res.status(400).json({
                    error: 'mode must be "restore" or "rollback".'
                });
            }

            if (
                table !== undefined &&
                table !== null &&
                (typeof table !== 'string' ||
                    !TABLES.includes(table))
            ) {
                return res.status(400).json({
                    error: 'Unknown table.'
                });
            }

            if (
                dryRun !== undefined &&
                typeof dryRun !== 'boolean'
            ) {
                return res.status(400).json({
                    error: 'dryRun must be true or false.'
                });
            }

            if (
                confirm !== undefined &&
                typeof confirm !== 'boolean'
            ) {
                return res.status(400).json({
                    error: 'confirm must be true or false.'
                });
            }

            const unknownTables = Object.keys(data)
                .filter(key => !TABLES.includes(key));

            if (unknownTables.length > 0) {
                return res.status(400).json({
                    error: 'The snapshot contains unsupported tables.',
                    tables: unknownTables
                });
            }

            const wanted = TABLES.filter(
                name =>
                    Object.prototype.hasOwnProperty.call(data, name) &&
                    (!table || table === name)
            );

            if (wanted.length === 0) {
                return res.status(400).json({
                    error: 'The snapshot has no supported tables to process.'
                });
            }

            if (table && !wanted.includes(table)) {
                return res.status(400).json({
                    error: 'The selected table is missing from the snapshot.'
                });
            }

            const apply = dryRun === false && confirm === true;

            if (dryRun === false && confirm !== true) {
                return res.status(400).json({
                    error: 'Applying a restore requires confirm: true.',
                    dry_run: true
                });
            }

            // A full rollback involving app_users must include every table.
            if (
                mode === 'rollback' &&
                wanted.includes('app_users') &&
                (
                    !!table ||
                    TABLES.some(name =>
                        !Object.prototype.hasOwnProperty.call(data, name)
                    )
                )
            ) {
                return res.status(400).json({
                    error:
                        'Rolling back app_users requires a complete snapshot ' +
                        'containing every supported table and no table filter.'
                });
            }

            // Validate all selected tables before acquiring a lock or writing.
            try {
                for (const name of wanted) {
                    validateSnapshotRows(name, data[name]);
                }
            } catch (error) {
                return res.status(400).json({
                    error: error.message
                });
            }

            // Acquire the lock synchronously before the first await.
            if (apply && running) {
                return res.status(409).json({
                    error: 'A backup or import is already running.'
                });
            }

            if (apply) {
                running = true;
            }

            try {
                let safetyBackup = null;

                if (apply) {
                safetyBackup = await runBackup(
                    `pre-${mode}:` +
                    (req.user?.email || 'Admin'),
                    { lockAlreadyHeld: true }
                );
                    if (!safetyBackup.ok) {
                        return res.status(503).json({
                            error:
                                `The ${mode} was stopped because a complete safety ` +
                                'backup could not be created.',
                            backup: safetyBackup
                        });
                    }
                }

                const report = {};
                const plan = {};

                const protectedAdmins = wanted.includes('app_users')
                    ? await liveAdminIds()
                    : new Set();

                // Build the complete plan before making any database changes.
                for (const name of wanted) {
                    const snapshotRows = data[name];

                    const now = await liveIds(name);
                    const nowSet = new Set(now);

                    let cleaned = cleanRows(name, snapshotRows);

// Never restore active one-time recovery credentials.
// Exported backups intentionally omit these fields.
if (name === 'account_archive') {
    cleaned = cleaned.map(row => ({
        ...row,
        code_hash: null,
        code_expires_at: null,
        code_sent_at: null,
        code_attempts: 0
    }));
}

// Never overwrite existing Admins or introduce new Admins.
const rows = name === 'app_users'
    ? cleaned.filter(row => {
        const userId = String(row.id);

        const isExistingAdmin =
            protectedAdmins.has(userId);

        const isAdminRole =
            String(row.role || '')
                .trim()
                .toLowerCase() === 'admin';

        return !isExistingAdmin && !isAdminRole;
    })
    : cleaned;

                    const snapshotIds = new Set(
                        cleaned.map(row => String(row.id))
                    );

                    const toAdd = rows.filter(
                        row => !nowSet.has(String(row.id))
                    ).length;

                    const toUpdate = rows.length - toAdd;

                    const toDelete = mode === 'rollback'
                    ? now.filter(id =>
                        !snapshotIds.has(id) &&
                        !(
                            name === 'app_users' &&
                            protectedAdmins.has(id)
                        )
                    )
                    : [];

                    plan[name] = {
                        rows,
                        toDelete
                    };

                    report[name] = {
                        in_snapshot: snapshotRows.length,
                        in_database: now.length,
                        to_add: toAdd,
                        to_update: toUpdate,
                        to_delete: toDelete.length,
                        protected_admins:
                            name === 'app_users'
                                ? protectedAdmins.size
                                : 0
                    };
                }

                if (apply) {
                    let writeFailed = false;

                    // Restore parents before dependent records.
                    for (const name of wanted) {
                        const rows = plan[name].rows;

                        for (
                            let index = 0;
                            index < rows.length;
                            index += BATCH_SIZE
                        ) {
                            const batch = rows.slice(
                                index,
                                index + BATCH_SIZE
                            );

                            const { error } = await supabase
                                .from(name)
                                .upsert(batch, {
                                    onConflict: 'id'
                                });

                            if (error) {
                                report[name].error = error.message;
                                writeFailed = true;
                                break;
                            }
                        }

                        if (writeFailed) {
                            break;
                        }
                    }

                    // Never delete records if an upsert failed.
                    // Rollback deletion is performed in reverse table order.
                    if (!writeFailed && mode === 'rollback') {
                        for (const name of [...wanted].reverse()) {
                            const ids = plan[name].toDelete;

                            for (
                                let index = 0;
                                index < ids.length;
                                index += DELETE_BATCH_SIZE
                            ) {
                                const batch = ids.slice(
                                    index,
                                    index + DELETE_BATCH_SIZE
                                );

                                const { error } = await supabase
                                    .from(name)
                                    .delete()
                                    .in('id', batch);

                                if (error) {
                                    report[name].error = error.message;
                                    writeFailed = true;
                                    break;
                                }
                            }

                            // Stop all remaining deletions after the first failure.
                            if (writeFailed) {
                                break;
                            }
                        }
                    }
                }

                const ok = Object.values(report).every(
                    item => !item.error
                );

                auth.audit(
                    apply ? 'backup_import' : 'backup_import_preview',
                    req,
                    {
                        mode,
                        table: table || 'all',
                        ok
                    }
                );

                const result = {
                    ok,
                    dry_run: !apply,
                    mode,
                    tables: report
                };

                if (safetyBackup) {
                    result.safety_backup = safetyBackup.name;
                }

                return res.status(ok ? 200 : 500).json(result);
            } catch (error) {
                console.error(
                    '[backup] import failed:',
                    error.message
                );

                return res.status(500).json({
                    error: 'The import could not be completed.',
                    detail: error.message
                });
            } finally {
                if (apply) {
                    running = false;
                }
            }
        }
    );

    // Short-lived download link for a stored backup.
    app.get(
        '/api/admin/backups/:name/download',
        auth.requireRole('Admin'),
        async (req, res) => {
            if (
                !/^woodlands-backup-[\w.-]+\.json\.gz$/.test(
                    req.params.name
                )
            ) {
                return res.status(400).json({
                    error: 'Invalid backup name.'
                });
            }

            const { data, error } = await supabase.storage
                .from(BUCKET)
                .createSignedUrl(req.params.name, 60);

            if (error || !data?.signedUrl) {
                return res.status(404).json({
                    error: 'Backup not found.'
                });
            }

            return res.json({
                url: data.signedUrl,
                expires_in: 60
            });
        }
    );

    async function start() {
        await loadSettings();
        applySchedule();

        if (!settings.enabled) {
            return;
        }

                // Catch-up after a restart or sleep.
        // Retries every 15 minutes until a fresh backup exists,
        // so a single transient Supabase blip does not leave us
        // blind for a whole day.
        const RETRY_MS = 15 * 60 * 1000;
        const MAX_ATTEMPTS = 8;   // ~2 hours of retries

        async function catchUp(attempt = 1) {
            try {
                if (!settings.enabled) {
                    return;
                }

                await ensureBucket();

                const backups = await listBackups();
                const newest = backups[0];

                const newestTime = newest
                    ? new Date(newest.created_at).getTime()
                    : NaN;

                const ageHours = Number.isFinite(newestTime)
                    ? (Date.now() - newestTime) / 36e5
                    : Infinity;

                if (ageHours > MAX_AGE_HOURS) {
                    console.log(
                        `[backup] latest backup is stale; running catch-up backup (attempt ${attempt}/${MAX_ATTEMPTS})`
                    );

                    const result = await runBackup('startup-catchup');

                    if (!result || result.ok !== true) {
                        throw new Error(
                            result?.error || 'catch-up backup did not succeed'
                        );
                    }

                    console.log('[backup] catch-up backup completed');
                    return;
                }

                console.log('[backup] latest backup is fresh; nothing to catch up');
            } catch (error) {
                console.error(
                    `[backup] catch-up attempt ${attempt}/${MAX_ATTEMPTS} failed:`,
                    error.message
                );

                if (attempt < MAX_ATTEMPTS) {
                    setTimeout(
                        () => catchUp(attempt + 1),
                        RETRY_MS
                    );
                } else {
                    console.error(
                        '[backup] catch-up gave up after ' +
                        MAX_ATTEMPTS + ' attempts; check Supabase and cron settings.'
                    );
                }
            }
        }

        setTimeout(() => catchUp(1), 30 * 1000);
    }

    return {
        start,
        runBackup,
        listBackups
    };
};
