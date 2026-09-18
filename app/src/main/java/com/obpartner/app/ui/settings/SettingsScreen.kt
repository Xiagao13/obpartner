package com.obpartner.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.AppSettings
import androidx.glance.appwidget.updateAll
import com.obpartner.app.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设置界面 (配置文件夹路径、系统授权、Obsidian 库名、周起始日及属性映射)
 * Settings Screen (Configure folder paths, system permissions, Obsidian vault name, week start day and property keys)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSettingsSaved: () -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }
    val currentSettings = remember { storageManager.getSettings() }

    var folderPath by remember { mutableStateOf(currentSettings.folderPath) }
    var obsidianVault by remember { mutableStateOf(currentSettings.obsidianVaultName) }
    var vaultPath by remember { mutableStateOf(currentSettings.getEffectiveVaultPath()) }
    var dataFolders by remember { mutableStateOf(currentSettings.dataFolders) }
    var widgetTheme by remember { mutableStateOf(currentSettings.widgetTheme) }
    var weekStartsOn by remember { mutableStateOf(currentSettings.weekStartsOn) }
    var defaultStartHour by remember { mutableStateOf(currentSettings.defaultStartHour.toString()) }
    var startTimeProp by remember { mutableStateOf(currentSettings.startTimeProp) }
    var endTimeProp by remember { mutableStateOf(currentSettings.endTimeProp) }
    var taskStartKey by remember { mutableStateOf(currentSettings.taskStartKey) }
    var taskEndKey by remember { mutableStateOf(currentSettings.taskEndKey) }
    var colorGroupProp by remember { mutableStateOf(currentSettings.colorGroupProp) }
    var displayProp by remember { mutableStateOf(currentSettings.displayProp) }
    var displayFields by remember { mutableStateOf(currentSettings.displayFields) }
    var showContent by remember { mutableStateOf(currentSettings.showContent) }

    // SAF 库根目录选择器
    val vaultFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            vaultPath = uri.toString()
            if (obsidianVault.isBlank()) {
                val decoded = Uri.decode(uri.toString())
                val candidate = decoded.substringAfterLast("/").substringAfterLast(":")
                if (candidate.isNotBlank()) {
                    obsidianVault = candidate
                }
            }
            Toast.makeText(context, "已选取 Obsidian 库根目录", Toast.LENGTH_SHORT).show()
        }
    }

    // SAF 数据子文件夹添加选择器
    val subFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val resolvedSubPath = resolveSubFolderPath(uri, vaultPath, obsidianVault)
            if (resolvedSubPath.isNotBlank()) {
                val currentList = dataFolders.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (!currentList.contains(resolvedSubPath)) {
                    currentList.add(resolvedSubPath)
                    dataFolders = currentList.joinToString(", ")
                    Toast.makeText(context, "已添加数据路径: $resolvedSubPath", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 检查所有文件访问权限 (Android 11+)
    val hasAllFilesAccess = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "软件与数据源配置",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // 权限状态警示条
        if (!hasAllFilesAccess) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "尚未授予【所有文件访问权限】",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "Android 11+ 系统需要此权限以读取手机外部存储中的 Markdown 笔记。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("前往系统设置授权")
                    }
                }
            }
        }

        // 上次扫描结果反馈
        if (storageManager.lastScanSummary.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = storageManager.lastScanSummary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ================= 严格规范的三步式 Obsidian 路径配置卡片 =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📁 Obsidian 库与数据路径规范配置",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 第一步：确定 Obsidian 库名称
                OutlinedTextField(
                    value = obsidianVault,
                    onValueChange = { obsidianVault = it },
                    label = { Text("第一步：Obsidian 库名称 (Vault Name) *") },
                    placeholder = { Text("例如: MyVault 或 个人知识库") },
                    supportingText = { Text("用于在 Obsidian 中以正规协议精准定位仓库") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 第二步：确定库所在的根文件夹
                Column {
                    OutlinedTextField(
                        value = vaultPath,
                        onValueChange = { vaultPath = it },
                        label = { Text("第二步：库所在的根文件夹 (Vault 根目录) *") },
                        placeholder = { Text("/sdcard/Documents/Obsidian/MyVault 或点击下方选择") },
                        supportingText = { Text("指向包含 .obsidian 配置目录的仓库最外层根目录") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { vaultFolderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开系统文件选择器选取库根目录 (SAF)")
                    }
                }

                // 第三步：选择需要调用的数据所在的文件夹路径（可选择多个）
                Column {
                    OutlinedTextField(
                        value = dataFolders,
                        onValueChange = { dataFolders = it },
                        label = { Text("第三步：调用的数据文件夹路径 (支持多选，逗号隔开)") },
                        placeholder = { Text("例如: 日历, 01_Daily/日历, Work/课程 (留空则扫描整个库)") },
                        supportingText = { Text("支持包含完整层级的子路径（如 01_Daily/日历）或绝对路径。本地依据真实路径扫描，打开时自动转为 Obsidian 相对路径") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { subFolderPicker.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ 选取并解析子路径")
                        }
                    }

                    // 常用推荐文件夹快捷标签
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("常用推荐:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val sampleFolders = listOf("日历", "日记", "课程", "日程安排")
                        sampleFolders.forEach { tag ->
                            val isAdded = dataFolders.contains(tag)
                            FilterChip(
                                selected = isAdded,
                                onClick = {
                                    val list = dataFolders.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                    if (isAdded) list.remove(tag) else list.add(tag)
                                    dataFolders = list.joinToString(", ")
                                },
                                label = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 正规相对路径协议跳转效果预览
                val rawFirstFolder = dataFolders.split(",", "，").firstOrNull { it.isNotBlank() }?.trim() ?: ""
                val sampleRelPath = if (rawFirstFolder.isNotBlank()) {
                    val cleanSub = rawFirstFolder.removePrefix("/storage/emulated/0/")
                        .substringAfterLast(":")
                        .removePrefix("/")
                    "$cleanSub/示例日程.md"
                } else {
                    "示例日程.md"
                }
                val sampleVaultName = obsidianVault.ifBlank { "库名" }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("✨ Obsidian 正规协议跳转预览：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "obsidian://open?vault=$sampleVaultName&file=$sampleRelPath",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Text("👉 无论本地扫描时使用多级子路径还是绝对路径，在点击打开笔记时，系统均自动转换为上方正规 Obsidian 库内相对路径，彻底解决找不到笔记问题。", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // 每周起始日 / First Day of Week
        Text(text = "每周起始日", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(
                selected = weekStartsOn.equals("monday", ignoreCase = true),
                onClick = { weekStartsOn = "monday" },
                label = { Text("周一 (Monday)") }
            )
            FilterChip(
                selected = weekStartsOn.equals("sunday", ignoreCase = true),
                onClick = { weekStartsOn = "sunday" },
                label = { Text("周日 (Sunday)") }
            )
        }

        // 4. 默认起始滚动时间 / Default Start Hour
        OutlinedTextField(
            value = defaultStartHour,
            onValueChange = { defaultStartHour = it },
            label = { Text("周视图/日视图默认起始小时 (0-23)") },
            modifier = Modifier.fillMaxWidth()
        )

        // 5. 桌面微件视觉风格 (毛玻璃/暗黑/极黑/透白) / Desktop Widget Visual Theme
        Text(text = "桌面小部件视觉风格", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = widgetTheme.equals("glass", ignoreCase = true),
                onClick = { widgetTheme = "glass" },
                label = { Text("柔光玻璃 ✨") }
            )
            FilterChip(
                selected = widgetTheme.equals("dark", ignoreCase = true),
                onClick = { widgetTheme = "dark" },
                label = { Text("深空暗黑") }
            )
            FilterChip(
                selected = widgetTheme.equals("amoled", ignoreCase = true),
                onClick = { widgetTheme = "amoled" },
                label = { Text("纯粹极黑") }
            )
            FilterChip(
                selected = widgetTheme.equals("light", ignoreCase = true),
                onClick = { widgetTheme = "light" },
                label = { Text("晨曦透白") }
            )
        }
        Text(
            text = "默认推荐「柔光玻璃」，具备 70% 柔光暗晶透光与柔白微边框，透出壁纸质感极佳；修改保存后立即全量同步到桌面微件。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider()

        Text(
            text = "Freepace 属性映射 (自定义 Frontmatter 键名)",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = startTimeProp,
            onValueChange = { startTimeProp = it },
            label = { Text("日程开始时间属性键 (默认: start_time)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = endTimeProp,
            onValueChange = { endTimeProp = it },
            label = { Text("日程结束时间属性键 (默认: end_time)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = taskStartKey,
            onValueChange = { taskStartKey = it },
            label = { Text("任务开始日期键 (默认: start_date)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = taskEndKey,
            onValueChange = { taskEndKey = it },
            label = { Text("任务截止日期键 (默认: due_date)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = colorGroupProp,
            onValueChange = { colorGroupProp = it },
            label = { Text("颜色分组属性键 (默认: student，可按学员或类别生成主题色)") },
            supportingText = { Text("如: student, category, type 等，将按此属性哈希出唯美色彩") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = displayProp,
            onValueChange = { displayProp = it },
            label = { Text("卡片主标题属性键 (留空时默认使用笔记文件名或 title)") },
            placeholder = { Text("例如: student") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = displayFields,
            onValueChange = { displayFields = it },
            label = { Text("卡片内部展示的扩展字段 (英文逗号分隔)") },
            placeholder = { Text("例如: student, 上课位置, 计价, 计费课时") },
            supportingText = { Text("周/日视图及桌面大卡片将在日程下方紧凑显示这些元数据") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("在日程卡片内展开扩展字段", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("关闭后仅展示标题与时间段，开启后渲染如地点、金额等", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = showContent,
                onCheckedChange = { showContent = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val newSettings = AppSettings(
                    obsidianVaultName = obsidianVault.trim(),
                    vaultPath = vaultPath.trim(),
                    dataFolders = dataFolders.trim(),
                    folderPath = vaultPath.trim(),
                    weekStartsOn = weekStartsOn,
                    defaultStartHour = defaultStartHour.toIntOrNull() ?: 8,
                    startTimeProp = startTimeProp.trim(),
                    endTimeProp = endTimeProp.trim(),
                    taskStartKey = taskStartKey.trim(),
                    taskEndKey = taskEndKey.trim(),
                    colorGroupProp = colorGroupProp.trim(),
                    displayProp = displayProp.trim(),
                    displayFields = displayFields.trim(),
                    showContent = showContent,
                    widgetTheme = widgetTheme
                )
                storageManager.saveSettings(newSettings)

                // 异步立即触发所有桌面微件全量刷新
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        CalendarWidget3x2().updateAll(context)
                        CalendarWidget2x3().updateAll(context)
                        CalendarWidget2x2().updateAll(context)
                        TodayTasksWidget3x2().updateAll(context)
                        TodayTasksWidget2x2().updateAll(context)
                    } catch (_: Exception) {
                    }
                }

                Toast.makeText(context, "配置已保存，桌面组件已同步刷新！", Toast.LENGTH_SHORT).show()
                onSettingsSaved()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("保存并重新扫描数据")
        }
    }
}

/**
 * 智能解析 SAF 选取的子文件夹路径
 * 若所选文件夹位于当前 Vault 根目录下，自动提取包含多级目录的相对子路径 (如 "01_Daily/日历")；
 * 若不在当前库下，返回完整绝对物理路径或存储路径，绝不丢失任何父级路径层级。
 *
 * Resolve SAF selected sub-folder path, extracting accurate relative sub-path or absolute path
 */
private fun resolveSubFolderPath(subUri: Uri, currentVaultPath: String, vaultName: String): String {
    val decoded = Uri.decode(subUri.toString())

    // 1. 提取 subUri 中的文档路径部分 (例如 Documents/Obsidian/MyVault/01_Daily/日历)
    val docPath = if (decoded.contains("tree/")) {
        val treePart = decoded.substringAfter("tree/")
        if (treePart.contains(":")) treePart.substringAfter(":") else treePart
    } else if (decoded.contains(":")) {
        decoded.substringAfter(":")
    } else {
        decoded.substringAfterLast("://")
    }.replace("\\", "/").trim().removePrefix("/").removeSuffix("/")

    // 2. 尝试从已配置的 currentVaultPath 中提取库根路径
    val vaultRootDocPath = if (currentVaultPath.startsWith("content://")) {
        val decodedRoot = Uri.decode(currentVaultPath)
        if (decodedRoot.contains("tree/")) {
            val rootTree = decodedRoot.substringAfter("tree/")
            if (rootTree.contains(":")) rootTree.substringAfter(":") else rootTree
        } else if (decodedRoot.contains(":")) {
            decodedRoot.substringAfter(":")
        } else {
            decodedRoot.substringAfterLast("://")
        }
    } else if (currentVaultPath.isNotBlank()) {
        currentVaultPath.removePrefix("/storage/emulated/0/")
            .removePrefix("/sdcard/")
            .removePrefix("/")
    } else {
        ""
    }.replace("\\", "/").trim().removePrefix("/").removeSuffix("/")

    // 3. 对比：如果子路径以库根路径开头，则提取完整的库内相对子路径 (例如 "01_Daily/日历")
    if (vaultRootDocPath.isNotBlank() && docPath.startsWith(vaultRootDocPath, ignoreCase = true)) {
        val rel = docPath.substring(vaultRootDocPath.length).removePrefix("/").removeSuffix("/")
        if (rel.isNotBlank()) return rel
    }

    // 4. 对比库名：如果包含 "库名/"，截取库名后的完整相对子路径 (例如 "01_Daily/日历")
    val vName = vaultName.trim()
    if (vName.isNotBlank() && docPath.contains("$vName/", ignoreCase = true)) {
        val rel = docPath.substringAfter("$vName/", "").removePrefix("/").removeSuffix("/")
        if (rel.isNotBlank()) return rel
    }

    // 5. 若不在当前库根目录下，且属于 primary 分区，还原为完整标准绝对物理路径
    if (decoded.contains("primary:")) {
        val subP = decoded.substringAfter("primary:").removePrefix("/")
        return "/storage/emulated/0/$subP"
    }

    // 6. 兜底返回 docPath (保留完整目录层级，绝不丢弃父级路径)
    return if (docPath.isNotBlank()) docPath else subUri.toString()
}

