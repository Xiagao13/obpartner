package com.obpartner.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.model.AppSettings
import com.obpartner.app.model.CalendarEvent
import com.obpartner.app.model.HabitItem
import com.obpartner.app.model.TaskItem
import com.obpartner.app.parser.FrontmatterScanner
import com.obpartner.app.parser.MarkdownEventScanner
import com.obpartner.app.parser.MarkdownTaskScanner
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Date

/**
 * 本地存储与 Obsidian 联动管理器
 * Storage and Obsidian Interoperability Manager
 */
class StorageManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("obpartner_prefs", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        return AppSettings(
            folderPath = prefs.getString("folder_path", "") ?: "",
            obsidianVaultName = prefs.getString("obsidian_vault", "") ?: "",
            weekStartsOn = prefs.getString("week_starts_on", "monday") ?: "monday",
            defaultStartHour = prefs.getInt("default_start_hour", 8),
            startTimeProp = prefs.getString("start_time_prop", "start_time") ?: "start_time",
            endTimeProp = prefs.getString("end_time_prop", "end_time") ?: "end_time",
            taskStartKey = prefs.getString("task_start_key", "start_date") ?: "start_date",
            taskEndKey = prefs.getString("task_end_key", "due_date") ?: "due_date",
            colorGroupProp = prefs.getString("color_group_prop", "category") ?: "category"
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().apply {
            putString("folder_path", settings.folderPath)
            putString("obsidian_vault", settings.obsidianVaultName)
            putString("week_starts_on", settings.weekStartsOn)
            putInt("default_start_hour", settings.defaultStartHour)
            putString("start_time_prop", settings.startTimeProp)
            putString("end_time_prop", settings.endTimeProp)
            putString("task_start_key", settings.taskStartKey)
            putString("task_end_key", settings.taskEndKey)
            putString("color_group_prop", settings.colorGroupProp)
            apply()
        }
    }

    /**
     * 扫描指定目录下的全部 Markdown 文件并解析
     * Scan and parse all Markdown files under specified folder paths
     */
    fun scanVault(): Triple<List<CalendarEvent>, List<TaskItem>, List<HabitItem>> {
        val settings = getSettings()
        val pathStr = settings.folderPath
        if (pathStr.isBlank()) return Triple(emptyList(), emptyList(), emptyList())

        val events = mutableListOf<CalendarEvent>()
        val tasks = mutableListOf<TaskItem>()
        val habits = mutableListOf<HabitItem>()

        val todayDate = Date()
        val todayStr = CalendarUtils.formatDate(todayDate)
        val cal = Calendar.getInstance().apply {
            time = todayDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMidnight = cal.timeInMillis

        val folders = pathStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        for (fPath in folders) {
            val dir = File(fPath)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile && it.extension.equals("md", ignoreCase = true) }.forEach { file ->
                    try {
                        file.inputStream().use { stream ->
                            val fm = FrontmatterScanner.scanFrontmatter(stream)
                            if (fm.isNotEmpty()) {
                                MarkdownEventScanner.parseEvent(file.absolutePath, file.name, fm, settings)?.let {
                                    events.add(it)
                                }
                                MarkdownTaskScanner.parseTask(file.absolutePath, file.name, fm, settings, todayMidnight)?.let {
                                    tasks.add(it)
                                }
                                MarkdownTaskScanner.parseHabit(file.absolutePath, file.name, fm, todayStr)?.let {
                                    habits.add(it)
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return Triple(events, tasks, habits)
    }

    /**
     * 生成呼起 Obsidian 并直接打开指定笔记的 Intent (核心需求：桌面点击直达)
     * Generate Intent to open Obsidian and navigate to the specified note
     */
    fun createOpenObsidianIntent(filePath: String): Intent {
        val settings = getSettings()
        val vaultName = settings.obsidianVaultName
        val fileName = File(filePath).nameWithoutExtension

        return if (vaultName.isNotBlank()) {
            // 通过官方 obsidian:// URI scheme 直接定位笔记
            val encodedVault = URLEncoder.encode(vaultName, StandardCharsets.UTF_8.toString())
            val encodedFile = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
            val uri = Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // 兜底方案：通过标准文件查看 Intent 呼起 Obsidian
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/markdown")
                setPackage("md.obsidian")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
