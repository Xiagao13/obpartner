package com.obpartner.app.parser

import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.model.AppSettings
import com.obpartner.app.model.CalendarEvent
import java.util.Date

/**
 * 日程事件提取器 (与 Freepace CalendarBlock 完全对齐)
 * Calendar Event Scanner (Faithfully aligned with Freepace CalendarBlock)
 */
object MarkdownEventScanner {

    fun parseEvent(
        filePath: String,
        fileName: String,
        frontmatter: Map<String, Any>,
        settings: AppSettings
    ): CalendarEvent? {
        val startStr = frontmatter[settings.startTimeProp]?.toString()
            ?: frontmatter["event_date"]?.toString()
            ?: frontmatter["start_date"]?.toString()
            ?: frontmatter["date"]?.toString()
            ?: return null

        val startDate = CalendarUtils.parseDate(startStr) ?: return null

        val endStr = frontmatter[settings.endTimeProp]?.toString()
            ?: frontmatter["end_date"]?.toString()

        val endDate = if (!endStr.isNullOrBlank()) {
            CalendarUtils.parseDate(endStr) ?: Date(startDate.time + 3600000L)
        } else {
            Date(startDate.time + 3600000L) // 默认 1 小时 / Default 1 hour
        }

        val isSameDay = CalendarUtils.isSameDay(startDate, endDate)
        val isCrossDay = !isSameDay
        val durationHours = (endDate.time - startDate.time).toDouble() / (1000 * 60 * 60)
        val isAllDay = durationHours >= 20 || isCrossDay

        val colorVal = frontmatter[settings.colorGroupProp]?.toString() ?: "default"
        val title = fileName.removeSuffix(".md")

        return CalendarEvent(
            id = filePath,
            title = title,
            path = filePath,
            start = startDate.time,
            end = endDate.time,
            isAllDay = isAllDay,
            isCrossDay = isCrossDay,
            colorValue = colorVal,
            displayText = title,
            extraData = frontmatter
        )
    }
}
