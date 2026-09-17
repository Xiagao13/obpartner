package com.obpartner.app.model

/**
 * 日程事件模型 (与 Freepace CalendarEvent 规范严格对齐)
 * Calendar Event Model (Faithfully aligned with Freepace CalendarEvent specification)
 *
 * @param id 唯一标识符 / Unique identifier
 * @param title 标题（对应文件名或 Frontmatter title） / Title (basename or fm title)
 * @param path Markdown 文件完整路径 / Full Markdown file path
 * @param start 开始时间戳（毫秒） / Start timestamp (millis)
 * @param end 结束时间戳（毫秒） / End timestamp (millis)
 * @param isAllDay 是否为全天事件（持续时间 >= 20小时或跨天） / Whether it's an all-day event
 * @param isCrossDay 是否为跨天事件 / Whether it spans across multiple days
 * @param colorValue 分类颜色标识 / Category color value
 * @param displayText 自定义展示文本 / Custom display text
 * @param extraData 额外的 Frontmatter 键值对 / Additional Frontmatter properties
 * @param laneIndex 泳道索引（用于重叠排布） / Lane index for overlapping layout
 * @param overlapIndex 重叠分栏索引 / Column index within overlapping group
 * @param overlapCount 重叠总分栏数 / Total overlapping columns count
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val path: String,
    val start: Long,
    val end: Long,
    val isAllDay: Boolean = false,
    val isCrossDay: Boolean = false,
    val colorValue: String = "default",
    val displayText: String = "",
    val extraData: Map<String, Any> = emptyMap(),
    val laneIndex: Int = 0,
    val overlapIndex: Int = 0,
    val overlapCount: Int = 1
)
