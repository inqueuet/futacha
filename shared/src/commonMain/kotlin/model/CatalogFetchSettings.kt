package com.valoser.futacha.shared.model

const val DEFAULT_CATALOG_FETCH_COLUMNS = 5
/**
 * The compatibility APK uses the first cxyl component as the number of
 * catalog columns and keeps cy at 25.  That allows the legacy 50..3000
 * thread choices (up to 120 columns) while the modern UI still defaults to
 * five columns.
 */
const val MAX_CATALOG_FETCH_COLUMNS = 120
const val DEFAULT_CATALOG_FETCH_ROWS = 60
const val MIN_CATALOG_FETCH_ROWS = 20
const val MAX_CATALOG_FETCH_ROWS = 200
const val MAX_CATALOG_TITLE_LINES = 16
const val MAX_COMPAT_CATALOG_TITLE_LINES = 256
const val DEFAULT_CATALOG_TITLE_LINES = MAX_CATALOG_TITLE_LINES

val CATALOG_FETCH_ROW_OPTIONS = listOf(20, 40, 60, 100, 160, 200)

data class CatalogFetchSettings(
    val columns: Int = DEFAULT_CATALOG_FETCH_COLUMNS,
    val rows: Int = DEFAULT_CATALOG_FETCH_ROWS,
    val titleLines: Int = DEFAULT_CATALOG_TITLE_LINES,
    val showVisitedHistory: Boolean = true
) {
    val approximateThreadCount: Int
        get() = columns * rows

    fun normalized(): CatalogFetchSettings {
        return copy(
            columns = columns.coerceIn(1, MAX_CATALOG_FETCH_COLUMNS),
            rows = normalizeCatalogFetchRows(rows),
            titleLines = titleLines.coerceIn(1, maxOf(MAX_CATALOG_TITLE_LINES, MAX_COMPAT_CATALOG_TITLE_LINES))
        )
    }
}

fun normalizeCatalogFetchRows(rows: Int): Int {
    return rows.coerceIn(MIN_CATALOG_FETCH_ROWS, MAX_CATALOG_FETCH_ROWS)
}
