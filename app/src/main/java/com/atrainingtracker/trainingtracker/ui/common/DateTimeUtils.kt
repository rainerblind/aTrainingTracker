package com.atrainingtracker.trainingtracker.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
}
