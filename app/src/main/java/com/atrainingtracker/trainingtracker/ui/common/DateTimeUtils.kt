package com.atrainingtracker.trainingtracker.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Shared utility functions for locale-aware date and time formatting across UI components.
 * Fulfills REQ-UI-475.3 and supports cross-component synergy (Rule 36).
 */
object DateTimeUtils {

    /**
     * Formats an ISO-8601 date string (e.g., "2024-05-01" or "2024-05-01T14:30:00") into a
     * localized medium date string (e.g., "01.05.2024" for de_DE, "May 1, 2024" for en_US).
     *
     * Gracefully returns an empty string if [dateString] is null or blank,
     * or returns [dateString] unchanged if parsing fails, ensuring fail-safe execution.
     *
     * @param dateString ISO-8601 formatted date (e.g., "yyyy-MM-dd")
     * @param locale Target locale for formatting, defaulting to [Locale.getDefault]
     * @return Formatted localized date string or fallback
     */
    @JvmStatic
    fun formatIsoDate(dateString: String?, locale: Locale = Locale.getDefault()): String {
        if (dateString.isNullOrBlank()) {
            return ""
        }
        return try {
            val datePart = dateString.trim().substringBefore('T').substringBefore(' ')
            val parsedDate = LocalDate.parse(datePart)
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            parsedDate.format(formatter)
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * Formats start and end timestamps into a localized weekly date range string (e.g. "2. Nov. - 8. Nov."
     * in German, "Nov 2 - Nov 8" in US English).
     * Fulfills REQ-PER-028.1 and preserves Monday-Sunday temporal aggregation bounds.
     *
     * @param startS Start timestamp in seconds
     * @param endS End timestamp in seconds
     * @param locale Target locale for formatting, defaulting to [Locale.getDefault]
     * @param zoneId Target time zone, defaulting to [ZoneId.systemDefault]
     * @return Formatted localized date range string, or empty string if invalid
     */
    @JvmStatic
    @JvmOverloads
    fun formatPeriodDateRange(
        startS: Long,
        endS: Long,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (startS <= 0 || endS <= 0) return ""
        return try {
            val startDt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(startS), zoneId)
            val endDt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(endS), zoneId)

            val pattern = resolveDateRangePattern(locale)
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            "${startDt.format(formatter)} - ${endDt.format(formatter)}"
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Resolves the locale-appropriate date pattern skeleton for month and day.
     * German: "d. MMM" (e.g. "2. Nov.")
     * Japanese: "M月d日" (e.g. "11月2日")
     * Romance & Slavic day-first languages: "d MMM" (e.g. "2 nov.")
     * US English & month-first languages: "MMM d" (e.g. "Nov 2" / "Nov. 2")
     */
    @JvmStatic
    fun resolveDateRangePattern(locale: Locale = Locale.getDefault()): String {
        return when (locale.language.lowercase(Locale.ROOT)) {
            "de" -> "d. MMM"
            "ja" -> "M月d日"
            "fr", "es", "it", "nl", "pl", "pt" -> "d MMM"
            else -> {
                try {
                    val shortPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                        FormatStyle.SHORT,
                        null,
                        IsoChronology.INSTANCE,
                        locale
                    )
                    val dIdx = shortPattern.indexOf('d').let { if (it >= 0) it else shortPattern.indexOf('D') }
                    val mIdx = shortPattern.indexOf('M').let { if (it >= 0) it else shortPattern.indexOf('L') }
                    if (dIdx in 0..<mIdx) {
                        if (shortPattern.contains("d.")) "d. MMM" else "d MMM"
                    } else {
                        "MMM d"
                    }
                } catch (_: Exception) {
                    "MMM d"
                }
            }
        }
    }
}
