package com.obpartner.app.model

/**
 * 任务模型 (与 Freepace TaskBlock 规范严格对齐)
 * Task Model (Faithfully aligned with Freepace TaskBlock specification)
 *
 * @param id 唯一标识符 / Unique identifier
 * @param name 任务名称（对应文件名） / Task name (file basename)
 * @param path Markdown 文件路径 / Full file path
 * @param type 类型 (Project, Event, Habit, Idea 等) / Type
 * @param parentId 父任务名称或 WikiLink 目标 / Parent task identifier
 * @param status 状态 (Todo, Doing, Done, Cancelled 等) / Status
 * @param priority 优先级 (P1, P2, P3) / Priority
 * @param manualProgress 进度百分比 (0-100) / Manual progress percentage
 * @param explicitStart 开始时间戳 / Start timestamp
 * @param explicitEnd 截止时间戳 / End or due timestamp
 * @param isOverdue 是否已超期 / Whether it is overdue
 * @param workLog 打卡或工时日志数组 / Work logs or check-in dates
 * @param tags 标签列表 / Tags list
 */
data class TaskItem(
    val id: String,
    val name: String,
    val path: String,
    val type: String = "Task",
    val parentId: String? = null,
    val status: String = "Todo",
    val priority: String = "P2",
    val manualProgress: Int = 0,
    val explicitStart: Long? = null,
    val explicitEnd: Long? = null,
    val isOverdue: Boolean = false,
    val workLog: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val vaultRelativePath: String = ""
) {
    val isCompleted: Boolean
        get() = status.equals("Done", ignoreCase = true)
}
