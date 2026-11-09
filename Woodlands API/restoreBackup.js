
require('dotenv').config();

const fs = require('fs');
const zlib = require('zlib');
const crypto = require('crypto');
const { createClient } = require('@supabase/supabase-js');

const BUCKET =
    process.env.BACKUP_BUCKET || 'woodlands-backups';

const PAGE_SIZE = 1000;
const BATCH_SIZE = 500;

// Keep app_users before tables that reference it.
// The remaining order follows the schema relationships we checked.
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

function createSupabaseClient() {
    const url = process.env.SUPABASE_URL;
    const key = process.env.SUPABASE_SECRET_KEY;

    if (!url || !key) {
        throw new Error(
            'SUPABASE_URL and SUPABASE_SECRET_KEY are required for database access.'
        );
    }

    return createClient(url, key, {
        auth: {
            persistSession: false,
            autoRefreshToken: false,
            detectSessionInUrl: false
        }
    });
}

async function loadBackup(source) {
    if (fs.existsSync(source)) {
        return fs.readFileSync(source);
    }

    const supabase = createSupabaseClient();

    const { data, error } = await supabase.storage
        .from(BUCKET)
        .download(source);

    if (error || !data) {
        throw new Error(
            'Could not download backup: ' +
            (error?.message || 'File not found.')
        );
    }

    return Buffer.from(await data.arrayBuffer());
}

async function ensureBackupBucket(supabase) {
    const { data: buckets, error } =
        await supabase.storage.listBuckets();

    if (error) {
        throw new Error(
            'Could not check backup storage: ' + error.message
        );
    }

    const exists = (buckets || []).some(
        bucket => bucket.name === BUCKET
    );

    if (!exists) {
        const { error: createError } =
            await supabase.storage.createBucket(BUCKET, {
                public: false
            });

        if (createError) {
            throw new Error(
                'Could not create the private backup bucket: ' +
                createError.message
            );
        }

        console.log(`Created private storage bucket: ${BUCKET}`);
    }
}

async function exportTable(supabase, table) {
    const allRows = [];
    let from = 0;

    while (true) {
        const to = from + PAGE_SIZE - 1;

        const { data, error } = await supabase
            .from(table)
            .select('*')
            .order('id', { ascending: true })
            .range(from, to);

        if (error) {
            throw new Error(
                `Could not export "${table}": ${error.message}`
            );
        }

        const rows = data || [];

        allRows.push(...rows);

        if (rows.length < PAGE_SIZE) {
            break;
        }

        from += PAGE_SIZE;
    }

    return cleanRows(table, allRows);
}

async function createSafetyBackup(supabase) {
    console.log('');
    console.log('Creating pre-restore safety backup...');
    console.log('Exporting all database tables.');

    await ensureBackupBucket(supabase);

    const data = {};
    const counts = {};

    // Export every table, even when restoring only one table.
    for (const table of TABLES) {
        data[table] = await exportTable(supabase, table);
        counts[table] = data[table].length;

        console.log(
            `  ${table}: ${counts[table]} rows backed up`
        );
    }

    const createdAt = new Date().toISOString();

    const backup = {
        meta: {
            version: 2,
            created_at: createdAt,
            trigger: 'pre-standalone-restore'
        },
        data
    };

    const compressed = zlib.gzipSync(
        Buffer.from(JSON.stringify(backup), 'utf8')
    );

    // Validate the complete backup before uploading it.
    validateBackup(compressed, null);

    const timestamp = createdAt.replace(/[:.]/g, '-');

    const randomSuffix = crypto
        .randomBytes(4)
        .toString('hex');

    const backupName =
        `woodlands-backup-pre-restore-${timestamp}-` +
        `${randomSuffix}.json.gz`;

    // Upload the safety backup.
    const { error } = await supabase.storage
        .from(BUCKET)
        .upload(backupName, compressed, {
            contentType: 'application/gzip',
            upsert: false
        });

    if (error) {
        throw new Error(
            'Safety backup upload failed: ' + error.message
        );
    }

    // Download the uploaded file and verify its contents.
    const { data: uploadedFile, error: verificationError } =
        await supabase.storage
            .from(BUCKET)
            .download(backupName);

    if (verificationError || !uploadedFile) {
        throw new Error(
            'Safety backup verification failed: ' +
            (verificationError?.message ||
                'Uploaded file could not be downloaded.')
        );
    }

    const downloadedBuffer = Buffer.from(
        await uploadedFile.arrayBuffer()
    );

    // Confirm that the downloaded file matches the original.
    if (!downloadedBuffer.equals(compressed)) {
        throw new Error(
            'Safety backup verification failed: downloaded file does not match the uploaded backup.'
        );
    }

    // Confirm that the downloaded backup can be parsed and validated.
    validateBackup(downloadedBuffer, null);

    console.log('');
    console.log('Safety backup successfully uploaded and verified.');
    console.log(`Backup file: ${backupName}`);
    console.log(`Total tables: ${TABLES.length}`);
    console.log(
        `Total rows: ${Object.values(counts).reduce(
            (sum, count) => sum + count,
            0
        )}`
    );

    return backupName;
}

function validateBackup(buffer, only) {
    let backup;

    try {
        backup = JSON.parse(
            zlib.gunzipSync(buffer).toString('utf8')
        );
    } catch (error) {
        throw new Error(
            'The selected file is not a valid gzip backup: ' +
            error.message
        );
    }

    // Validate the backup structure first.
    if (!isPlainObject(backup) || !isPlainObject(backup.meta)) {
        throw new Error(
            'The backup metadata is missing or invalid.'
        );
    }

    if (![1, 2].includes(backup.meta.version)) {
        throw new Error(
            'Unsupported backup version. Expected version 1 or 2.'
        );
    }

    if (!isPlainObject(backup.data)) {
        throw new Error(
            'The backup data section is invalid.'
        );
    }

    // Reject unsupported tables.
    const unknownTables = Object.keys(backup.data)
        .filter(table => !TABLES.includes(table));

    if (unknownTables.length > 0) {
        throw new Error(
            'The backup contains unsupported tables: ' +
            unknownTables.join(', ')
        );
    }

    // Full restores must contain all supported tables.
    if (!only) {
        const missingTables = TABLES.filter(
            table => !Object.prototype.hasOwnProperty.call(
                backup.data,
                table
            )
        );

        if (missingTables.length > 0) {
            throw new Error(
                'The backup is incomplete. Missing tables: ' +
                missingTables.join(', ') +
                '. Use --table only when intentionally restoring a single table.'
            );
        }
    }

    // Validate every included table and its records.
    for (const [table, rows] of Object.entries(backup.data)) {
        if (!Array.isArray(rows)) {
            throw new Error(
                `Backup table "${table}" must contain an array.`
            );
        }

        const ids = new Set();

        for (const row of rows) {
            if (!isPlainObject(row)) {
                throw new Error(
                    `Backup table "${table}" contains an invalid row.`
                );
            }

            if (
                row.id === undefined ||
                row.id === null ||
                !['string', 'number'].includes(typeof row.id) ||
                String(row.id).trim() === '' ||
                (typeof row.id === 'number' &&
                    !Number.isFinite(row.id))
            ) {
                throw new Error(
                    `Backup table "${table}" contains a row without a valid ID.`
                );
            }

            const id = String(row.id);

            if (ids.has(id)) {
                throw new Error(
                    `Backup table "${table}" contains duplicate IDs.`
                );
            }

            ids.add(id);
        }
    }

    // A requested individual table must exist in the backup.
    if (only && !Array.isArray(backup.data[only])) {
        throw new Error(
            `The backup does not contain the selected table "${only}".`
        );
    }

    return backup;
}

function parseArguments(args) {
    let source = null;
    let only = null;
    let apply = false;

    for (let index = 0; index < args.length; index++) {
        const argument = args[index];

        if (argument === '--apply') {
            if (apply) {
                throw new Error(
                    'The --apply argument was supplied more than once.'
                );
            }

            apply = true;
            continue;
        }

        if (argument === '--table') {
            if (only !== null) {
                throw new Error(
                    'The --table argument was supplied more than once.'
                );
            }

            const next = args[index + 1];

            if (!next || next.startsWith('--')) {
                throw new Error(
                    '--table must be followed by a table name.'
                );
            }

            if (!TABLES.includes(next)) {
                throw new Error(
                    '--table must be followed by one of these supported tables:\n' +
                    TABLES.join(', ')
                );
            }

            only = next;
            index++;
            continue;
        }

        if (argument.startsWith('--')) {
            throw new Error(
                `Unknown command-line argument: ${argument}`
            );
        }

        if (source !== null) {
            throw new Error(
                'Only one backup file or storage backup name may be supplied.'
            );
        }

        source = argument;
    }

    if (!source) {
        throw new Error(
            'Usage: node restoreBackup.js <file.json.gz | name-in-bucket> ' +
            '[--table table_name] [--apply]'
        );
    }

    return { source, only, apply };
}

async function getExistingAdminIds(supabase) {
    const adminIds = new Set();

    let from = 0;

    while (true) {
        const to = from + PAGE_SIZE - 1;

        const { data, error } = await supabase
            .from('app_users')
            .select('id')
            .ilike('role', 'admin')
            .order('id', { ascending: true })
            .range(from, to);

        if (error) {
            throw new Error(
                'Could not retrieve existing Admin accounts: ' +
                error.message
            );
        }

        const rows = data || [];

        for (const user of rows) {
            adminIds.add(String(user.id));
        }

        if (rows.length < PAGE_SIZE) {
            break;
        }

        from += PAGE_SIZE;
    }

    return adminIds;
}

function protectAdminRows(table, rows, protectedAdmins) {
    if (table !== 'app_users') {
        return rows;
    }

    return rows.filter(row => {
        const userId = String(row.id);

        const isExistingAdmin =
            protectedAdmins.has(userId);

        const isAdminRole =
            String(row.role || '').trim().toLowerCase() === 'admin';

        return !isExistingAdmin && !isAdminRole;
    });
}

async function restore() {
    const { source, only, apply } =
        parseArguments(process.argv.slice(2));

    const buffer = await loadBackup(source);
    const backup = validateBackup(buffer, only);

    const selectedTables = TABLES.filter(
        table =>
            Array.isArray(backup.data[table]) &&
            (!only || only === table)
    );

    if (selectedTables.length === 0) {
        throw new Error(
            'The backup does not contain any supported tables to restore.'
        );
    }

    console.log('');
    console.log('Backup information');
    console.log('------------------');
    console.log(`Created: ${backup.meta.created_at || 'Unknown'}`);
    console.log(`Trigger: ${backup.meta.trigger || 'Unknown'}`);
    console.log(`Version: ${backup.meta.version}`);
    console.log(`Tables: ${selectedTables.length}`);
    console.log('');

    console.log(
        apply
            ? 'MODE: APPLY - writing to the database'
            : 'MODE: DRY RUN - no database changes will be made'
    );

    console.log('');

    // Dry-run mode: preview only. No database changes or safety backup.
    if (!apply) {
        for (const table of selectedTables) {
            console.log(
                `  ${table}: ${backup.data[table].length} rows would be restored`
            );
        }

        console.log('');
        console.log('Preview complete. No data was changed.');
        console.log('Add --apply to perform the restore.');

        return;
    }

    const supabase = createSupabaseClient();

    // Create and verify a full safety backup before changing anything.
    console.log('');
    console.log('Preparing database safety backup...');

    const safetyBackupName = await createSafetyBackup(supabase);

    console.log('');
    console.log('Safety backup confirmed:');
    console.log(safetyBackupName);
    console.log('Proceeding with restore.');
    console.log('');

    // Existing Admin accounts must never be overwritten by a backup.
    const protectedAdmins = selectedTables.includes('app_users')
        ? await getExistingAdminIds(supabase)
        : new Set();

    console.log(
        `Existing Admin accounts protected: ${protectedAdmins.size}`
    );

    console.log('');

    let totalRestored = 0;
    let totalSkipped = 0;

    for (const table of selectedTables) {
        const rows = cleanRows(table, backup.data[table]);

        const safeRows = protectAdminRows(
            table,
            rows,
            protectedAdmins
        );

        let completed = 0;

        for (
            let index = 0;
            index < safeRows.length;
            index += BATCH_SIZE
        ) {
            const batch = safeRows.slice(
                index,
                index + BATCH_SIZE
            );

            const { error } = await supabase
                .from(table)
                .upsert(batch, {
                    onConflict: 'id'
                });

            if (error) {
                throw new Error(
                    `Restore failed for "${table}" after ` +
                    `${completed}/${safeRows.length} rows: ${error.message}. ` +
                    'Some earlier batches or tables may already have been restored.'
                );
            }

            completed += batch.length;
        }

        const skipped = rows.length - safeRows.length;

        totalRestored += completed;
        totalSkipped += skipped;

        console.log(
            `  ${table}: ${completed}/${rows.length} rows restored` +
            (
                skipped > 0
                    ? `; ${skipped} Admin account row(s) protected`
                    : ''
            )
        );
    }

    console.log('');
    console.log('Restore completed.');
    console.log(`Total rows restored: ${totalRestored}`);
    console.log(`Total Admin rows protected: ${totalSkipped}`);
    console.log(
        'No rows absent from the backup were deleted by this script.'
    );
}

restore().catch(error => {
    console.error('');
    console.error('Restore failed:', error.message);
    process.exitCode = 1;
});
