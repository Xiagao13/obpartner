package com.obpartner.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

    var lastScanSummary: String = ""
        private set

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
            colorGroupProp = prefs.getString("color_group_prop", "student") ?: "student",
            displayProp = prefs.getString("display_prop", "") ?: "",
            displayFields = prefs.getString("display_fields", "student, 上课位置, 计价") ?: "student, 上课位置, 计价",
            showContent = prefs.getBoolean("show_content", true),
            widgetTheme = prefs.getString("widget_theme", "glass") ?: "glass"
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
            putString("display_prop", settings.displayProp)
            putString("display_fields", settings.displayFields)
            putBoolean("show_content", settings.showContent)
            putString("widget_theme", settings.widgetTheme)
            apply()
        }
    }



    /**
     * 扫描 Markdown 文件并解析日程、任务与习惯
     * Scan and parse all Markdown files under specified folder paths or SAF tree URIs
     */
    fun scanVault(): Triple<List<CalendarEvent>, List<TaskItem>, List<HabitItem>> {
        val settings = getSettings()
        val pathStr = settings.folderPath

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

        // 若用户未指定路径，自动尝试扫描安卓手机常见 Obsidian 笔记目录
        val folders = if (pathStr.isNotBlank()) {
            pathStr.split(",").map { it.trim().removeSuffix("/") }.filter { it.isNotEmpty() }
        } else {
            listOf(
                "/storage/emulated/0/Documents/Obsidian",
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Obsidian",
                "/sdcard/Documents/Obsidian",
                "/sdcard/Obsidian"
            )
        }

        var scannedFilesCount = 0

        for (fPath in folders) {
            if (fPath.startsWith("content://")) {
                // 1. SAF DocumentFile 模式 / Storage Access Framework Tree Uri
                try {
                    val treeUri = Uri.parse(fPath)
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        scanDocumentDir(rootDoc, settings, todayMidnight, todayStr, events, tasks, habits) {
                            scannedFilesCount++
                        }
                    }
                } catch (_: Exception) {
                }
            } else {
                // 2. 标准文件路径模式 / Standard File Path
                try {
                    val dir = File(fPath)
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown()
                            .filter { it.isFile && (it.extension.equals("md", ignoreCase = true) || it.extension.equals("markdown", ignoreCase = true)) }
                            .forEach { file ->
                                scannedFilesCount++
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
                } catch (_: Exception) {
                }
            }
        }

        lastScanSummary = "扫描完成：共扫描 $scannedFilesCount 个文件，解析出 ${events.size} 个日程，${tasks.size} 个待办，${habits.size} 个习惯"

        return Triple(events, tasks, habits)
    }

    private fun scanDocumentDir(
        dir: DocumentFile,
        settings: AppSettings,
        todayMidnight: Long,
        todayStr: String,
        events: MutableList<CalendarEvent>,
        tasks: MutableList<TaskItem>,
        habits: MutableList<HabitItem>,
        onFileScanned: () -> Unit
    ) {
        val files = dir.listFiles()
        for (doc in files) {
            if (doc.isDirectory) {
                scanDocumentDir(doc, settings, todayMidnight, todayStr, events, tasks, habits, onFileScanned)
            } else if (doc.isFile && (doc.name?.endsWith(".md", ignoreCase = true) == true || doc.name?.endsWith(".markdown", ignoreCase = true) == true)) {
                onFileScanned()
                try {
                    context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                        val fm = FrontmatterScanner.scanFrontmatter(stream)
                        if (fm.isNotEmpty()) {
                            val name = doc.name ?: "Unknown"
                            val path = doc.uri.toString()
                            MarkdownEventScanner.parseEvent(path, name, fm, settings)?.let {
                                events.add(it)
                            }
                            MarkdownTaskScanner.parseTask(path, name, fm, settings, todayMidnight)?.let {
                                tasks.add(it)
                            }
                            MarkdownTaskScanner.parseHabit(path, name, fm, todayStr)?.let {
                                habits.add(it)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 生成呼起 Obsidian 并直接打开指定笔记的 Intent (核心需求：桌面点击直达)
     * Generate Intent to open Obsidian and navigate to the specified note
     */
    fun createOpenObsidianIntent(filePath: String): Intent {
        val settings = getSettings()
        val vaultName = settings.obsidianVaultName
        val fileName = File(filePath).nameWithoutExtension

        val encodedFile = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
        val uri = if (vaultName.isNotBlank()) {
            val encodedVault = URLEncoder.encode(vaultName, StandardCharsets.UTF_8.toString())
            Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")
        } else {
            Uri.parse("obsidian://open?file=$encodedFile")
        }

        return try {
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            // 备选方案
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(filePath), "text/markdown")
                setPackage("md.obsidian")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
