package com.thehhr.form.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/*
 * Helpers that reproduce the web app's JavaScript formatting, so vault files written by the
 * native app are byte-identical to the ones 3.x wrote (and both can keep reading each other).
 */

/** JS `String(n)`: `75.0` -> "75", `2.5` -> "2.5". */
fun jsNum(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value == 0.0) return "0"
    if (value == floor(value) && abs(value) < 1e15) return value.toLong().toString()
    // Java switches to "5.0E-4" below 1e-3; JS stays plain down to 1e-7.
    if (abs(value) < 1e-3) return java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    return value.toString()
}

fun jsNum(value: Int): String = value.toString()

/** JS `Math.round` (rounds .5 up, toward +infinity). */
fun jsRound(value: Double): Long = floor(value + 0.5).toLong()

fun round1(value: Double): Double = jsRound(value * 10) / 10.0
fun round2(value: Double): Double = jsRound(value * 100) / 100.0
fun round4(value: Double): Double = jsRound(value * 10000) / 10000.0

/** JS `Number(s)` for the strings vault files contain: blank -> 0, junk -> NaN. */
fun jsNumber(raw: String?): Double {
    if (raw == null) return Double.NaN
    val t = raw.trim()
    if (t.isEmpty()) return 0.0
    return t.toDoubleOrNull()?.takeIf { !t.endsWith("d", true) && !t.endsWith("f", true) } ?: Double.NaN
}

/** The web app's `clamp`: `Number(v) || min`, then bounded. */
fun jsClamp(value: Double, min: Double, max: Double): Double {
    val n = if (value.isNaN() || value == 0.0) min else value
    return minOf(max, maxOf(min, n))
}

fun jsClamp(raw: String?, min: Double, max: Double): Double = jsClamp(jsNumber(raw), min, max)

/** The web app's `vClampNum`: blank/null -> fallback, non-finite -> fallback. */
fun vClamp(raw: String?, min: Double, max: Double, fallback: Double): Double {
    if (raw == null || raw == "") return fallback
    val n = jsNumber(raw)
    if (n.isNaN() || n.isInfinite()) return fallback
    return minOf(max, maxOf(min, n))
}

fun vClampOrNull(raw: String?, min: Double, max: Double): Double? {
    if (raw == null || raw == "") return null
    val n = jsNumber(raw)
    if (n.isNaN() || n.isInfinite()) return null
    return minOf(max, maxOf(min, n))
}

private fun isWordChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

/** JS `value.replace(/\b\w/g, c => c.toUpperCase())`. */
fun jsTitle(value: String): String {
    val sb = StringBuilder(value.length)
    for (i in value.indices) {
        val c = value[i]
        val start = isWordChar(c) && (i == 0 || !isWordChar(value[i - 1]))
        sb.append(if (start && c in 'a'..'z') c.uppercaseChar() else c)
    }
    return sb.toString()
}

/** JS `JSON.stringify(string)`. */
fun jsonQuote(value: String): String {
    val sb = StringBuilder("\"")
    for (c in value) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
    }
    return sb.append('"').toString()
}

/** Vault names must never start with '#' or span lines (would break parsing). */
fun sanitizeVaultName(value: String?): String =
    (value ?: "").replace(Regex("\\s+"), " ").replace(Regex("^#+\\s*"), "").trim()

fun stripImportDash(line: String): String = if (line.startsWith("-")) line.substring(1).trim() else line

fun isValidDate(value: String?): Boolean {
    if (value == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return false
    return try { LocalDate.parse(value); true } catch (_: DateTimeParseException) { false }
}

fun today(): String = LocalDate.now().toString()

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-01-05" -> "Mon, Jan 5, 2026". */
fun vaultDateHeading(dateKey: String): String = try {
    val d = LocalDate.parse(dateKey)
    "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}, ${d.year}"
} catch (_: DateTimeParseException) { dateKey }

private val DATE_HEADING = Regex("^(?:Sun|Mon|Tue|Wed|Thu|Fri|Sat),?\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2}),?\\s+(\\d{4})$", RegexOption.IGNORE_CASE)

fun parseVaultDateHeading(text: String): String? {
    val m = DATE_HEADING.find(text.trim()) ?: return null
    val month = MONTHS.indexOfFirst { it.equals(m.groupValues[1], true) }
    if (month < 0) return null
    return try {
        LocalDate.of(m.groupValues[3].toInt(), month + 1, m.groupValues[2].toInt()).toString()
    } catch (_: Exception) { null }
}

fun Double.roundToIntSafe(): Int = jsRound(this).toInt()
fun Double.toLongSafe(): Long = if (isNaN()) 0 else roundToLong()
