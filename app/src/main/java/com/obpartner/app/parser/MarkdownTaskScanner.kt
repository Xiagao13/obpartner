package com.obpartner.app.parser

import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.model.AppSettings
import com.obpartner.app.model.HabitItem
import com.obpartner.app.model.TaskItem
import java.util.Calendar

/**
 * 任务与习惯提取器 (与 Freepace TaskBlock 完全对齐)
 * Task and Habit Scanner (Faithfully aligned with Freepace TaskBlock)
 */
object MarkdownTaskScanner {

    fun parseTask(
        filePath: String,
        fileName: String,
        frontmatter: Map<String, Any>,
        settings: AppSettings,
        todayMidnight: Long
    ): TaskItem? {
        val type = frontmatter["type"]?.toString() ?: return null
        val allowedTypes = listOf("Project", "Event", "Habit", "Idea", "HabitRecord", "Task")
        if (!allowedTypes.any { it.equals(type, ignoreCase = true) }) return null

        val name = fileName.removeSuffix(".md")
        val status = frontmatter["status"]?.toString() ?: "Todo"
        val priority = frontmatter["priority"]?.toString() ?: "P2"

        // 提取父任务 WikiLink: [[ParentName|Alias]] -> ParentName
        val parentRaw = frontmatter["parent"]?.toString()
        val parentId = parentRaw?.replace("[", "")?.replace("]", "")?.split("|")?.firstOrNull()?.trim()

        // 提取起止时间
        val startRaw = frontmatter[settings.taskStartKey]
            ?: frontmatter["start_date"]
            ?: frontmatter["date"]
        val endRaw = frontmatter[settings.taskEndKey]
            ?: frontmatter["due_date"]
            ?: frontmatter["end_date"]
            ?: frontmatter["date"]

        val explicitStart = CalendarUtils.parseDate(startRaw)?.time
        val explicitEnd = CalendarUtils.parseDate(endRaw)?.time

        val isOverdue = !status.equals("Done", ignoreCase = true) &&
                explicitEnd != null && explicitEnd < todayMidnight

        val workLogList = when (val wl = frontmatter["work_log"]) {
            is List<*> -> wl.mapNotNull { it?.toString() }
            is String -> wl.split(",").map { it.trim() }
            else -> emptyList()
        }

        val tagsList = when (val t = frontmatter["tags"] ?: frontmatter["tag"]) {
            is List<*> -> t.mapNotNull { it?.toString() }
            is String -> t.split(",").map { it.trim() }
            else -> emptyList()
        }

        val manualProgress = frontmatter["progress"]?.toString()?.toIntOrNull() ?: 0

        return TaskItem(
            id = filePath,
            name = name,
            path = filePath,
            type = type,
            parentId = parentId,
            status = status,
            priority = priority,
            manualProgress = manualProgress,
            explicitStart = explicitStart,
            explicitEnd = explicitEnd,
            isOverdue = isOverdue,
            workLog = workLogList,
            tags = tagsList
        )
    }

    fun parseHabit(
        filePath: String,
        fileName: String,
        frontmatter: Map<String, Any>,
        todayStr: String
    ): HabitItem? {
        val type = frontmatter["type"]?.toString() ?: return null
        if (!type.equals("Habit", ignoreCase = true)) return null

        val name = fileName.removeSuffix(".md")
        val workLogList = when (val wl = frontmatter["work_log"]) {
            is List<*> -> wl.mapNotNull { it?.toString() }
            is String -> wl.split(",").map { it.trim() }
            else -> emptyList()
        }

        val isDoneToday = workLogList.contains(todayStr)
        val streak = calculateStreak(workLogList, todayStr)

        return HabitItem(
            id = filePath,
            name = name,
            path = filePath,
            isDoneToday = isDoneToday,
            streak = streak,
            history = workLogList
        )
    }

    private fun calculateStreak(workLog: List<String>, todayStr: String): Int {
        if (workLog.isEmpty()) return 0
        val sortedDates = workLog.distinct().sortedDescending()
        var streak = 0
        val cal = Calendar.getInstance()

        // 如果今天已经打卡，从今天开始算；否则如果昨天打了卡，从昨天算
        if (sortedDates.contains(todayStr)) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = CalendarUtils.formatDate(cal.time)
            if (sortedDates.contains(yesterdayStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                return 0
            }
        }

        while (true) {
            val dateStr = CalendarUtils.formatDate(cal.time)
            if (sortedDates.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }
}
