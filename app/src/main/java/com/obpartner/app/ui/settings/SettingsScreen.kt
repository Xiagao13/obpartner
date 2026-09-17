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


    // SAF 文件夹选择器
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            folderPath = uri.toString()
            Toast.makeText(context, "已选取文件夹: $uri", Toast.LENGTH_SHORT).show()
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

        // 1. 文件夹路径与选择器 / Folder Paths
        Column {
            OutlinedTextField(
                value = folderPath,
                onValueChange = { folderPath = it },
                label = { Text("Markdown 文件夹路径") },
                placeholder = { Text("/sdcard/Documents/Obsidian 或 点击右侧选择") },
                supportingText = { Text("留空时会自动尝试扫描系统默认 Documents/Obsidian 目录") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("打开系统文件选择器选取文件夹 (SAF)")
            }
        }

        // 2. Obsidian 仓库名 (用于一键直达跳转) / Obsidian Vault Name
        OutlinedTextField(
            value = obsidianVault,
            onValueChange = { obsidianVault = it },
            label = { Text("Obsidian 仓库名称 (用于点击笔记直接呼起)") },
            placeholder = { Text("例如: MyVault") },
            supportingText = { Text("若填写，点击桌面小部件上的日程将以 obsidian://open 协议秒开该笔记") },
            modifier = Modifier.fillMaxWidth()
        )

        // 3. 每周起始日 / First Day of Week
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
                    folderPath = folderPath.trim(),
                    obsidianVaultName = obsidianVault.trim(),
                    weekStartsOn = weekStartsOn,
                    defaultStartHour = defaultStartHour.toIntOrNull() ?: 8,
                    startTimeProp = startTimeProp.trim(),
                    endTimeProp = endTimeProp.trim(),
                    taskStartKey = taskStartKey.trim(),
                    taskEndKey = taskEndKey.trim(),
                    colorGroupProp = colorGroupProp.trim(),
                    displayProp = displayProp.trim(),
                    displayFields = displayFields.trim(),
                    showContent = showContent
                )
                storageManager.saveSettings(newSettings)
                Toast.makeText(context, "配置已保存，正在重新扫描...", Toast.LENGTH_SHORT).show()
                onSettingsSaved()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("保存并重新扫描数据")
        }
    }
}

