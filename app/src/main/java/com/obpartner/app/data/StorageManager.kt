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
import org.json.JSONArray
import org.json.JSONObject
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

    companion object {
        @Volatile
        var cachedEvents: List<CalendarEvent>? = null
        @Volatile
        var cachedTasks: List<TaskItem>? = null
        @Volatile
        var cachedHabits: List<HabitItem>? = null
        @Volatile
        var lastScanTimestamp: Long = 0L
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5分钟内存缓存 / 5 minutes cache validity

        fun invalidateCache() {
            cachedEvents = null
            cachedTasks = null
            cachedHabits = null
            lastScanTimestamp = 0L
        }

        private fun shouldIgnoreDirectory(dirName: String): Boolean {
            val lower = dirName.lowercase()
            return lower == ".obsidian" ||
                    lower == ".git" ||
                    lower == ".trash" ||
                    lower == "node_modules" ||
                    lower == ".smart-env" ||
                    lower == ".canvas" ||
                    lower.startsWith(".trash")
        }
    }

    var lastScanSummary: String = ""
        private set

    /**
     * 将解析出的日程快照持久化到磁盘，保障桌面微件冷启动毫秒级渲染
     * Save parsed events snapshot to disk cache for millisecond widget cold start
     */
    private fun saveDiskSnapshot(events: List<CalendarEvent>) {
        try {
            val arr = JSONArray()
            for (ev in events.take(300)) {
                val obj = JSONObject().apply {
                    put("id", ev.id)
                    put("title", ev.title)
                    put("path", ev.path)
                    put("start", ev.start)
                    put("end", ev.end)
                    put("isAllDay", ev.isAllDay)
                    put("isCrossDay", ev.isCrossDay)
                    put("colorValue", ev.colorValue)
                    put("displayText", ev.displayText)
                    if (ev.extraData.isNotEmpty()) {
                        val extraJson = JSONObject()
                        for ((k, v) in ev.extraData) {
                            extraJson.put(k, v.toString())
                        }
                        put("extraData", extraJson)
                    }
                }
                arr.put(obj)
            }
            prefs.edit().putString("disk_cache_events", arr.toString()).commit()
        } catch (_: Exception) {}
    }

    /**
     * 从磁盘快照读取持久化缓存
     * Load persisted events snapshot from disk
     */
    private fun loadDiskSnapshot(): List<CalendarEvent> {
        try {
            val raw = prefs.getString("disk_cache_events", null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<CalendarEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val extraMap = mutableMapOf<String, Any>()
                if (obj.has("extraData")) {
                    val extraObj = obj.getJSONObject("extraData")
                    val keys = extraObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        extraMap[k] = extraObj.optString(k)
                    }
                }
                list.add(
                    CalendarEvent(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        path = obj.optString("path"),
                        start = obj.optLong("start"),
                        end = obj.optLong("end"),
                        isAllDay = obj.optBoolean("isAllDay", false),
                        isCrossDay = obj.optBoolean("isCrossDay", false),
                        colorValue = obj.optString("colorValue", "default"),
                        displayText = obj.optString("displayText", ""),
                        extraData = extraMap
                    )
                )
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * 极速获取缓存数据（微秒级），供桌面微件首帧直接渲染，绝不阻塞主线程，彻底消除无响应与等待
     * Fast retrieve cached events (<1ms), for immediate widget rendering without any UI freeze
     */
    fun getCachedVaultFast(): List<CalendarEvent> {
        val mem = cachedEvents
        if (mem != null) {
            return mem
        }
        val disk = loadDiskSnapshot()
        if (disk.isNotEmpty()) {
            cachedEvents = disk
            return disk
        }
        return emptyList()
    }

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
        invalidateCache()
    }

    /**
     * 获取缓存的日程与待办数据
     * Get cached vault data
     */
    fun getCachedVault(forceRefresh: Boolean = false): Triple<List<CalendarEvent>, List<TaskItem>, List<HabitItem>> {
        val now = System.currentTimeMillis()
        val events = cachedEvents
        val tasks = cachedTasks
        val habits = cachedHabits
        if (!forceRefresh && events != null && tasks != null && habits != null && (now - lastScanTimestamp < CACHE_VALIDITY_MS)) {
            return Triple(events, tasks, habits)
        }
        return scanVault()
    }

    /**
     * 扫描 Markdown 文件并解析日程、任务与习惯 (支持 Frontmatter 与正文 ## 日程安排 混合提取)
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

        // 若用户未指定路径，自动尝试扫描安卓手机常见 Obsidian 笔记目录 (坚决不扫描 Documents 根目录以绝后患)
        val folders = if (pathStr.isNotBlank()) {
            pathStr.split(",").map { it.trim().removeSuffix("/") }.filter { it.isNotEmpty() }
        } else {
            listOf(
                "/storage/emulated/0/Documents/Obsidian",
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
                            .onEnter { d -> !shouldIgnoreDirectory(d.name) }
                            .filter { it.isFile && (it.extension.equals("md", ignoreCase = true) || it.extension.equals("markdown", ignoreCase = true)) }
                            .forEach { file ->
                                scannedFilesCount++
                                try {
                                    val text = file.readText()
                                    val (fm, body) = FrontmatterScanner.extractFrontmatterAndBody(text)
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
                                    // 提取 Markdown 正文日程 (如日记中的 ## 日程安排 或时间任务)
                                    val bodyEvents = MarkdownEventScanner.parseBodyEvents(file.absolutePath, file.name, body, fm, settings)
                                    events.addAll(bodyEvents)
                                } catch (_: Exception) {
                                }
                            }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 去重与排序 (同一文件若有重名日程避免重复显示)
        val distinctEvents = events.distinctBy { "${it.title}_${it.start}_${it.end}" }.sortedBy { it.start }
        val distinctTasks = tasks.distinctBy { it.id }
        val distinctHabits = habits.distinctBy { it.id }

        // 更新内存缓存与磁盘持久化快照
        cachedEvents = distinctEvents
        cachedTasks = distinctTasks
        cachedHabits = distinctHabits
        lastScanTimestamp = System.currentTimeMillis()
        saveDiskSnapshot(distinctEvents)

        lastScanSummary = "扫描完成：共扫描 $scannedFilesCount 个文件，解析出 ${distinctEvents.size} 个日程，${distinctTasks.size} 个待办，${distinctHabits.size} 个习惯"

        return Triple(distinctEvents, distinctTasks, distinctHabits)
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
                val dirName = doc.name ?: ""
                if (!shouldIgnoreDirectory(dirName)) {
                    scanDocumentDir(doc, settings, todayMidnight, todayStr, events, tasks, habits, onFileScanned)
                }
            } else if (doc.isFile && (doc.name?.endsWith(".md", ignoreCase = true) == true || doc.name?.endsWith(".markdown", ignoreCase = true) == true)) {
                onFileScanned()
                try {
                    context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                        val text = stream.bufferedReader().use { it.readText() }
                        val (fm, body) = FrontmatterScanner.extractFrontmatterAndBody(text)
                        val name = doc.name ?: "Unknown"
                        val path = doc.uri.toString()

                        if (fm.isNotEmpty()) {
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
                        val bodyEvents = MarkdownEventScanner.parseBodyEvents(path, name, body, fm, settings)
                        events.addAll(bodyEvents)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 生成呼起 Obsidian 并直接打开指定笔记的 Intent (修复“找不到笔记”问题，精准保留相对路径与 Vault 库名)
     * Generate Intent to open Obsidian with accurate relative path and vault name
     */
    fun createOpenObsidianIntent(filePath: String): Intent {
        val settings = getSettings()
        var vaultName = settings.obsidianVaultName.trim()
        val cleanPath = filePath.substringBefore("#") // 移除行号锚点

        var relativePath: String? = null

        if (cleanPath.startsWith("content://")) {
            // SAF 模式：解析 Uri
            val decoded = Uri.decode(cleanPath)
            if (vaultName.isNotBlank() && decoded.contains("$vaultName/")) {
                relativePath = decoded.substringAfter("$vaultName/")
            } else if (decoded.contains("Obsidian/")) {
                val afterObsidian = decoded.substringAfter("Obsidian/")
                val parts = afterObsidian.split("/", limit = 2)
                if (parts.isNotEmpty() && vaultName.isBlank()) {
                    vaultName = parts[0]
                }
                if (parts.size > 1) {
                    relativePath = parts[1]
                }
            }
        } else {
            // 本地标准绝对路径
            val fileObj = File(cleanPath)
            if (vaultName.isNotBlank() && cleanPath.contains("/$vaultName/")) {
                relativePath = cleanPath.substringAfter("/$vaultName/")
            } else {
                // 向上查找 .obsidian 目录以自动确定 Vault 根目录与库名
                var curr = fileObj.parentFile
                while (curr != null && curr.name.isNotBlank()) {
                    if (File(curr, ".obsidian").exists()) {
                        if (vaultName.isBlank()) {
                            vaultName = curr.name
                        }
                        relativePath = fileObj.relativeToOrNull(curr)?.path
                        break
                    }
                    curr = curr.parentFile
                }
            }
        }

        val targetFile = (relativePath ?: File(cleanPath).name).replace("\\", "/")

        val uri = if (vaultName.isNotBlank()) {
            val encodedVault = URLEncoder.encode(vaultName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val encodedFile = URLEncoder.encode(targetFile, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")
        } else {
            val encodedFile = URLEncoder.encode(targetFile, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            Uri.parse("obsidian://open?file=$encodedFile")
        }

        return try {
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(cleanPath), "text/markdown")
                setPackage("md.obsidian")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
