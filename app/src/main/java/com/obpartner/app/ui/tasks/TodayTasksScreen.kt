package com.obpartner.app.ui.tasks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.HabitItem
import com.obpartner.app.model.TaskItem
import com.obpartner.app.parser.MarkdownWriter
import com.obpartner.app.ui.theme.*
import java.io.File
import java.util.Date

/**
 * 今日面板界面 (对齐 Freepace TaskBlock：超期、进行中、已完成、习惯打卡，并支持桌面交互与直达 Obsidian)
 * Today's Panel Screen (Aligned with Freepace TaskBlock: Overdue, Doing, Completed, Habits)
 */
@Composable
fun TodayTasksScreen(
    tasks: List<TaskItem>,
    habits: List<HabitItem>,
    onTaskUpdated: () -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }
    val todayStr = remember { CalendarUtils.formatDate(Date()) }

    // 分组：超期、今日进行中、已完成
    val overdueTasks = remember(tasks) { tasks.filter { it.isOverdue && !it.isCompleted } }
    val doingTasks = remember(tasks) { tasks.filter { !it.isOverdue && !it.isCompleted } }
    val completedTasks = remember(tasks) { tasks.filter { it.isCompleted } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 习惯打卡板块 (Habit Tracker)
        if (habits.isNotEmpty()) {
            item {
                Text(
                    text = "习惯打卡",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(habits) { habit ->
                        HabitCard(
                            habit = habit,
                            onCheckIn = {
                                val file = File(habit.path)
                                if (MarkdownWriter.checkInHabit(file, todayStr)) {
                                    Toast.makeText(context, "打卡成功: ${habit.name}", Toast.LENGTH_SHORT).show()
                                    onTaskUpdated()
                                }
                            },
                            onClick = {
                                storageManager.createOpenObsidianIntent(habit.path, habit.vaultRelativePath).let { context.startActivity(it) }
                            }
                        )
                    }
                }
            }
        }

        // 2. 超期任务 (Overdue Tasks)
        if (overdueTasks.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = OverdueRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "超期 (${overdueTasks.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OverdueRed
                    )
                }
            }
            items(overdueTasks) { task ->
                TaskCardItem(
                    task = task,
                    onStatusChange = { isDone ->
                        val newStatus = if (isDone) "Done" else "Todo"
                        if (MarkdownWriter.updateTaskStatus(File(task.path), newStatus)) {
                            onTaskUpdated()
                        }
                    },
                    onClick = {
                        storageManager.createOpenObsidianIntent(task.path, task.vaultRelativePath).let { context.startActivity(it) }
                    }
                )
            }
        }

        // 3. 今日进行中任务 (Doing Tasks)
        item {
            Text(
                text = "今日待办 (${doingTasks.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoingBlue
            )
        }
        if (doingTasks.isEmpty()) {
            item {
                Text(
                    text = "今日无待办任务 🎉",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(doingTasks) { task ->
                TaskCardItem(
                    task = task,
                    onStatusChange = { isDone ->
                        val newStatus = if (isDone) "Done" else "Todo"
                        if (MarkdownWriter.updateTaskStatus(File(task.path), newStatus)) {
                            onTaskUpdated()
                        }
                    },
                    onClick = {
                        storageManager.createOpenObsidianIntent(task.path, task.vaultRelativePath).let { context.startActivity(it) }
                    }
                )
            }
        }

        // 4. 已完成任务 (Completed Tasks)
        if (completedTasks.isNotEmpty()) {
            item {
                Text(
                    text = "已完成 (${completedTasks.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoneGreen
                )
            }
            items(completedTasks) { task ->
                TaskCardItem(
                    task = task,
                    onStatusChange = { isDone ->
                        val newStatus = if (isDone) "Done" else "Todo"
                        if (MarkdownWriter.updateTaskStatus(File(task.path), newStatus)) {
                            onTaskUpdated()
                        }
                    },
                    onClick = {
                        storageManager.createOpenObsidianIntent(task.path, task.vaultRelativePath).let { context.startActivity(it) }
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskCardItem(
    task: TaskItem,
    onStatusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onStatusChange(it) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 优先级标签 (P1/P2/P3)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (task.priority) {
                                    "P1" -> OverdueRed.copy(alpha = 0.2f)
                                    "P2" -> DoingBlue.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = task.priority,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (task.priority) {
                                "P1" -> OverdueRed
                                "P2" -> DoingBlue
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // 截止日期
                    task.explicitEnd?.let { endTime ->
                        Text(
                            text = "截止: ${CalendarUtils.formatDate(Date(endTime))}",
                            fontSize = 11.sp,
                            color = if (task.isOverdue) OverdueRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = "Obsidian ↗",
                fontSize = 11.sp,
                color = AccentPrimary
            )
        }
    }
}

@Composable
private fun HabitCard(
    habit: HabitItem,
    onCheckIn: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (habit.isDoneToday) DoneGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = habit.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "连续 ${habit.streak} 天",
                fontSize = 11.sp,
                color = HabitPurple,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onCheckIn() },
                enabled = !habit.isDoneToday,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (habit.isDoneToday) DoneGreen else AccentPrimary
                )
            ) {
                if (habit.isDoneToday) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("已打卡", fontSize = 11.sp)
                } else {
                    Text("打卡", fontSize = 11.sp)
                }
            }
        }
    }
}
