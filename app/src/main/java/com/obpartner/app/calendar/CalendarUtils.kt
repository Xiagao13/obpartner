package com.obpartner.app.calendar

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 日历计算与格式化工具类
 * Calendar Calculation and Formatting Utilities
 */
object CalendarUtils {

    private val dateRegex = Pattern.compile("(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2})")
    private val timeRegex = Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?)")

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val defaultDateStringFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
    private val extraFormats = arrayOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    )

    /**
     * 解析各类日期时间对象或字符串 (优先通过 java.time 兼容各种 ISO 8601 时区及毫秒格式)
     * Parse various date/time objects or strings (Supports java.time ISO 8601 with timezone, Date, timestamps, etc.)
     */
    fun parseDate(raw: Any?): Date? {
        if (raw == null) return null
        if (raw is Date) return raw
        if (raw is Number) return Date(raw.toLong())

        val dateStr = raw.toString().trim().replace("\"", "").replace("'", "")
        if (dateStr.isBlank()) return null

        // 1. 尝试使用 java.time 进行精准 ISO-8601 解析（支持时区 +08:00、Z、毫秒等）
        try {
            val odt = OffsetDateTime.parse(dateStr)
            return Date(odt.toInstant().toEpochMilli())
        } catch (_: Exception) {}

        try {
            val zdt = ZonedDateTime.parse(dateStr)
            return Date(zdt.toInstant().toEpochMilli())
        } catch (_: Exception) {}

        try {
            val instant = Instant.parse(dateStr)
            return Date(instant.toEpochMilli())
        } catch (_: Exception) {}

        try {
            val ldt = LocalDateTime.parse(dateStr)
            return Date(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        } catch (_: Exception) {}

        try {
            val ld = LocalDate.parse(dateStr)
            return Date(ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        } catch (_: Exception) {}

        // 2. 尝试多种 SimpleDateFormat 格式
        for (format in extraFormats) {
            try {
                return format.parse(dateStr)
            } catch (_: Exception) {}
        }

        // 3. 尝试默认 Date.toString 格式
        try {
            return defaultDateStringFormat.parse(dateStr)
        } catch (_: Exception) {}

        return null
    }

    /**
     * 从文本（如文件名 "2026-05-01 腾讯会议.md" 或笔记标题）中提取日期字符串
     * Extract date string from text (e.g. filename or title)
     */
    fun extractDateFromText(text: String): String? {
        val matcher = dateRegex.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.replace('/', '-')?.replace('.', '-')
        }
        return null
    }

    /**
     * 将纯时间字符串（如 "20:45" 或 "20:45:00"）与基准日期组合
     * Combine pure time string (e.g. "20:45") with a base date
     */
    fun combineDateAndTime(baseDate: Date, timeStr: String): Date? {
        val trimmed = timeStr.trim().replace("\"", "").replace("'", "")
        val matcher = timeRegex.matcher(trimmed)
        if (!matcher.find()) return null

        val matchedTime = matcher.group(1) ?: return null
        val parts = matchedTime.split(":")
        if (parts.size < 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val second = if (parts.size >= 3) parts[2].toIntOrNull() ?: 0 else 0

        val cal = Calendar.getInstance().apply {
            time = baseDate
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.time
    }

    /**
     * 判断字符串是否包含时间段（如 "20:45 - 22:45" 或 "20:45~22:45"）并拆分
     * Check if string contains a time range and split into start and end time
     */
    fun splitTimeRange(raw: String): Pair<String, String?> {
        val trimmed = raw.trim().replace("\"", "").replace("'", "")
        val delimiters = arrayOf(" - ", "-", " ~ ", "~", " – ", "—")
        for (delim in delimiters) {
            if (trimmed.contains(delim)) {
                val parts = trimmed.split(delim, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return Pair(parts[0].trim(), parts[1].trim())
                }
            }
        }
        return Pair(trimmed, null)
    }


    fun formatDate(date: Date): String = dayFormat.format(date)
    fun formatTime(date: Date): String = timeFormat.format(date)

    fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 获取给定日期所在周的所有 7 天
     * Get all 7 dates of the week containing the given date
     *
     * @param date 参考日期 / Reference date
     * @param weekStartsOn 每周起始日 ("monday" 或 "sunday") / First day of the week
     */
    fun getWeekDates(date: Date, weekStartsOn: String = "monday"): List<Date> {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startDayOfWeek = if (weekStartsOn.equals("sunday", ignoreCase = true)) {
            Calendar.SUNDAY
        } else {
            Calendar.MONDAY
        }

        while (cal.get(Calendar.DAY_OF_WEEK) != startDayOfWeek) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        val weekDates = mutableListOf<Date>()
        for (i in 0 until 7) {
            weekDates.add(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return weekDates
    }

    /**
     * 月视图单元格数据结构
     * Month View Cell Data
     */
    data class MonthCell(
        val date: Date,
        val isCurrentMonth: Boolean,
        val isToday: Boolean
    )

    /**
     * 生成月视图 7x6 (42个单元格) 的完整网格 (严格对齐 Freepace 偏移算法)
     * Generate month grid cells (Faithfully aligned with Freepace offset logic)
     */
    fun generateMonthCells(
        currentMonthDate: Date,
        weekStartsOn: String = "monday"
    ): List<MonthCell> {
        val cal = Calendar.getInstance().apply {
            time = currentMonthDate
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetMonth = cal.get(Calendar.MONTH)
        val today = Date()

        val weekStartNum = if (weekStartsOn.equals("sunday", ignoreCase = true)) 0 else 1
        // JavaScript getDay(): Sunday is 0, Monday is 1
        val jsDayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }

        val startOffset = (jsDayOfWeek - weekStartNum + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -startOffset)

        val cells = mutableListOf<MonthCell>()
        for (i in 0 until 42) {
            val cellDate = cal.time
            val isCurrentMonth = cal.get(Calendar.MONTH) == targetMonth
            cells.add(
                MonthCell(
                    date = cellDate,
                    isCurrentMonth = isCurrentMonth,
                    isToday = isSameDay(cellDate, today)
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cells
    }
}
