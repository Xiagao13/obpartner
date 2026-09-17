package com.obpartner.app.parser

import java.io.File

/**
 * Markdown 安全回写器 (精准局部更新 Frontmatter，绝不影响正文内容)
 * Markdown Safe Writer (Accurately updates Frontmatter in-place, preserving body intact)
 */
object MarkdownWriter {

    /**
     * 更新任务状态 (例如将 status 改为 Done 或 Todo)
     * Update task status in Frontmatter (e.g. status: Done)
     */
    fun updateTaskStatus(file: File, newStatus: String): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val content = file.readText()

        // 检查是否存在 Frontmatter / Check if Frontmatter exists
        if (!content.startsWith("---")) return false
        val secondDashIndex = content.indexOf("\n---", 3)
        if (secondDashIndex == -1) return false

        val frontmatterContent = content.substring(0, secondDashIndex + 4)
        val bodyContent = content.substring(secondDashIndex + 4)

        val updatedFrontmatter = if (Regex("(?m)^status:\\s*.*$").containsMatchIn(frontmatterContent)) {
            frontmatterContent.replace(Regex("(?m)^status:\\s*.*$"), "status: $newStatus")
        } else {
            // 如果原本没有 status 字段，插入在结束 --- 前
            frontmatterContent.replace("\n---", "\nstatus: $newStatus\n---")
        }

        file.writeText(updatedFrontmatter + bodyContent)
        return true
    }

    /**
     * 习惯打卡回写 (向 work_log 数组追加今日日期)
     * Check in habit (Append today's date to work_log in Frontmatter)
     */
    fun checkInHabit(file: File, todayStr: String): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val content = file.readText()

        if (!content.startsWith("---")) return false
        val secondDashIndex = content.indexOf("\n---", 3)
        if (secondDashIndex == -1) return false

        val frontmatterContent = content.substring(0, secondDashIndex + 4)
        val bodyContent = content.substring(secondDashIndex + 4)

        val workLogRegex = Regex("(?s)work_log:\\s*\\[(.*?)\\]")
        val match = workLogRegex.find(frontmatterContent)

        val updatedFrontmatter = if (match != null) {
            val existing = match.groupValues[1].trim()
            if (existing.contains(todayStr)) {
                // 已经打过卡 / Already checked in
                frontmatterContent
            } else {
                val newLogs = if (existing.isEmpty()) "\"$todayStr\"" else "$existing, \"$todayStr\""
                frontmatterContent.replace(match.value, "work_log: [$newLogs]")
            }
        } else {
            // 不存在 work_log 字段，在结尾添加
            frontmatterContent.replace("\n---", "\nwork_log: [\"$todayStr\"]\n---")
        }

        file.writeText(updatedFrontmatter + bodyContent)
        return true
    }
}
