package com.obpartner.app.model

/**
 * 习惯打卡模型 (与 Freepace Habit Tracker 规范严格对齐)
 * Habit Tracker Model (Faithfully aligned with Freepace Habit Tracker specification)
 *
 * @param id 唯一标识符 / Unique identifier
 * @param name 习惯名称 / Habit name
 * @param path 关联 Markdown 文件路径 / Associated file path
 * @param isDoneToday 今日是否已打卡 / Whether it has been checked in today
 * @param streak 连续打卡天数 / Current continuous streak count
 * @param history 历史打卡日期列表 (格式: YYYY-MM-DD) / History of check-in dates
 */
data class HabitItem(
    val id: String,
    val name: String,
    val path: String,
    val isDoneToday: Boolean = false,
    val streak: Int = 0,
    val history: List<String> = emptyList()
)
