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
 * 桌面组件 2：当日任务组件 (支持自适应缩放与直接勾选完成)
 * Desktop Widget 2: Today Tasks Widget (Supports exact responsive sizing, completion toggle, and Obsidian launch)
 */
class TodayTasksWidget : GlanceAppWidget() {

    // 启用精确尺寸模式，支持用户在桌面自由拉伸缩放尺寸 (2x2, 3x2, 4x2, 4x3, 4x4)
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (_, tasks, _) = storageManager.scanVault()

        val overdueTasks = tasks.filter { it.isOverdue && !it.isCompleted }
        val doingTasks = tasks.filter { !it.isOverdue && !it.isCompleted }

        provideContent {
            TodayTasksWidgetContent(
                context = context,
                overdueTasks = overdueTasks,
                doingTasks = doingTasks,
                totalTasksCount = tasks.size,
                storageManager = storageManager
            )
        }
    }

    @Composable
    private fun TodayTasksWidgetContent(
        context: Context,
        overdueTasks: List<TaskItem>,
        doingTasks: List<TaskItem>,
        totalTasksCount: Int,
        storageManager: StorageManager
    ) {
        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF181824))
                .cornerRadius(16.dp)
                .padding(10.dp)
        ) {
            // 顶部表头
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日待办",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Text(
                    text = "打开待办 ↗",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9575CD)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.clickable(actionStartActivity(mainActivityIntent))
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (overdueTasks.isEmpty() && doingTasks.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyMsg = if (totalTasksCount > 0) "今日待办已全部搞定 🎉" else "暂未扫描到待办文件\n请在设置中配置路径与权限"
                    Text(
                        text = emptyMsg,
                        style = TextStyle(color = ColorProvider(Color(0xFF81C784)), fontSize = 11.sp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                ) {
                    // 1. 超期任务展示
                    if (overdueTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "⚠️ 超期 (${overdueTasks.size})",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFFF5252)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier.padding(vertical = 2.dp)
                            )
                        }
                        items(overdueTasks) { task ->
                            TaskWidgetRow(task = task, isOverdue = true, storageManager = storageManager)
                        }
                    }

                    // 2. 今日待办展示
                    if (doingTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "📌 今日待办 (${doingTasks.size})",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF64B5F6)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(doingTasks) { task ->
                            TaskWidgetRow(task = task, isOverdue = false, storageManager = storageManager)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskWidgetRow(
        task: TaskItem,
        isOverdue: Boolean,
        storageManager: StorageManager
    ) {
        val openObsidianIntent = storageManager.createOpenObsidianIntent(task.path)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(Color(0xFF262738))
                .cornerRadius(6.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 点击方块标记完成 / Toggle complete
            Box(
                modifier = GlanceModifier
                    .size(20.dp)
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
                    style = TextStyle(color = ColorProvider(Color(0xFFB0BEC5)), fontSize = 12.sp)
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // 点击任务名称：直达 Obsidian 打开该笔记 / Clicking note opens Obsidian directly
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openObsidianIntent))
            ) {
                Text(
                    text = task.name,
                    style = TextStyle(
                        color = ColorProvider(if (isOverdue) Color(0xFFFF8A80) else Color.White),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                task.explicitEnd?.let { endTime ->
                    Text(
                        text = "截止: ${CalendarUtils.formatDate(Date(endTime))}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF90A4AE)),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Text(
                text = "↗",
                style = TextStyle(color = ColorProvider(Color(0xFF9575CD)), fontSize = 12.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(openObsidianIntent))
            )
        }
    }
}

/**
 * 桌面微件点击勾选完成回调
 * Widget Checkbox Action Callback
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
            TodayTasksWidget().update(context, glanceId)
        }
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}
