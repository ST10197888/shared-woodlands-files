
package com.woodlands.mobile

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalDb(context: Context) : SQLiteOpenHelper(
    context,
    "woodlands_mobile.db",
    null,
    8
) {

    /** Used by Queries.kt to stamp outbox operations with the signed-in account. */
    internal val appContext: Context = context.applicationContext

    companion object {
        @Volatile
        private var instance: LocalDb? = null

        fun shared(context: Context): LocalDb =
            instance ?: synchronized(this) {
                instance ?: LocalDb(context.applicationContext).also {
                    instance = it
                }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    /**
     * Existing databases are upgraded additively in onOpen().
     * Do not drop tables or delete existing offline data here.
     */
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Additive schema checks are handled by onOpen().
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)

        if (db.isReadOnly) return

        val branchColumns = mutableSetOf<String>()

        db.rawQuery("PRAGMA table_info(branches)", null).use { cursor ->
            while (cursor.moveToNext()) {
                branchColumns += cursor.getString(1)
            }
        }

        if ("image" !in branchColumns) {
            db.execSQL("ALTER TABLE branches ADD COLUMN image TEXT DEFAULT ''")
        }

        if ("address" !in branchColumns) {
            db.execSQL("ALTER TABLE branches ADD COLUMN address TEXT DEFAULT ''")
        }

        // Public staff directory; available while logged out.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts(
                id TEXT PRIMARY KEY,
                full_name TEXT,
                email TEXT,
                phone TEXT,
                role TEXT,
                branch TEXT,
                active INTEGER DEFAULT 1,
                created_at INTEGER
            )
            """.trimIndent()
        )

        // Services table.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS services(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                name TEXT,
                description TEXT,
                image TEXT,
                active INTEGER DEFAULT 1
            )
            """.trimIndent()
        )

        // In-app notifications.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                owner_id TEXT,
                title TEXT,
                message TEXT,
                kind TEXT,
                is_read INTEGER DEFAULT 0,
                created_at INTEGER
            )
            """.trimIndent()
        )

        // Upgrade existing notification tables without deleting existing data.
        val notificationColumns = mutableSetOf<String>()

        db.rawQuery("PRAGMA table_info(notifications)", null).use { cursor ->
            while (cursor.moveToNext()) {
                notificationColumns += cursor.getString(1)
            }
        }

        if ("remote_id" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN remote_id TEXT")
        }

        if ("owner_id" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN owner_id TEXT")
        }

        if ("title" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN title TEXT")
        }

        if ("message" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN message TEXT")
        }

        if ("kind" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN kind TEXT")
        }

        if ("is_read" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN is_read INTEGER DEFAULT 0")
        }

        if ("created_at" !in notificationColumns) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN created_at INTEGER")
        }

        // Add columns to the existing outbox without deleting queued operations.
        val outboxColumns = mutableSetOf<String>()

        db.rawQuery("PRAGMA table_info(outbox)", null).use { cursor ->
            while (cursor.moveToNext()) {
                outboxColumns += cursor.getString(1)
            }
        }

        if ("owner_id" !in outboxColumns) {
            db.execSQL("ALTER TABLE outbox ADD COLUMN owner_id TEXT")
        }

        if ("failed" !in outboxColumns) {
            db.execSQL(
                "ALTER TABLE outbox ADD COLUMN failed INTEGER DEFAULT 0"
            )
        }

        if ("last_error" !in outboxColumns) {
            db.execSQL("ALTER TABLE outbox ADD COLUMN last_error TEXT")
        }
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                category TEXT,
                title TEXT,
                tagline TEXT,
                description TEXT,
                image TEXT,
                gallery TEXT,
                features TEXT,
                finishes TEXT,
                lead_time TEXT,
                tag TEXT,
                price TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE testimonials(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                name TEXT,
                role TEXT,
                location TEXT,
                rating INTEGER,
                review TEXT,
                project TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE faqs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                category TEXT,
                question TEXT,
                answer TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE branches(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                name TEXT,
                region TEXT,
                phone TEXT,
                hours TEXT,
                notes TEXT,
                image TEXT DEFAULT '',
                address TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE quote_requests(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                quote_code TEXT,
                first_name TEXT,
                last_name TEXT,
                email TEXT,
                phone TEXT,
                branch TEXT,
                service TEXT,
                message TEXT,
                product_id TEXT,
                created_at INTEGER,
                status TEXT DEFAULT 'Pending',
                value TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE contact_submissions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name TEXT,
                last_name TEXT,
                email TEXT,
                phone TEXT,
                branch TEXT,
                service TEXT,
                message TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE users(
                id TEXT PRIMARY KEY,
                full_name TEXT,
                email TEXT,
                phone TEXT,
                role TEXT,
                branch TEXT,
                active INTEGER DEFAULT 1,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE outbox(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT,
                method TEXT,
                path TEXT,
                body TEXT,
                local_ref TEXT,
                attempts INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
