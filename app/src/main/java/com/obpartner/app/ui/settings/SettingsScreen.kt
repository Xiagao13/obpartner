package com.obpartner.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.AppSettings

/**
 * 设置界面 (配置文件夹路径、Obsidian 库名、周起始日及属性映射)
 * Settings Screen (Configure folder paths, Obsidian vault name, week start day and property keys)
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

        // 1. 文件夹路径 / Folder Paths
        OutlinedTextField(
            value = folderPath,
            onValueChange = { folderPath = it },
            label = { Text("Markdown 文件夹路径 (支持英文逗号分隔)") },
            placeholder = { Text("/sdcard/Documents/ObsidianVault/Daily") },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Obsidian 仓库名 (用于一键直达跳转) / Obsidian Vault Name
        OutlinedTextField(
            value = obsidianVault,
            onValueChange = { obsidianVault = it },
            label = { Text("Obsidian 仓库名称 (用于点击笔记直接呼起)") },
            placeholder = { Text("例如: MyVault") },
            supportingText = { Text("若填写，点击桌面或应用中的日程将以 obsidian://open 协议极速呼起") },
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
                    taskEndKey = taskEndKey.trim()
                )
                storageManager.saveSettings(newSettings)
                Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                onSettingsSaved()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("保存并重新扫描数据")
        }
    }
}
