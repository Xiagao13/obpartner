package com.obpartner.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.obpartner.app.MainActivity
import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.TaskItem
import com.obpartner.app.parser.MarkdownWriter
import java.io.File
import java.util.Date

/**
 * 待办桌面组件公共视图 (1:1 对齐 Freepace TaskBlock 风格)
 */
object TodayTasksWidgetShared {

    @Composable
    fun TaskHeader(
        title: String,
        countText: String,
        onOpenIntent: Intent
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (countText.isNotBlank()) {
                Text(
                    text = " $countText",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF80CBC4)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }

            Text(
                text = "打开待办 ↗",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9575CD)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(onOpenIntent))
            )
        }
    }

    @Composable
    fun TaskItemRow(
        task: TaskItem,
        isOverdue: Boolean,
        storageManager: StorageManager,
        compact: Boolean = false
    ) {
        val openObsidianIntent = storageManager.createOpenObsidianIntent(task.path)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(Color(0xFF242538))
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 点击方块一键完成 / Toggle complete
            Box(
                modifier = GlanceModifier
                    .size(if (compact) 18.dp else 22.dp)
                    .background(Color(0xFF3F4158))
                    .cornerRadius(4.dp)
                    .clickable(
                        actionRunCallback<ToggleTaskActionCallback>(
                            actionParametersOf(ToggleTaskActionCallback.taskPathKey to task.path)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "○",
                    style = TextStyle(color = ColorProvider(Color(0xFFB0BEC5)), fontSize = if (compact) 10.sp else 12.sp)
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // 任务名称与截止时间，点击直达 Obsidian 打开该笔记
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openObsidianIntent))
            ) {
                Text(
                    text = task.name,
                    style = TextStyle(
                        color = ColorProvider(if (isOverdue) Color(0xFFFF8A80) else Color.White),
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                task.explicitEnd?.let { endTime ->
                    Text(
                        text = "截止: ${CalendarUtils.formatDate(Date(endTime))}",
                        style = TextStyle(
                            color = ColorProvider(if (isOverdue) Color(0xFFFF5252) else Color(0xFF90A4AE)),
                            fontSize = 9.sp
                        )
                    )
                }
            }

            Text(
                text = "↗",
                style = TextStyle(color = ColorProvider(Color(0xFF9575CD)), fontSize = 11.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(openObsidianIntent))
            )
        }
    }
}

/**
 * 桌面组件 2 (4×2 列表版)
 */
class TodayTasksWidget4x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (_, tasks, _) = storageManager.scanVault()

        val overdueTasks = tasks.filter { it.isOverdue && !it.isCompleted }
        val doingTasks = tasks.filter { !it.isOverdue && !it.isCompleted }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF181824))
                    .cornerRadius(16.dp)
                    .padding(8.dp)
            ) {
                TodayTasksWidgetShared.TaskHeader(
                    title = "今日待办",
                    countText = "(${doingTasks.size + overdueTasks.size})",
                    onOpenIntent = mainIntent
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                if (overdueTasks.isEmpty() && doingTasks.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tasks.isNotEmpty()) "今日待办已全部搞定 🎉" else "暂未扫描到待办文件",
                            style = TextStyle(color = ColorProvider(Color(0xFF81C784)), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        if (overdueTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "⚠️ 超期任务 (${overdueTasks.size})",
                                    style = TextStyle(color = ColorProvider(Color(0xFFFF5252)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.padding(vertical = 1.dp)
                                )
                            }
                            items(overdueTasks) { task ->
                                TodayTasksWidgetShared.TaskItemRow(task = task, isOverdue = true, storageManager = storageManager)
                            }
                        }

                        if (doingTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📌 进行中 (${doingTasks.size})",
                                    style = TextStyle(color = ColorProvider(Color(0xFF64B5F6)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.padding(top = 3.dp, bottom = 1.dp)
                                )
                            }
                            items(doingTasks) { task ->
                                TodayTasksWidgetShared.TaskItemRow(task = task, isOverdue = false, storageManager = storageManager)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面组件 2 (2×2 方块版 - 精简看板)
 */
class TodayTasksWidget2x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (_, tasks, _) = storageManager.scanVault()

        val overdueTasks = tasks.filter { it.isOverdue && !it.isCompleted }
        val doingTasks = tasks.filter { !it.isOverdue && !it.isCompleted }
        val completedTasks = tasks.filter { it.isCompleted }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val urgentTasks = (overdueTasks + doingTasks).take(3)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF181824))
                    .cornerRadius(16.dp)
                    .padding(8.dp)
            ) {
                // 顶部计数概览
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日待办",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "${completedTasks.size}/${tasks.size}",
                        style = TextStyle(color = ColorProvider(Color(0xFF81C784)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.clickable(actionStartActivity(mainIntent))
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                if (urgentTasks.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tasks.isNotEmpty()) "全部搞定 🎉" else "暂无待办",
                            style = TextStyle(color = ColorProvider(Color(0xFF81C784)), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(urgentTasks) { task ->
                            TodayTasksWidgetShared.TaskItemRow(
                                task = task,
                                isOverdue = task.isOverdue,
                                storageManager = storageManager,
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 默认待办组件
 */
class TodayTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        TodayTasksWidget4x2().provideGlance(context, id)
    }
}

/**
 * 桌面微件点击勾选完成回调
 */
class ToggleTaskActionCallback : ActionCallback {
    companion object {
        val taskPathKey = ActionParameters.Key<String>("task_path")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val path = parameters[taskPathKey] ?: return
        val file = File(path)
        if (file.exists()) {
            MarkdownWriter.updateTaskStatus(file, "Done")
            TodayTasksWidget4x2().update(context, glanceId)
            TodayTasksWidget2x2().update(context, glanceId)
            TodayTasksWidget().update(context, glanceId)
        }
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}

class TodayTasksWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget4x2()
}

class TodayTasksWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget2x2()
}

