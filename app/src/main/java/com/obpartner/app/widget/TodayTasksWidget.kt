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
import com.obpartner.app.calendar.ColorUtils
import com.obpartner.app.calendar.WidgetThemeConfig
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.TaskItem
import com.obpartner.app.parser.MarkdownWriter
import java.io.File
import java.util.Date

/**
 * 待办桌面组件公共视图 (支持 3×2/2×2 及柔光磨砂玻璃多主题)
 * Today Tasks Widget Shared Views (Supports 3×2, 2×2 & Glassmorphism themes)
 */
object TodayTasksWidgetShared {

    /**
     * 柔光玻璃拟态微件外层容器
     * Frosted Glassmorphism Outer Container
     */
    @Composable
    fun GlassContainer(
        theme: WidgetThemeConfig,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(theme.glassBorder)
                .cornerRadius(18.dp)
                .padding(1.dp)
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(theme.widgetBg)
                    .cornerRadius(17.dp)
                    .padding(8.dp),
                content = content
            )
        }
    }

    /**
     * 待办通用头部
     * Task Header
     */
    @Composable
    fun TaskHeader(
        title: String,
        countText: String,
        theme: WidgetThemeConfig,
        onOpenIntent: Intent
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(theme.headerText),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (countText.isNotBlank()) {
                Text(
                    text = " $countText",
                    style = TextStyle(
                        color = ColorProvider(theme.highlight),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }

            Text(
                text = "Obsidian ↗",
                style = TextStyle(
                    color = ColorProvider(theme.accent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(onOpenIntent))
            )
        }
    }

    /**
     * 单项待办卡片行 (一键勾选完成 + 点击标题直达 Obsidian)
     * Task Item Row
     */
    @Composable
    fun TaskItemRow(
        task: TaskItem,
        isOverdue: Boolean,
        theme: WidgetThemeConfig,
        storageManager: StorageManager,
        compact: Boolean = false
    ) {
        val openObsidianIntent = storageManager.createOpenObsidianIntent(task.path, task.vaultRelativePath)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(theme.cardBg)
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 点击圆圈一键完成 / Toggle complete
            Box(
                modifier = GlanceModifier
                    .size(if (compact) 18.dp else 22.dp)
                    .background(if (theme.id == "light") Color(0x206366F1) else Color(0x33FFFFFF))
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
                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = if (compact) 10.sp else 12.sp)
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
                        color = ColorProvider(if (isOverdue) Color(0xFFFF8A80) else theme.headerText),
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                task.explicitEnd?.let { endTime ->
                    Text(
                        text = "截止: ${CalendarUtils.formatDate(Date(endTime))}",
                        style = TextStyle(
                            color = ColorProvider(if (isOverdue) Color(0xFFFF5252) else theme.subText),
                            fontSize = 9.sp
                        )
                    )
                }
            }

            Text(
                text = "↗",
                style = TextStyle(color = ColorProvider(theme.accent), fontSize = 10.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(openObsidianIntent))
            )
        }
    }
}

/**
 * 桌面待办组件 1 (3×2 横版主推 - 适配主流 5 列网格，完美居中)
 * 3x2 Today Tasks Widget (Primary horizontal layout)
 */
class TodayTasksWidget3x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        var tasks = storageManager.getCachedTasksFast()
        if (tasks.isEmpty()) {
            val (_, freshTasks, _) = storageManager.scanVault()
            tasks = freshTasks
        }
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)

        val overdueTasks = tasks.filter { it.isOverdue && !it.isCompleted }
        val doingTasks = tasks.filter { !it.isOverdue && !it.isCompleted }
        val completedTasks = tasks.filter { it.isCompleted }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            TodayTasksWidgetShared.GlassContainer(theme = theme) {
                TodayTasksWidgetShared.TaskHeader(
                    title = "今日待办",
                    countText = "${completedTasks.size}/${tasks.size}",
                    theme = theme,
                    onOpenIntent = mainIntent
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                if (overdueTasks.isEmpty() && doingTasks.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tasks.isNotEmpty()) "待办已全部搞定 🎉" else "暂未扫描到待办文件",
                            style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        if (overdueTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "⚠️ 超期 (${overdueTasks.size})",
                                    style = TextStyle(color = ColorProvider(Color(0xFFFF5252)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.padding(vertical = 1.dp)
                                )
                            }
                            items(overdueTasks) { task ->
                                TodayTasksWidgetShared.TaskItemRow(task = task, isOverdue = true, theme = theme, storageManager = storageManager)
                            }
                        }

                        if (doingTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📌 进行中 (${doingTasks.size})",
                                    style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.padding(top = 2.dp, bottom = 1.dp)
                                )
                            }
                            items(doingTasks) { task ->
                                TodayTasksWidgetShared.TaskItemRow(task = task, isOverdue = false, theme = theme, storageManager = storageManager)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面待办组件 2 (2×2 方块精简版 - 紧凑看板)
 * 2x2 Today Tasks Widget (Compact square layout)
 */
class TodayTasksWidget2x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        var tasks = storageManager.getCachedTasksFast()
        if (tasks.isEmpty()) {
            val (_, freshTasks, _) = storageManager.scanVault()
            tasks = freshTasks
        }
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)

        val overdueTasks = tasks.filter { it.isOverdue && !it.isCompleted }
        val doingTasks = tasks.filter { !it.isOverdue && !it.isCompleted }
        val completedTasks = tasks.filter { it.isCompleted }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val urgentTasks = (overdueTasks + doingTasks).take(3)

            TodayTasksWidgetShared.GlassContainer(theme = theme) {
                // 顶部计数概览
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "待办",
                        style = TextStyle(color = ColorProvider(theme.headerText), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "${completedTasks.size}/${tasks.size}",
                        style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 11.sp, fontWeight = FontWeight.Bold),
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
                            style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(urgentTasks) { task ->
                            TodayTasksWidgetShared.TaskItemRow(
                                task = task,
                                isOverdue = task.isOverdue,
                                theme = theme,
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
 * 桌面待办组件 3 (4×2 宽版兼容版)
 */
class TodayTasksWidget4x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        var tasks = storageManager.getCachedTasksFast()
        if (tasks.isEmpty()) {
            val (_, freshTasks, _) = storageManager.scanVault()
            tasks = freshTasks
        }
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)

        val overdueTasks = tasks.filter { it.isOverdue && !it.isCompleted }
        val doingTasks = tasks.filter { !it.isOverdue && !it.isCompleted }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            TodayTasksWidgetShared.GlassContainer(theme = theme) {
                TodayTasksWidgetShared.TaskHeader(
                    title = "今日待办",
                    countText = "(${doingTasks.size + overdueTasks.size})",
                    theme = theme,
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
                            style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 11.sp)
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
                                TodayTasksWidgetShared.TaskItemRow(task = task, isOverdue = true, theme = theme, storageManager = storageManager)
                            }
                        }

                        if (doingTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📌 进行中 (${doingTasks.size})",
                                    style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.padding(top = 3.dp, bottom = 1.dp)
                                )
                            }
                            items(doingTasks) { task ->
                                TodayTasksWidgetShared.TaskItemRow(task = task, isOverdue = false, theme = theme, storageManager = storageManager)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 默认待办组件 (指向 3×2 横版主推)
 */
class TodayTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        TodayTasksWidget3x2().provideGlance(context, id)
    }
}

/**
 * 桌面微件点击勾选完成回调
 * Toggle Task Action Callback
 */
class ToggleTaskActionCallback : ActionCallback {
    companion object {
        val taskPathKey = ActionParameters.Key<String>("task_path")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val path = parameters[taskPathKey] ?: return
        val storageManager = StorageManager(context)
        storageManager.updateSingleTaskStatus(path, "Done")
    }
}

// 广播接收器注册 / Broadcast Receivers
class TodayTasksWidget3x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget3x2()
}

class TodayTasksWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget2x2()
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}

class TodayTasksWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget4x2()
}
