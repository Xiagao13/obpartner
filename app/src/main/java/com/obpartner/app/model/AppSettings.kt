package com.obpartner.app.model

/**
 * 软件全局配置
 * Global Application Settings
 *
 * @param folderPath 扫描的 Markdown 文件夹绝对路径或 SAF URI / Folder path or SAF Uri
 * @param obsidianVaultName Obsidian 仓库名称 (用于点击直达跳转) / Obsidian vault name
 * @param weekStartsOn 每周起始日 ("monday" 或 "sunday") / First day of the week
 * @param defaultStartHour 周视图/日视图默认起始滚动小时 (默认 8:00) / Default start scroll hour
 * @param startTimeProp 日程开始时间键名 (默认 "start_time") / Property name for event start
 * @param endTimeProp 日程结束时间键名 (默认 "end_time") / Property name for event end
 * @param taskStartKey 任务开始时间键名 (默认 "start_date") / Property name for task start
 * @param taskEndKey 任务截止时间键名 (默认 "due_date") / Property name for task due
 * @param colorGroupProp 分组色彩键名 (默认 "student"，可根据属性着色) / Property name for color grouping
 * @param displayProp 主标题显示键名 (如 "student"，为空时使用笔记标题) / Property name for display title
 * @param displayFields 卡片内部展示的扩展字段列表 (逗号分隔) / Comma-separated fields to display in card
 * @param showContent 是否在日程卡片内展示扩展字段 / Whether to show extra content fields in card
 * @param widgetTheme 桌面微件视觉主题 ("glass", "dark", "amoled", "light") / Desktop widget theme
 */
data class AppSettings(
    val folderPath: String = "",
    val obsidianVaultName: String = "",
    val weekStartsOn: String = "monday",
    val defaultStartHour: Int = 8,
    val startTimeProp: String = "start_time",
    val endTimeProp: String = "end_time",
    val taskStartKey: String = "start_date",
    val taskEndKey: String = "due_date",
    val colorGroupProp: String = "student",
    val displayProp: String = "",
    val displayFields: String = "student, 上课位置, 计价",
    val showContent: Boolean = true,
    val widgetTheme: String = "glass"
)


