package com.valoser.futacha.compat

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Downgrade policy for the compatibility database.
 *
 * Every migration so far only adds tables or columns (new columns carry
 * defaults), so an older build can keep using a newer database as long as
 * every table and column it reads is still present and no newer column must be
 * filled on insert. Such a database is kept as is and the newer version is
 * recorded, so reinstalling the newer build skips the migrations that were
 * already applied instead of failing on, e.g., a duplicate ADD COLUMN.
 * Anything else is set aside (renamed, never deleted) and this build starts
 * with a fresh database instead of failing to launch.
 */
internal const val COMPAT_SCHEMA_APPLIED_VERSION_METADATA_KEY = "schema.appliedVersion"

internal data class CompatSchemaColumn(
    val name: String,
    val notNull: Boolean,
    val hasDefault: Boolean,
    val primaryKey: Boolean
)

internal class IncompatibleNewerCompatibilityDatabaseException(
    val databaseVersion: Int,
    reasons: List<String>
) : IllegalStateException(
    "Compatibility DB version $databaseVersion is not readable by this build: ${reasons.take(5).joinToString()}"
)

/** Lists why a newer schema cannot be used by this build; empty when it can. */
internal fun compatDowngradeIncompatibilities(
    expected: Map<String, List<CompatSchemaColumn>>,
    actual: Map<String, List<CompatSchemaColumn>>
): List<String> {
    val actualTables = actual.mapKeys { it.key.lowercase() }
    return buildList {
        expected.forEach { (table, expectedColumns) ->
            val actualColumns = actualTables[table.lowercase()]
            if (actualColumns == null) {
                add("missing table $table")
                return@forEach
            }
            val actualNames = actualColumns.mapTo(mutableSetOf()) { it.name.lowercase() }
            val expectedNames = expectedColumns.mapTo(mutableSetOf()) { it.name.lowercase() }
            expectedColumns.filter { it.name.lowercase() !in actualNames }
                .forEach { add("missing column $table.${it.name}") }
            // Inserts from this build leave unknown columns empty.
            actualColumns.filter {
                it.name.lowercase() !in expectedNames && it.notNull && !it.hasDefault && !it.primaryKey
            }.forEach { add("required column $table.${it.name}") }
        }
    }
}

internal fun SQLiteDatabase.compatSchemaColumns(): Map<String, List<CompatSchemaColumn>> {
    val tables = rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata'",
        null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    return tables.associateWith { table ->
        rawQuery("PRAGMA table_info(\"${table.replace("\"", "\"\"")}\")", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            val notNull = cursor.getColumnIndexOrThrow("notnull")
            val default = cursor.getColumnIndexOrThrow("dflt_value")
            val primaryKey = cursor.getColumnIndexOrThrow("pk")
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CompatSchemaColumn(
                            name = cursor.getString(name),
                            notNull = cursor.getInt(notNull) != 0,
                            hasDefault = !cursor.isNull(default),
                            primaryKey = cursor.getInt(primaryKey) != 0
                        )
                    )
                }
            }
        }
    }
}

/** Keeps a compatible newer database, or throws so the caller can set it aside. */
internal fun SQLiteDatabase.acceptCompatibilityDatabaseDowngrade(
    oldVersion: Int,
    createStatements: List<String>
) {
    val expected = SQLiteDatabase.create(null).use { schema ->
        createStatements.forEach(schema::execSQL)
        schema.compatSchemaColumns()
    }
    val reasons = compatDowngradeIncompatibilities(expected, compatSchemaColumns())
    if (reasons.isNotEmpty()) throw IncompatibleNewerCompatibilityDatabaseException(oldVersion, reasons)
    val recorded = compatAppliedSchemaVersion() ?: 0
    if (oldVersion > recorded) {
        val values = ContentValues().apply {
            put("key", COMPAT_SCHEMA_APPLIED_VERSION_METADATA_KEY)
            put("value", oldVersion.toString())
        }
        insertWithOnConflict("compat_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

/** The newest schema version whose migrations already ran on this file, if it was downgraded. */
internal fun SQLiteDatabase.compatAppliedSchemaVersion(): Int? {
    val hasMetadata = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='compat_metadata'", null
    ).use { it.moveToFirst() }
    if (!hasMetadata) return null
    return query(
        "compat_metadata", arrayOf("value"), "key=?", arrayOf(COMPAT_SCHEMA_APPLIED_VERSION_METADATA_KEY),
        null, null, null, "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() else null }
}

/** Clears the downgrade marker once the schema has caught up with it. */
internal fun SQLiteDatabase.clearCompatAppliedSchemaVersionUpTo(version: Int) {
    val recorded = compatAppliedSchemaVersion() ?: return
    if (recorded <= version) {
        delete("compat_metadata", "key=?", arrayOf(COMPAT_SCHEMA_APPLIED_VERSION_METADATA_KEY))
    }
}

/** Renames the database and its journal files so their data stays recoverable. */
internal fun setAsideIncompatibleCompatibilityDatabase(databaseFile: File, version: Int, nowMillis: Long): File {
    val target = File(databaseFile.parentFile, "${databaseFile.name}.newer-v$version-$nowMillis")
    listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
        val source = File(databaseFile.path + suffix)
        if (source.exists()) {
            val destination = File(target.path + suffix)
            check(source.renameTo(destination)) { "Could not set aside ${source.name}" }
        }
    }
    return target
}
