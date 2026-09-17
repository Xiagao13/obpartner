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
        // 1. 获取日期基准上下文 (优先从 date 字段，其次从文件名如 2026-05-01.md 提取)
        val dateContextStr = frontmatter["date"]?.toString()
            ?: frontmatter["event_date"]?.toString()
            ?: frontmatter["start_date"]?.toString()
            ?: frontmatter["day"]?.toString()
            ?: CalendarUtils.extractDateFromText(fileName)

        val baseDate = dateContextStr?.let { CalendarUtils.parseDate(it) }

        // 2. 提取开始时间原始值
        val startRaw = frontmatter[settings.startTimeProp]
            ?: frontmatter["start_time"]
            ?: frontmatter["startTime"]
            ?: frontmatter["start"]
            ?: frontmatter["time"]
            ?: frontmatter["event_date"]
            ?: frontmatter["start_date"]
            ?: frontmatter["date"]

        var startDate: Date? = null
        var rangeEndStr: String? = null

        if (startRaw != null) {
            val (startPart, endPart) = CalendarUtils.splitTimeRange(startRaw.toString())
            rangeEndStr = endPart

            // 尝试直接解析完整日期时间 (如 2026-05-01T20:45:00)
            startDate = CalendarUtils.parseDate(startPart)

            // 若直接解析失败，说明可能是纯时间 (如 "20:45" 或 "20:45:00")，与基准日期组合
            if (startDate == null) {
                val refDate = baseDate ?: Date()
                startDate = CalendarUtils.combineDateAndTime(refDate, startPart)
            }
        } else if (baseDate != null && isLikelyEvent(frontmatter)) {
            // 没有显式 start_time，但有 date 且声明了 type: Event，视作全天事件
            startDate = baseDate
        }

        if (startDate == null) return null

        // 3. 提取并计算结束时间
        var endDate: Date? = null

        if (!rangeEndStr.isNullOrBlank()) {
            endDate = CalendarUtils.parseDate(rangeEndStr)
                ?: CalendarUtils.combineDateAndTime(startDate, rangeEndStr)
        }

        if (endDate == null) {
            val endRaw = frontmatter[settings.endTimeProp]
                ?: frontmatter["end_time"]
                ?: frontmatter["endTime"]
                ?: frontmatter["end"]
                ?: frontmatter["end_date"]

            if (endRaw != null) {
                endDate = CalendarUtils.parseDate(endRaw)
                    ?: CalendarUtils.combineDateAndTime(startDate, endRaw.toString())
            }
        }

        // 若仍无结束时间，检查是否设置了课时/时长属性 (如 计费课时: "2" 或 duration: 1.5)
        if (endDate == null) {
            val durationRaw = frontmatter["计费课时"]
                ?: frontmatter["课时"]
                ?: frontmatter["duration"]
                ?: frontmatter["时长"]

            if (durationRaw != null) {
                val durationNum = durationRaw.toString().trim().replace("\"", "").toDoubleOrNull()
                if (durationNum != null && durationNum > 0) {
                    // 若数值 > 10 通常为分钟 (如 45, 60, 90, 120)，若 <= 10 通常为小时 (如 1, 1.5, 2)
                    val durationMillis = if (durationNum > 10) {
                        (durationNum * 60 * 1000L).toLong()
                    } else {
                        (durationNum * 3600 * 1000L).toLong()
                    }
                    endDate = Date(startDate.time + durationMillis)
                }
            }
        }

        // 默认保底时长：1 小时 (与 Freepace 一致: start.getTime() + 60 * 60 * 1000)
        if (endDate == null) {
            endDate = Date(startDate.time + 3600000L)
        }

        val isSameDay = CalendarUtils.isSameDay(startDate, endDate)
        val isCrossDay = !isSameDay
        val durationHours = (endDate.time - startDate.time).toDouble() / (1000 * 60 * 60)
        val isAllDay = durationHours >= 20 || isCrossDay

        // 4. 计算显示文本 displayText (与 Freepace 对齐：支持 displayProp 自定义，为空时 fallback 到 title)
        val baseTitle = frontmatter["title"]?.toString()?.takeIf { it.isNotBlank() }
            ?: fileName.removeSuffix(".md").removeSuffix(".markdown")

        val displayText = if (settings.displayProp.isNotBlank()) {
            frontmatter[settings.displayProp]?.toString()?.takeIf { it.isNotBlank() } ?: baseTitle
        } else {
            baseTitle
        }

        // 5. 计算色彩分类标识 colorValue (优先自定义 colorGroupProp，其次学员名、分类或类型)
        val colorVal = if (settings.colorGroupProp.isNotBlank() && frontmatter[settings.colorGroupProp]?.toString()?.isNotBlank() == true) {
            frontmatter[settings.colorGroupProp].toString()
        } else {
            frontmatter["student"]?.toString()?.takeIf { it.isNotBlank() }
                ?: frontmatter["category"]?.toString()?.takeIf { it.isNotBlank() }
                ?: frontmatter["type"]?.toString()?.takeIf { it.isNotBlank() }
                ?: "default"
        }

        // 6. 保留所有扩展属性 (含文件名)
        val extraData = frontmatter.toMutableMap().apply {
            put("file.name", baseTitle)
        }

        return CalendarEvent(
            id = filePath,
            title = baseTitle,
            path = filePath,
            start = startDate.time,
            end = endDate.time,
            isAllDay = isAllDay,
            isCrossDay = isCrossDay,
            colorValue = colorVal,
            displayText = displayText,
            extraData = extraData
        )
    }

    private fun isLikelyEvent(frontmatter: Map<String, Any>): Boolean {
        val type = frontmatter["type"]?.toString()
        if (type.equals("Event", ignoreCase = true) || type.equals("日程", ignoreCase = true)) {
            return true
        }
        val tags = frontmatter["tags"]?.toString() ?: ""
        return tags.contains("event", ignoreCase = true) || tags.contains("日程", ignoreCase = true)
    }
}

