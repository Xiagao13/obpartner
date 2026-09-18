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
                    put("vaultRelativePath", ev.vaultRelativePath)
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
                        extraData = extraMap,
                        vaultRelativePath = obj.optString("vaultRelativePath", "")
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
            obsidianVaultName = prefs.getString("obsidian_vault", "") ?: "",
            vaultPath = prefs.getString("vault_path", "") ?: "",
            dataFolders = prefs.getString("data_folders", "") ?: "",
            folderPath = prefs.getString("folder_path", "") ?: "",
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
            putString("obsidian_vault", settings.obsidianVaultName)
            putString("vault_path", settings.vaultPath)
            putString("data_folders", settings.dataFolders)
            putString("folder_path", settings.folderPath)
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
     * 规范化扫描 Obsidian 库：先定位 Vault 根目录，再按需过滤数据子文件夹，精准提取相对路径
     * Standard Vault Scanner: Locates Vault root, scans specified data folders and computes relative paths
     */
    fun scanVault(): Triple<List<CalendarEvent>, List<TaskItem>, List<HabitItem>> {
        val settings = getSettings()
        val vaultRoot = settings.getEffectiveVaultPath()
        val dataFolderList = settings.getDataFolderList()

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

        var scannedFilesCount = 0

        if (vaultRoot.isNotBlank()) {
            if (vaultRoot.startsWith("content://")) {
                // 1. SAF 模式
                try {
                    val rootUri = Uri.parse(vaultRoot)
                    val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        if (dataFolderList.isNotEmpty()) {
                            for (folderName in dataFolderList) {
                                val targetDir = findSubDocumentDir(rootDoc, folderName)
                                if (targetDir != null && targetDir.isDirectory) {
                                    scanDocumentDir(targetDir, settings, todayMidnight, todayStr, events, tasks, habits, folderName) {
                                        scannedFilesCount++
                                    }
                                }
                            }
                        } else {
                            scanDocumentDir(rootDoc, settings, todayMidnight, todayStr, events, tasks, habits, "") {
                                scannedFilesCount++
                            }
                        }
                    }
                } catch (_: Exception) {}
            } else {
                // 2. 本地标准路径模式
                try {
                    val rootDir = File(vaultRoot)
                    if (rootDir.exists() && rootDir.isDirectory) {
                        if (dataFolderList.isNotEmpty()) {
                            for (folderName in dataFolderList) {
                                val targetDir = if (folderName.startsWith("/")) File(folderName) else File(rootDir, folderName)
                                if (targetDir.exists() && targetDir.isDirectory) {
                                    scanLocalDir(targetDir, rootDir, settings, todayMidnight, todayStr, events, tasks, habits) {
                                        scannedFilesCount++
                                    }
                                }
                            }
                        } else {
                            scanLocalDir(rootDir, rootDir, settings, todayMidnight, todayStr, events, tasks, habits) {
                                scannedFilesCount++
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            // 用户未配置时探测安卓常见 Obsidian 目录
            val candidateFolders = listOf(
                "/storage/emulated/0/Documents/Obsidian",
                "/storage/emulated/0/Obsidian",
                "/sdcard/Documents/Obsidian",
                "/sdcard/Obsidian"
            )
            for (fPath in candidateFolders) {
                val dir = File(fPath)
                if (dir.exists() && dir.isDirectory) {
                    scanLocalDir(dir, dir, settings, todayMidnight, todayStr, events, tasks, habits) {
                        scannedFilesCount++
                    }
                }
            }
        }

        // 去重与排序
        val distinctEvents = events.distinctBy { "${it.title}_${it.start}_${it.end}" }.sortedBy { it.start }
        val distinctTasks = tasks.distinctBy { it.id }
        val distinctHabits = habits.distinctBy { it.id }

        // 更新内存缓存与磁盘持久化快照
        cachedEvents = distinctEvents
        cachedTasks = distinctTasks
        cachedHabits = distinctHabits
        lastScanTimestamp = System.currentTimeMillis()
        saveDiskSnapshot(distinctEvents)

        val folderDesc = if (dataFolderList.isNotEmpty()) "【${dataFolderList.joinToString(", ")}】" else "整个库"
        lastScanSummary = "扫描完成：已扫描 $folderDesc 共 $scannedFilesCount 个笔记，解析出 ${distinctEvents.size} 个日程，${distinctTasks.size} 个待办，${distinctHabits.size} 个习惯"

        return Triple(distinctEvents, distinctTasks, distinctHabits)
    }

    private fun findSubDocumentDir(root: DocumentFile, subPath: String): DocumentFile? {
        val parts = subPath.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        var current: DocumentFile? = root
        for (part in parts) {
            current = current?.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(part, ignoreCase = true) }
            if (current == null) break
        }
        return current
    }

    private fun scanLocalDir(
        dir: File,
        vaultRoot: File,
        settings: AppSettings,
        todayMidnight: Long,
        todayStr: String,
        events: MutableList<CalendarEvent>,
        tasks: MutableList<TaskItem>,
        habits: MutableList<HabitItem>,
        onFileScanned: () -> Unit
    ) {
        dir.walkTopDown()
            .onEnter { d -> !shouldIgnoreDirectory(d.name) }
            .filter { it.isFile && (it.extension.equals("md", ignoreCase = true) || it.extension.equals("markdown", ignoreCase = true)) }
            .forEach { file ->
                onFileScanned()
                try {
                    val relPath = file.relativeToOrNull(vaultRoot)?.path?.replace("\\", "/")?.removePrefix("/") ?: file.name
                    val text = file.readText()
                    val (fm, body) = FrontmatterScanner.extractFrontmatterAndBody(text)
                    if (fm.isNotEmpty()) {
                        MarkdownEventScanner.parseEvent(file.absolutePath, file.name, fm, settings, relativePath = relPath)?.let {
                            events.add(it)
                        }
                        MarkdownTaskScanner.parseTask(file.absolutePath, file.name, fm, settings, todayMidnight, relativePath = relPath)?.let {
                            tasks.add(it)
                        }
                        MarkdownTaskScanner.parseHabit(file.absolutePath, file.name, fm, todayStr, relativePath = relPath)?.let {
                            habits.add(it)
                        }
                    }
                    val bodyEvents = MarkdownEventScanner.parseBodyEvents(file.absolutePath, file.name, body, fm, settings, relativePath = relPath)
                    events.addAll(bodyEvents)
                } catch (_: Exception) {}
            }
    }

    private fun scanDocumentDir(
        dir: DocumentFile,
        settings: AppSettings,
        todayMidnight: Long,
        todayStr: String,
        events: MutableList<CalendarEvent>,
        tasks: MutableList<TaskItem>,
        habits: MutableList<HabitItem>,
        relativeParent: String,
        onFileScanned: () -> Unit
    ) {
        val files = dir.listFiles()
        for (doc in files) {
            val docName = doc.name ?: continue
            if (doc.isDirectory) {
                if (!shouldIgnoreDirectory(docName)) {
                    val nextRel = if (relativeParent.isBlank()) docName else "$relativeParent/$docName"
                    scanDocumentDir(doc, settings, todayMidnight, todayStr, events, tasks, habits, nextRel, onFileScanned)
                }
            } else if (doc.isFile && (docName.endsWith(".md", ignoreCase = true) || docName.endsWith(".markdown", ignoreCase = true))) {
                onFileScanned()
                try {
                    val relPath = if (relativeParent.isBlank()) docName else "$relativeParent/$docName"
                    context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                        val text = stream.bufferedReader().use { it.readText() }
                        val (fm, body) = FrontmatterScanner.extractFrontmatterAndBody(text)
                        val path = doc.uri.toString()

                        if (fm.isNotEmpty()) {
                            MarkdownEventScanner.parseEvent(path, docName, fm, settings, relativePath = relPath)?.let {
                                events.add(it)
                            }
                            MarkdownTaskScanner.parseTask(path, docName, fm, settings, todayMidnight, relativePath = relPath)?.let {
                                tasks.add(it)
                            }
                            MarkdownTaskScanner.parseHabit(path, docName, fm, todayStr, relativePath = relPath)?.let {
                                habits.add(it)
                            }
                        }
                        val bodyEvents = MarkdownEventScanner.parseBodyEvents(path, docName, body, fm, settings, relativePath = relPath)
                        events.addAll(bodyEvents)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * 生成呼起 Obsidian 并直接打开指定笔记的标准 Intent
     * 严格遵循 Obsidian 官方 URI Scheme: obsidian://open?vault=<vaultName>&file=<relativePathInVault>
     * 坚决杜绝移动端完整绝对路径 (/storage/...) 与前导斜杠，确保 100% 精确唤醒并打开对应笔记
     *
     * Generate standard Intent to open note in Obsidian using official relative path protocol
     */
    fun createOpenObsidianIntent(filePath: String, relativePathInVault: String? = null): Intent {
        val settings = getSettings()
        var vaultName = settings.obsidianVaultName.trim()
        val cleanPath = filePath.substringBefore("#").trim()

        // 1. 获取纯净的相对路径 (优先使用传入的 relativePathInVault)
        var targetRelativePath = (relativePathInVault?.trim() ?: "").replace("\\", "/").removePrefix("/")

        // 2. 若无传入相对路径，从 cleanPath 和设置中智能提取纯净相对路径
        if (targetRelativePath.isBlank()) {
            targetRelativePath = extractVaultRelativePath(cleanPath, settings)
        }

        targetRelativePath = targetRelativePath.replace("\\", "/").removePrefix("/")

        // 若依然误带库名前缀 (例如 "MyVault/日历/today.md")，剥离第一级库名，因为 obsidian://open 的 file 参数是在库根目录下寻址
        if (vaultName.isNotBlank() && targetRelativePath.startsWith("$vaultName/")) {
            targetRelativePath = targetRelativePath.substringAfter("$vaultName/").removePrefix("/")
        }

        // 若库名为空，尝试从路径中自动推导
        if (vaultName.isBlank()) {
            vaultName = guessVaultName(cleanPath, settings)
        }

        // 3. 构建符合 Obsidian 官方规范的 URI
        val uri = if (vaultName.isNotBlank() && targetRelativePath.isNotBlank()) {
            val encodedVault = URLEncoder.encode(vaultName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val encodedFile = URLEncoder.encode(targetRelativePath, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")
        } else if (vaultName.isNotBlank()) {
            val encodedVault = URLEncoder.encode(vaultName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            Uri.parse("obsidian://open?vault=$encodedVault")
        } else {
            val onlyFileName = File(cleanPath).name
            val encodedFile = URLEncoder.encode(onlyFileName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
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

    private fun extractVaultRelativePath(cleanPath: String, settings: AppSettings): String {
        val vaultRoot = settings.getEffectiveVaultPath()
        val vaultName = settings.obsidianVaultName.trim()

        if (cleanPath.startsWith("content://")) {
            val decoded = Uri.decode(cleanPath)
            if (vaultName.isNotBlank() && decoded.contains("$vaultName/")) {
                return decoded.substringAfter("$vaultName/").removePrefix("/")
            }
            if (decoded.contains("Obsidian/")) {
                val afterObsidian = decoded.substringAfter("Obsidian/")
                return afterObsidian.substringAfter("/").removePrefix("/")
            }
            return Uri.parse(cleanPath).lastPathSegment?.substringAfterLast(":")?.substringAfterLast("/") ?: File(cleanPath).name
        }

        // 本地路径
        if (vaultRoot.isNotBlank() && cleanPath.startsWith(vaultRoot)) {
            return cleanPath.removePrefix(vaultRoot).removePrefix("/")
        }
        if (vaultName.isNotBlank() && cleanPath.contains("/$vaultName/")) {
            return cleanPath.substringAfter("/$vaultName/").removePrefix("/")
        }

        // 向上探测 .obsidian 目录以精准截取
        val fileObj = File(cleanPath)
        var curr = fileObj.parentFile
        while (curr != null && curr.name.isNotBlank()) {
            if (File(curr, ".obsidian").exists()) {
                return fileObj.relativeToOrNull(curr)?.path?.replace("\\", "/")?.removePrefix("/") ?: fileObj.name
            }
            curr = curr.parentFile
        }

        return fileObj.name
    }

    private fun guessVaultName(cleanPath: String, settings: AppSettings): String {
        val vaultRoot = settings.getEffectiveVaultPath()
        if (vaultRoot.isNotBlank()) {
            if (vaultRoot.startsWith("content://")) {
                val decoded = Uri.decode(vaultRoot)
                return decoded.substringAfterLast("/").substringAfterLast(":")
            } else {
                return File(vaultRoot).name
            }
        }
        val fileObj = File(cleanPath)
        var curr = fileObj.parentFile
        while (curr != null && curr.name.isNotBlank()) {
            if (File(curr, ".obsidian").exists()) {
                return curr.name
            }
            curr = curr.parentFile
        }
        return ""
    }
}
