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
 * @param colorGroupProp 分组色彩键名 (默认 "category") / Property name for color grouping
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
    val colorGroupProp: String = "category"
)
