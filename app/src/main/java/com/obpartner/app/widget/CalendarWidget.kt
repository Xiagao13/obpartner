package com.obpartner.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
 * 桌面日历微件公共组件与视图体系 (支持 3×2/2×3/2×2 及柔光磨砂玻璃多主题)
 * Calendar Widgets Shared Views & Components (Supports 3×2, 2×3, 2×2 & Glassmorphism themes)
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
     * 日历通用头部
     * Common Calendar Header
     */
    @Composable
    fun CalendarHeader(
        todayStr: String,
        lunarText: String,
        theme: WidgetThemeConfig,
        onOpenAppIntent: Intent,
        titleSuffix: String = ""
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "$todayStr$titleSuffix",
                    style = TextStyle(
                        color = ColorProvider(theme.headerText),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "农历 $lunarText",
                    style = TextStyle(
                        color = ColorProvider(theme.subText),
                        fontSize = 10.sp
                    )
                )
            }

            Text(
                text = "Obsidian ↗",
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
     * 周指示栏 (复刻 Freepace 日历块顶部星期胶囊)
     * Weekdays Indicator Bar
     */
    @Composable
    fun WeekdaysBar(
        weekDates: List<Date>,
        today: Date,
        theme: WidgetThemeConfig
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(theme.cardBg)
                .cornerRadius(6.dp)
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dayFmt = SimpleDateFormat("d", Locale.getDefault())
            val weekFmt = SimpleDateFormat("E", Locale.CHINESE)

            weekDates.forEach { date ->
                val isCurrent = CalendarUtils.isSameDay(date, today)
                val dateLunar = LunarHelper.getLunarDetails(date)

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp)
                        .background(if (isCurrent) theme.accent else Color.Transparent)
                        .cornerRadius(if (isCurrent) 4.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekFmt.format(date),
                        style = TextStyle(
                            color = ColorProvider(if (isCurrent) Color.White else theme.subText),
                            fontSize = 8.sp
                        )
                    )
                    Text(
                        text = dayFmt.format(date),
                        style = TextStyle(
                            color = ColorProvider(if (isCurrent) Color.White else theme.headerText),
                            fontSize = 10.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    dateLunar.holidayStatus?.let { status ->
                        Text(
                            text = if (status == "work") "班" else "休",
                            style = TextStyle(
                                color = ColorProvider(if (status == "work") theme.subText else Color(0xFFFF5252)),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * 单条日程行渲染 (左侧彩色粗指示条，点击秒开 Obsidian)
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
            // Freepace 日历块特征：左侧彩色垂直指示条
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
 * 桌面日历微件 1 (3×2 横版主推 - 适配主流 5 列/6 列桌面，支持对称居中)
 * 3x2 Calendar Widget (Primary horizontal layout, centerable on 5-col launchers)
 */
class CalendarWidget3x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val dayNumStr = SimpleDateFormat("d", Locale.getDefault()).format(today)
            val monthWeekStr = SimpleDateFormat("M月 EEEE", Locale.CHINESE).format(today)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayStart = Calendar.getInstance().apply {
                time = today
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val upcomingEvents = events.filter { (it.start >= todayStart || it.end >= today.time) && !CalendarUtils.isSameDay(Date(it.start), today) }
                .sortedBy { it.start }
            val displayEvents = if (todayEvents.isNotEmpty()) todayEvents else upcomingEvents.take(3)
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            CalendarWidgetShared.GlassContainer(theme = theme) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧大号日期看板 (固定紧凑宽度)
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
                                fontSize = 34.sp,
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
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "日历 ↗",
                            style = TextStyle(
                                color = ColorProvider(theme.accent),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.clickable(actionStartActivity(mainIntent))
                        )
                    }

                    // 纵向毛玻璃分隔线
                    Box(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(theme.dividerColor)
                    ) {}

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // 右侧日程内容列表 (自适应撑满剩余宽度)
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = if (isUpcoming) "近期日程 (${displayEvents.size})" else "今日日程 (${todayEvents.size})",
                            style = TextStyle(
                                color = ColorProvider(theme.highlight),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))

                        if (displayEvents.isEmpty()) {
                            Box(
                                modifier = GlanceModifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (events.isEmpty()) "暂无扫描到日程" else "今日无日程安排 🎉",
                                    style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp)
                                )
                            }
                        } else {
                            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                items(displayEvents) { ev ->
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
 * 桌面日历微件 2 (2×3 竖版主推 - 占 2 列 3 行，与两侧应用图标完美和谐)
 * 2x3 Calendar Widget (Primary vertical layout, pairs smoothly with icons)
 */
class CalendarWidget2x3 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val todayDateStr = SimpleDateFormat("M月d日 E", Locale.CHINESE).format(today)
            val weekDates = CalendarUtils.getWeekDates(today, settings.weekStartsOn)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayStart = Calendar.getInstance().apply {
                time = today
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val upcomingEvents = events.filter { (it.start >= todayStart || it.end >= today.time) && !CalendarUtils.isSameDay(Date(it.start), today) }
                .sortedBy { it.start }
            val displayEvents = if (todayEvents.isNotEmpty()) todayEvents else upcomingEvents.take(4)
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            CalendarWidgetShared.GlassContainer(theme = theme) {
                // 顶部日期及跳转
                CalendarWidgetShared.CalendarHeader(
                    todayStr = todayDateStr,
                    lunarText = "${lunar.text} ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                    theme = theme,
                    onOpenAppIntent = mainIntent
                )

                Spacer(modifier = GlanceModifier.height(4.dp))
                CalendarWidgetShared.WeekdaysBar(weekDates = weekDates, today = today, theme = theme)
                Spacer(modifier = GlanceModifier.height(6.dp))

                Text(
                    text = if (isUpcoming) "近期日程 (${displayEvents.size})" else "今日日程 (${todayEvents.size})",
                    style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = GlanceModifier.height(2.dp))

                if (displayEvents.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (events.isEmpty()) "暂无日程文件" else "今日无日程安排 🎉",
                            style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(displayEvents) { ev ->
                            CalendarWidgetShared.EventItemRow(
                                event = ev,
                                theme = theme,
                                storageManager = storageManager,
                                showExtraFields = false,
                                compact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面日历微件 3 (2×2 方块精简版 - 经典小方块)
 * 2x2 Calendar Widget (Compact square layout)
 */
class CalendarWidget2x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val dayStr = SimpleDateFormat("M月d日 E", Locale.CHINESE).format(today)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayStart = Calendar.getInstance().apply {
                time = today
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val upcomingEvents = events.filter { (it.start >= todayStart || it.end >= today.time) && !CalendarUtils.isSameDay(Date(it.start), today) }
                .sortedBy { it.start }
            val displayEvents = if (todayEvents.isNotEmpty()) todayEvents else upcomingEvents.take(2)
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            CalendarWidgetShared.GlassContainer(theme = theme) {
                // 顶部：日期与农历
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
                        text = "↗",
                        style = TextStyle(color = ColorProvider(theme.accent), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.clickable(actionStartActivity(mainIntent))
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = if (isUpcoming) "近期 (${displayEvents.size})" else "今日 (${todayEvents.size})",
                    style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )

                if (displayEvents.isEmpty()) {
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
                        items(displayEvents) { ev ->
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

/**
 * 桌面日历微件 4 (4×2 宽屏兼容版)
 */
class CalendarWidget4x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val todayStr = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(today)
            val weekDates = CalendarUtils.getWeekDates(today, settings.weekStartsOn)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayStart = Calendar.getInstance().apply {
                time = today
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val upcomingEvents = events.filter { (it.start >= todayStart || it.end >= today.time) && !CalendarUtils.isSameDay(Date(it.start), today) }
                .sortedBy { it.start }
            val displayEvents = if (todayEvents.isNotEmpty()) todayEvents else upcomingEvents.take(4)
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            CalendarWidgetShared.GlassContainer(theme = theme) {
                CalendarWidgetShared.CalendarHeader(
                    todayStr = todayStr,
                    lunarText = "${lunar.text} ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                    theme = theme,
                    onOpenAppIntent = mainIntent
                )

                Spacer(modifier = GlanceModifier.height(4.dp))
                CalendarWidgetShared.WeekdaysBar(weekDates = weekDates, today = today, theme = theme)
                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = if (isUpcoming) "近期日程 (${displayEvents.size})" else "今日日程 (${todayEvents.size})",
                    style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )

                if (displayEvents.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (events.isEmpty()) "未扫描到日程文件" else "今日无日程安排 🎉",
                            style = TextStyle(color = ColorProvider(theme.subText), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(displayEvents) { ev ->
                            CalendarWidgetShared.EventItemRow(
                                event = ev,
                                theme = theme,
                                storageManager = storageManager,
                                showExtraFields = false,
                                compact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面日历微件 5 (4×4 大卡片版 - Freepace 日历块同款豪华看板)
 */
class CalendarWidget4x4 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val settings = storageManager.getSettings()
        val theme = ColorUtils.getWidgetTheme(settings.widgetTheme)
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val todayStr = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(today)
            val weekDates = CalendarUtils.getWeekDates(today, settings.weekStartsOn)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayStart = Calendar.getInstance().apply {
                time = today
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val upcomingEvents = events.filter { (it.start >= todayStart || it.end >= today.time) && !CalendarUtils.isSameDay(Date(it.start), today) }
                .sortedBy { it.start }
            val displayEvents = if (todayEvents.isNotEmpty()) todayEvents else upcomingEvents.take(8)
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            CalendarWidgetShared.GlassContainer(theme = theme) {
                CalendarWidgetShared.CalendarHeader(
                    todayStr = todayStr,
                    lunarText = "${lunar.text} (${lunar.fullLunarDate}) ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                    theme = theme,
                    onOpenAppIntent = mainIntent,
                    titleSuffix = " 📅 日程看板"
                )

                Spacer(modifier = GlanceModifier.height(6.dp))
                CalendarWidgetShared.WeekdaysBar(weekDates = weekDates, today = today, theme = theme)
                Spacer(modifier = GlanceModifier.height(8.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUpcoming) "近期日程流 (${displayEvents.size})" else "今日日程流 (${todayEvents.size})",
                        style = TextStyle(color = ColorProvider(theme.highlight), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "共 ${events.size} 项日程",
                        style = TextStyle(color = ColorProvider(theme.subText), fontSize = 10.sp)
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                if (displayEvents.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (events.isEmpty()) "暂未扫描到日程文件\n请在应用设置中授权并检查路径" else "近期暂无日程安排 🎉",
                            style = TextStyle(color = ColorProvider(theme.subText), fontSize = 12.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(displayEvents) { ev ->
                            CalendarWidgetShared.EventItemRow(
                                event = ev,
                                theme = theme,
                                storageManager = storageManager,
                                showExtraFields = true,
                                compact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 默认日历组件 (指向 3×2 横版主推)
 */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CalendarWidget3x2().provideGlance(context, id)
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

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

class CalendarWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget4x2()
}

class CalendarWidget4x4Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget4x4()
}
