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
        val startRaw = frontmatter[settings.startTimeProp]
            ?: frontmatter["event_date"]
            ?: frontmatter["start_date"]
            ?: frontmatter["date"]
            ?: return null

        val startDate = CalendarUtils.parseDate(startRaw) ?: return null

        val endRaw = frontmatter[settings.endTimeProp]
            ?: frontmatter["end_date"]

        val endDate = if (endRaw != null) {
            CalendarUtils.parseDate(endRaw) ?: Date(startDate.time + 3600000L)
        } else {
            Date(startDate.time + 3600000L) // 默认 1 小时 / Default 1 hour
        }

        val isSameDay = CalendarUtils.isSameDay(startDate, endDate)
        val isCrossDay = !isSameDay
        val durationHours = (endDate.time - startDate.time).toDouble() / (1000 * 60 * 60)
        val isAllDay = durationHours >= 20 || isCrossDay

        val colorVal = frontmatter[settings.colorGroupProp]?.toString() ?: "default"
        val title = frontmatter["title"]?.toString()?.takeIf { it.isNotBlank() }
            ?: fileName.removeSuffix(".md")

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
