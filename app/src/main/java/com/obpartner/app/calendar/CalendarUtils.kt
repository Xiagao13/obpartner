package com.obpartner.app.calendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日历计算与格式化工具类
 * Calendar Calculation and Formatting Utilities
 */
object CalendarUtils {

    private val isoFormats = arrayOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    )

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val defaultDateStringFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
    private val extraFormats = arrayOf(
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    )

    /**
     * 解析各类日期时间对象或字符串 (支持 Date、时间戳、ISO 8601 与多种文本格式)
     * Parse various date/time objects or strings (Supports Date, timestamps, ISO 8601, etc.)
     */
    fun parseDate(raw: Any?): Date? {
        if (raw == null) return null
        if (raw is Date) return raw
        if (raw is Number) return Date(raw.toLong())

        val dateStr = raw.toString().trim().replace("\"", "").replace("'", "")
        if (dateStr.isBlank()) return null

        for (format in isoFormats) {
            try {
                return format.parse(dateStr)
            } catch (_: Exception) {
            }
        }
        for (format in extraFormats) {
            try {
                return format.parse(dateStr)
            } catch (_: Exception) {
            }
        }
        try {
            return defaultDateStringFormat.parse(dateStr)
        } catch (_: Exception) {
        }
        return null
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
