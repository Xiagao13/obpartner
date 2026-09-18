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
import com.obpartner.app.calendar.LunarHelper
import com.obpartner.app.calendar.WidgetThemeConfig
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.CalendarEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * 桌面日历微件交互回调 (支持翻周翻月、重置今天、周/月视图切换与日期筛选)
 * Desktop Calendar Widget Navigation Callback (Supports week/month paging, today reset, view toggle & date filter)
 */
class CalendarNavActionCallback : ActionCallback {
    companion object {
        val actionKey = ActionParameters.Key<String>("nav_action")
        val viewModeKey = ActionParameters.Key<String>("nav_view_mode")
        val dateTimestampKey = ActionParameters.Key<Long>("nav_date_timestamp")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[actionKey] ?: return
        val prefs = context.getSharedPreferences("widget_calendar_state", Context.MODE_PRIVATE)
        val idStr = glanceId.toString()

        when (action) {
            "nav_prev" -> {
                val currentMode = prefs.getString("${idStr}_view_mode", "week") ?: "week"
                if (currentMode == "month") {
                    val current = prefs.getInt("${idStr}_month_offset", 0)
                    prefs.edit().putInt("${idStr}_month_offset", current - 1).apply()
                } else {
                    val current = prefs.getInt("${idStr}_week_offset", 0)
                    prefs.edit().putInt("${idStr}_week_offset", current - 1).apply()
                }
            }
            "nav_next" -> {
                val currentMode = prefs.getString("${idStr}_view_mode", "week") ?: "week"
                if (currentMode == "month") {
                    val current = prefs.getInt("${idStr}_month_offset", 0)
                    prefs.edit().putInt("${idStr}_month_offset", current + 1).apply()
                } else {
                    val current = prefs.getInt("${idStr}_week_offset", 0)
                    prefs.edit().putInt("${idStr}_week_offset", current + 1).apply()
                }
            }
            "reset_today" -> {
                prefs.edit()
                    .putInt("${idStr}_week_offset", 0)
                    .putInt("${idStr}_month_offset", 0)
                    .remove("${idStr}_selected_day")
                    .apply()
            }
            "set_view" -> {
                val mode = parameters[viewModeKey] ?: "week"
                prefs.edit().putString("${idStr}_view_mode", mode).apply()
            }
            "select_day" -> {
                val timestamp = parameters[dateTimestampKey] ?: 0L
                if (timestamp > 0L) {
                    prefs.edit().putLong("${idStr}_selected_day", timestamp).apply()
                } else {
                    prefs.edit().remove("${idStr}_selected_day").apply()
                }
            }
        }

        // 刷新微件
        try { CalendarWidget2x3().update(context, glanceId) } catch (_: Exception) {}
        try { CalendarWidget3x2().update(context, glanceId) } catch (_: Exception) {}
        try { CalendarWidget2x2().update(context, glanceId) } catch (_: Exception) {}
    }
}

/**
 * 桌面日历微件公共组件与视图体系
 * Calendar Widgets Shared Views & Components
 */
object CalendarWidgetShared {

    /**
     * 柔光玻璃拟态微件外层容器 (双层边框模拟真实磨砂玻璃高光微边)
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
                .cornerRadius(16.dp)
                .padding(1.dp)
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(theme.widgetBg)
                    .cornerRadius(15.dp)
                    .padding(8.dp),
                content = content
            )
        }
    }

    /**
     * 交互式工具栏头部 (1:1 复刻 Freepace 日历块顶栏：上一周/月、今天、下一周/月、标题与周/月视图切换)
     * Interactive Navigation Header
     */
    @Composable
    fun NavHeader(
        title: String,
        subTitle: String = "",
        viewMode: String,
        theme: WidgetThemeConfig,
        onOpenAppIntent: Intent,
        showNavButtons: Boolean = true,
        showViewSwitcher: Boolean = true
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showNavButtons) {
                // 上一周 / 上一月
                Text(
                    text = "◀",
                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .clickable(
                            actionRunCallback<CalendarNavActionCallback>(
                                actionParametersOf(CalendarNavActionCallback.actionKey to "nav_prev")
                            )
                        )
                )
                // 回到今天
                Text(
                    text = "今",
                    style = TextStyle(color = ColorProvider(theme.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .clickable(
                            actionRunCallback<CalendarNavActionCallback>(
                                actionParametersOf(CalendarNavActionCallback.actionKey to "reset_today")
                            )
                        )
                )
                // 下一周 / 下一月
                Text(
                    text = "▶",
                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .clickable(
                            actionRunCallback<CalendarNavActionCallback>(
                                actionParametersOf(CalendarNavActionCallback.actionKey to "nav_next")
                            )
                        )
                )
                Spacer(modifier = GlanceModifier.width(3.dp))
            }

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(theme.headerText),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                if (subTitle.isNotBlank()) {
                    Text(
                        text = subTitle,
                        style = TextStyle(
                            color = ColorProvider(theme.subText),
                            fontSize = 8.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (showViewSwitcher) {
                // 周 / 月 视图切换胶囊
                Row(
                    modifier = GlanceModifier
                        .background(theme.cardBg)
                        .cornerRadius(4.dp)
                        .padding(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "周",
                        style = TextStyle(
                            color = ColorProvider(if (viewMode == "week") Color.White else theme.subText),
                            fontSize = 9.sp,
                            fontWeight = if (viewMode == "week") FontWeight.Bold else FontWeight.Normal
                        ),
                        modifier = GlanceModifier
                            .background(if (viewMode == "week") theme.accent else Color.Transparent)
                            .cornerRadius(3.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clickable(
                                actionRunCallback<CalendarNavActionCallback>(
                                    actionParametersOf(
                                        CalendarNavActionCallback.actionKey to "set_view",
                                        CalendarNavActionCallback.viewModeKey to "week"
                                    )
                                )
                            )
                    )
                    Text(
                        text = "月",
                        style = TextStyle(
                            color = ColorProvider(if (viewMode == "month") Color.White else theme.subText),
                            fontSize = 9.sp,
                            fontWeight = if (viewMode == "month") FontWeight.Bold else FontWeight.Normal
                        ),
                        modifier = GlanceModifier
                            .background(if (viewMode == "month") theme.accent else Color.Transparent)
                            .cornerRadius(3.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clickable(
                                actionRunCallback<CalendarNavActionCallback>(
                                    actionParametersOf(
                                        CalendarNavActionCallback.actionKey to "set_view",
                                        CalendarNavActionCallback.viewModeKey to "month"
                                    )
                                )
                            )
                    )
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
            }

            Text(
                text = "↗",
                style = TextStyle(
                    color = ColorProvider(theme.accent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(onOpenAppIntent))
            )
        }
    }

    /**
     * 7天周胶囊指示条 (点击具体某天可直接高亮并筛选当日日程)
     * Weekdays Capsule Bar (Click to filter events of selected day)
     */
    @Composable
    fun WeekdaysBar(
        weekDates: List<Date>,
        today: Date,
        selectedDate: Date?,
        events: List<CalendarEvent>,
        theme: WidgetThemeConfig
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(theme.cardBg)
                .cornerRadius(6.dp)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dayFmt = SimpleDateFormat("d", Locale.getDefault())
            val weekFmt = SimpleDateFormat("E", Locale.CHINESE)

            weekDates.forEach { date ->
                val isToday = CalendarUtils.isSameDay(date, today)
                val isSelected = selectedDate != null && CalendarUtils.isSameDay(date, selectedDate)
                val hasEvents = events.any { CalendarUtils.isSameDay(Date(it.start), date) }
                val dateLunar = LunarHelper.getLunarDetails(date)

                val highlightBg = when {
                    isSelected -> theme.accent
                    isToday -> theme.accent.copy(alpha = 0.45f)
                    else -> Color.Transparent
                }

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp)
                        .background(highlightBg)
                        .cornerRadius(4.dp)
                        .clickable(
                            actionRunCallback<CalendarNavActionCallback>(
                                actionParametersOf(
                                    CalendarNavActionCallback.actionKey to "select_day",
                                    CalendarNavActionCallback.dateTimestampKey to if (isSelected) 0L else date.time
                                )
                            )
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekFmt.format(date),
                        style = TextStyle(
                            color = ColorProvider(if (isSelected || isToday) Color.White else theme.subText),
                            fontSize = 8.sp
                        )
                    )
                    Text(
                        text = dayFmt.format(date),
                        style = TextStyle(
                            color = ColorProvider(if (isSelected || isToday) Color.White else theme.headerText),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    if (hasEvents) {
                        Text(
                            text = "•",
                            style = TextStyle(
                                color = ColorProvider(if (isSelected) Color.White else theme.highlight),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    } else {
                        dateLunar.holidayStatus?.let { status ->
                            Text(
                                text = if (status == "work") "班" else "休",
                                style = TextStyle(
                                    color = ColorProvider(if (status == "work") theme.subText else Color(0xFFFF5252)),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } ?: Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }

    /**
     * 7×6 完整月历网格视图 (含星期列头、42格日期、今日高亮与日程打点)
     * 7x6 Full Month Grid View
     */
    @Composable
    fun MonthViewGrid(
        cells: List<CalendarUtils.MonthCell>,
        today: Date,
        selectedDate: Date?,
        events: List<CalendarEvent>,
        theme: WidgetThemeConfig
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                weekLabels.forEach { label ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = TextStyle(color = ColorProvider(theme.subText), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            val dayFmt = SimpleDateFormat("d", Locale.getDefault())
            for (row in 0 until 6) {
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        if (index < cells.size) {
                            val cell = cells[index]
                            val isToday = cell.isToday
                            val isSelected = selectedDate != null && CalendarUtils.isSameDay(cell.date, selectedDate)
                            val hasEvents = events.any { CalendarUtils.isSameDay(Date(it.start), cell.date) }

                            val cellBg = when {
                                isSelected -> theme.accent
                                isToday -> theme.accent.copy(alpha = 0.45f)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                isSelected || isToday -> Color.White
                                cell.isCurrentMonth -> theme.headerText
                                else -> theme.subText.copy(alpha = 0.4f)
                            }

                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .background(cellBg)
                                    .cornerRadius(3.dp)
                                    .padding(vertical = 1.dp)
                                    .clickable(
                                        actionRunCallback<CalendarNavActionCallback>(
                                            actionParametersOf(
                                                CalendarNavActionCallback.actionKey to "select_day",
                                                CalendarNavActionCallback.dateTimestampKey to if (isSelected) 0L else cell.date.time
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayFmt.format(cell.date),
                                        style = TextStyle(
                                            color = ColorProvider(textColor),
                                            fontSize = 9.sp,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    if (hasEvents) {
                                        Box(
                                            modifier = GlanceModifier
                                                .size(3.dp)
                                                .background(if (isSelected) Color.White else theme.highlight)
                                                .cornerRadius(1.5.dp)
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 单条日程行渲染 (彩色粗指示条，区分跨年完整日期展示，点击直达 Obsidian)
     * Single Schedule Row
     */
    @Composable
    fun EventItemRow(
        event: CalendarEvent,
        theme: WidgetThemeConfig,
        storageManager: StorageManager,
        showExtraFields: Boolean = false,
        compact: Boolean = false
    ) {
        val openObsidianIntent = storageManager.createOpenObsidianIntent(event.path)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val eventCal = Calendar.getInstance().apply { time = Date(event.start) }
        val isDiffYear = eventCal.get(Calendar.YEAR) != currentYear
        val dateFormatPattern = if (isDiffYear) "yyyy-MM-dd HH:mm" else "MM-dd HH:mm"
        val eventDateStr = SimpleDateFormat(dateFormatPattern, Locale.getDefault()).format(Date(event.start))
        val timeStr = if (event.isAllDay) "全天 ($eventDateStr)" else
            if (compact) (if (isDiffYear) "${eventCal.get(Calendar.YEAR)}-" else "") + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(event.start))
            else "$eventDateStr - ${CalendarUtils.formatTime(Date(event.end))}"

        val isDarkTheme = theme.id != "light"
        val accentColor = ColorUtils.stringToColor(event.colorValue, isDark = isDarkTheme, mode = "border")
        val displayTitle = if (event.displayText.isNotBlank()) event.displayText else event.title

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(theme.cardBg)
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = if (compact) 3.dp else 5.dp)
                .clickable(actionStartActivity(openObsidianIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(3.5.dp)
                    .height(if (showExtraFields) 34.dp else if (compact) 20.dp else 24.dp)
                    .background(accentColor)
                    .cornerRadius(2.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(6.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = displayTitle,
                    style = TextStyle(
                        color = ColorProvider(theme.headerText),
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = timeStr,
                    style = TextStyle(
                        color = ColorProvider(theme.subText),
                        fontSize = 9.sp
                    )
                )

                if (showExtraFields) {
                    val settings = storageManager.getSettings()
                    val fieldKeys = settings.displayFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val extraText = fieldKeys.mapNotNull { key ->
                        val v = event.extraData[key]?.toString()
                        if (!v.isNullOrBlank() && v != displayTitle) "$key: $v" else null
                    }.joinToString("  •  ")

                    if (extraText.isNotBlank()) {
                        Text(
                            text = extraText,
                            style = TextStyle(
                                color = ColorProvider(theme.highlight),
                                fontSize = 9.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = "↗",
                style = TextStyle(
                    color = ColorProvider(theme.accent),
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * 桌面日历微件 1 (日历·周日程流 - 2×3 竖版，结构对齐周视图，支持翻周翻月与周/月视图无缝切换)
 * 2x3 Calendar Widget (Structure aligned with WeekView, supports week/month paging & view toggle)
 */
class CalendarWidget2x3 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.getCachedVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()

        // 读取持久化交互状态 (当前周/月偏移量、当前视图模式、选中日期)
        val prefs = context.getSharedPreferences("widget_calendar_state", Context.MODE_PRIVATE)
        val idStr = id.toString()
        val viewMode = prefs.getString("${idStr}_view_mode", "week") ?: "week"
        val weekOffset = prefs.getInt("${idStr}_week_offset", 0)
        val monthOffset = prefs.getInt("${idStr}_month_offset", 0)
        val selectedDayTs = prefs.getLong("${idStr}_selected_day", 0L)
        val selectedDate = if (selectedDayTs > 0L) Date(selectedDayTs) else null

        // 目标周与日期范围
        val targetWeekCal = Calendar.getInstance().apply {
            time = today
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }
        val weekDates = CalendarUtils.getWeekDates(targetWeekCal.time, settings.weekStartsOn)
        val weekStartTs = Calendar.getInstance().apply {
            time = weekDates.first()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekEndTs = Calendar.getInstance().apply {
            time = weekDates.last()
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        // 目标月份与网格
        val targetMonthCal = Calendar.getInstance().apply {
            time = today
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val monthCells = CalendarUtils.generateMonthCells(targetMonthCal.time, settings.weekStartsOn)

        // 本周内所有日程
        val weekEvents = events.filter { it.start in weekStartTs..weekEndTs }.sortedBy { it.start }

        // 选中某天时的日程
        val dayEvents = if (selectedDate != null) {
            events.filter { CalendarUtils.isSameDay(Date(it.start), selectedDate) }.sortedBy { it.start }
        } else emptyList()

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val weekTitle = if (weekOffset == 0) "本周 (${SimpleDateFormat("M.d", Locale.getDefault()).format(weekDates.first())}-${SimpleDateFormat("M.d", Locale.getDefault()).format(weekDates.last())})"
            else "${SimpleDateFormat("M.d", Locale.getDefault()).format(weekDates.first())}-${SimpleDateFormat("M.d", Locale.getDefault()).format(weekDates.last())} (${if (weekOffset > 0) "+${weekOffset}周" else "${weekOffset}周"})"
            val monthTitle = SimpleDateFormat("yyyy年 M月", Locale.CHINESE).format(targetMonthCal.time)

            CalendarWidgetShared.GlassContainer(theme = theme) {
                // 1. 顶栏：支持 ◀ 今 ▶ 翻周/翻月与 [周] [月] 视图切换
                CalendarWidgetShared.NavHeader(
                    title = if (viewMode == "month") monthTitle else weekTitle,
                    subTitle = if (viewMode == "month") "共 ${events.filter { CalendarUtils.isSameDay(Date(it.start), targetMonthCal.time) }.size} 项日程" else "本周共 ${weekEvents.size} 项日程",
                    viewMode = viewMode,
                    theme = theme,
                    onOpenAppIntent = mainIntent,
                    showNavButtons = true,
                    showViewSwitcher = true
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                if (viewMode == "month") {
                    // --- 月视图模式 ---
                    CalendarWidgetShared.MonthViewGrid(
                        cells = monthCells,
                        today = today,
                        selectedDate = selectedDate,
                        events = events,
                        theme = theme
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    if (selectedDate != null) {
                        Text(
                            text = "${SimpleDateFormat("M月d日 E", Locale.CHINESE).format(selectedDate)} 日程 (${dayEvents.size})",
                            style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        if (dayEvents.isEmpty()) {
                            Text(
                                text = "当日无日程安排 🎉",
                                style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp),
                                modifier = GlanceModifier.padding(vertical = 2.dp)
                            )
                        } else {
                            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                items(dayEvents) { ev ->
                                    CalendarWidgetShared.EventItemRow(event = ev, theme = theme, storageManager = storageManager, compact = true)
                                }
                            }
                        }
                    } else {
                        // 提示点击查看
                        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "点击月历中任意日期可高亮查看日程",
                                style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp)
                            )
                        }
                    }
                } else {
                    // --- 周视图模式 (结构严格对齐周视图) ---
                    CalendarWidgetShared.WeekdaysBar(
                        weekDates = weekDates,
                        today = today,
                        selectedDate = selectedDate,
                        events = events,
                        theme = theme
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    if (selectedDate != null) {
                        // 显示单日日程与返回全部整周
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${SimpleDateFormat("M月d日 E", Locale.CHINESE).format(selectedDate)} · 日程 (${dayEvents.size})",
                                style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "全部 ↩",
                                style = TextStyle(color = ColorProvider(theme.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.clickable(
                                    actionRunCallback<CalendarNavActionCallback>(
                                        actionParametersOf(
                                            CalendarNavActionCallback.actionKey to "select_day",
                                            CalendarNavActionCallback.dateTimestampKey to 0L
                                        )
                                    )
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(2.dp))

                        if (dayEvents.isEmpty()) {
                            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "当日无日程安排 🎉",
                                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp)
                                )
                            }
                        } else {
                            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                items(dayEvents) { ev ->
                                    CalendarWidgetShared.EventItemRow(event = ev, theme = theme, storageManager = storageManager, compact = false)
                                }
                            }
                        }
                    } else {
                        // 显示本周日程流 (按天分组排布，杜绝出现非周视图片段)
                        Text(
                            text = "本周日程流 (${weekEvents.size})",
                            style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))

                        if (weekEvents.isEmpty()) {
                            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "本周暂无日程安排 🎉",
                                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp)
                                )
                            }
                        } else {
                            val weekFmt = SimpleDateFormat("E", Locale.CHINESE)
                            val dayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())

                            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                weekDates.forEach { dayDate ->
                                    val currentDayEvents = weekEvents.filter { CalendarUtils.isSameDay(Date(it.start), dayDate) }
                                    if (currentDayEvents.isNotEmpty()) {
                                        val isCurrentToday = CalendarUtils.isSameDay(dayDate, today)
                                        item {
                                            Text(
                                                text = "${weekFmt.format(dayDate)} ${dayFmt.format(dayDate)}${if (isCurrentToday) " [今天]" else ""} (${currentDayEvents.size})",
                                                style = TextStyle(
                                                    color = ColorProvider(if (isCurrentToday) theme.highlight else theme.subText),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = GlanceModifier.padding(top = 4.dp, bottom = 1.dp)
                                            )
                                        }
                                        items(currentDayEvents) { ev ->
                                            CalendarWidgetShared.EventItemRow(event = ev, theme = theme, storageManager = storageManager, compact = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面日历微件 2 (日历·今日看板 - 3×2 横版，左侧公历农历大看板与快捷控制，右侧日程流)
 * 3x2 Calendar Widget (Horizontal layout with today dashboard and schedule flow)
 */
class CalendarWidget3x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.getCachedVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        val prefs = context.getSharedPreferences("widget_calendar_state", Context.MODE_PRIVATE)
        val idStr = id.toString()
        val viewMode = prefs.getString("${idStr}_view_mode", "week") ?: "week"
        val weekOffset = prefs.getInt("${idStr}_week_offset", 0)

        val targetWeekCal = Calendar.getInstance().apply {
            time = today
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }
        val weekDates = CalendarUtils.getWeekDates(targetWeekCal.time, settings.weekStartsOn)
        val weekStartTs = Calendar.getInstance().apply {
            time = weekDates.first()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekEndTs = Calendar.getInstance().apply {
            time = weekDates.last()
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val weekEvents = events.filter { it.start in weekStartTs..weekEndTs }.sortedBy { it.start }
        val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }

        provideContent {
            val dayNumStr = SimpleDateFormat("d", Locale.getDefault()).format(today)
            val monthWeekStr = SimpleDateFormat("M月 EEEE", Locale.CHINESE).format(today)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            CalendarWidgetShared.GlassContainer(theme = theme) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧大号日期看板与翻周控制
                    Column(
                        modifier = GlanceModifier
                            .width(88.dp)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayNumStr,
                            style = TextStyle(
                                color = ColorProvider(theme.dateNumText),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = monthWeekStr,
                            style = TextStyle(
                                color = ColorProvider(theme.subText),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "${lunar.text} ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                            style = TextStyle(
                                color = ColorProvider(if (lunar.holidayStatus == "holiday") Color(0xFFFF5252) else theme.subText),
                                fontSize = 9.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // 快速翻周按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "◀",
                                style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(horizontal = 3.dp).clickable(
                                    actionRunCallback<CalendarNavActionCallback>(
                                        actionParametersOf(CalendarNavActionCallback.actionKey to "nav_prev")
                                    )
                                )
                            )
                            Text(
                                text = "今",
                                style = TextStyle(color = ColorProvider(theme.accent), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(horizontal = 3.dp).clickable(
                                    actionRunCallback<CalendarNavActionCallback>(
                                        actionParametersOf(CalendarNavActionCallback.actionKey to "reset_today")
                                    )
                                )
                            )
                            Text(
                                text = "▶",
                                style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(horizontal = 3.dp).clickable(
                                    actionRunCallback<CalendarNavActionCallback>(
                                        actionParametersOf(CalendarNavActionCallback.actionKey to "nav_next")
                                    )
                                )
                            )
                        }
                    }

                    // 纵向毛玻璃分隔线
                    Box(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(theme.dividerColor)
                    ) {}

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // 右侧本周/今日日程内容列表
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                    ) {
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (weekOffset == 0) "今日 (${todayEvents.size}) / 本周 (${weekEvents.size})" else "该周日程 (${weekEvents.size})",
                                style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "日历 ↗",
                                style = TextStyle(color = ColorProvider(theme.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.clickable(actionStartActivity(mainIntent))
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))

                        val displayList = if (weekOffset == 0 && todayEvents.isNotEmpty()) todayEvents else weekEvents
                        if (displayList.isEmpty()) {
                            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (events.isEmpty()) "暂无扫描到日程" else "当前无日程安排 🎉",
                                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp)
                                )
                            }
                        } else {
                            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                items(displayList) { ev ->
                                    CalendarWidgetShared.EventItemRow(
                                        event = ev,
                                        theme = theme,
                                        storageManager = storageManager,
                                        showExtraFields = false,
                                        compact = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面日历微件 3 (日历·速览方块 - 2×2 方块精简版)
 * 2x2 Calendar Widget (Compact square layout)
 */
class CalendarWidget2x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.getCachedVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        val prefs = context.getSharedPreferences("widget_calendar_state", Context.MODE_PRIVATE)
        val idStr = id.toString()
        val weekOffset = prefs.getInt("${idStr}_week_offset", 0)

        val targetWeekCal = Calendar.getInstance().apply {
            time = today
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }
        val weekDates = CalendarUtils.getWeekDates(targetWeekCal.time, settings.weekStartsOn)
        val weekStartTs = Calendar.getInstance().apply {
            time = weekDates.first()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekEndTs = Calendar.getInstance().apply {
            time = weekDates.last()
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val weekEvents = events.filter { it.start in weekStartTs..weekEndTs }.sortedBy { it.start }
        val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }

        provideContent {
            val dayStr = SimpleDateFormat("M月d日 E", Locale.CHINESE).format(today)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            CalendarWidgetShared.GlassContainer(theme = theme) {
                // 顶部：日期、农历与翻周
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = dayStr,
                            style = TextStyle(color = ColorProvider(theme.headerText), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = lunar.text,
                            style = TextStyle(color = ColorProvider(theme.subText), fontSize = 9.sp)
                        )
                    }
                    Text(
                        text = "◀",
                        style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp),
                        modifier = GlanceModifier.clickable(
                            actionRunCallback<CalendarNavActionCallback>(
                                actionParametersOf(CalendarNavActionCallback.actionKey to "nav_prev")
                            )
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "▶",
                        style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp),
                        modifier = GlanceModifier.clickable(
                            actionRunCallback<CalendarNavActionCallback>(
                                actionParametersOf(CalendarNavActionCallback.actionKey to "nav_next")
                            )
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                val displayList = if (weekOffset == 0 && todayEvents.isNotEmpty()) todayEvents else weekEvents
                Text(
                    text = if (weekOffset == 0) "今日 (${todayEvents.size})" else "该周 (${weekEvents.size})",
                    style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )

                if (displayList.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "无日程安排 🎉",
                            style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(displayList.take(3)) { ev ->
                            CalendarWidgetShared.EventItemRow(
                                event = ev,
                                theme = theme,
                                storageManager = storageManager,
                                showExtraFields = false,
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }
}

// 广播接收器注册 / Broadcast Receivers
class CalendarWidget3x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget3x2()
}

class CalendarWidget2x3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget2x3()
}

class CalendarWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget2x2()
}
