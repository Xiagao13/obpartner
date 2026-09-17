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
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.CalendarEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * 桌面日历微件基类与公共内容渲染 (1:1 照搬 Freepace 日历块现代暗黑风格)
 * Calendar Widgets (Faithfully aligned with Freepace CalendarBlock dark aesthetic)
 */
object CalendarWidgetShared {

    @Composable
    fun CalendarHeader(
        todayStr: String,
        lunarText: String,
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
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "农历 $lunarText",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFB0BEC5)),
                        fontSize = 10.sp
                    )
                )
            }

            Text(
                text = "打开日历 ↗",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9575CD)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(onOpenAppIntent))
            )
        }
    }

    @Composable
    fun WeekdaysBar(weekDates: List<Date>, today: Date) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0xFF252638))
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
                        .background(if (isCurrent) Color(0xFF7C4DFF) else Color.Transparent)
                        .cornerRadius(if (isCurrent) 4.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekFmt.format(date),
                        style = TextStyle(color = ColorProvider(if (isCurrent) Color.White else Color(0xFF90A4AE)), fontSize = 8.sp)
                    )
                    Text(
                        text = dayFmt.format(date),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 10.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    dateLunar.holidayStatus?.let { status ->
                        Text(
                            text = if (status == "work") "班" else "休",
                            style = TextStyle(
                                color = ColorProvider(if (status == "work") Color(0xFF90A4AE) else Color(0xFFFF5252)),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EventItemRow(
        event: CalendarEvent,
        storageManager: StorageManager,
        showExtraFields: Boolean = false
    ) {
        val openObsidianIntent = storageManager.createOpenObsidianIntent(event.path)
        val eventDateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(event.start))
        val timeStr = if (event.isAllDay) "全天 ($eventDateStr)" else
            "$eventDateStr - ${CalendarUtils.formatTime(Date(event.end))}"

        val accentColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "border")
        val displayTitle = if (event.displayText.isNotBlank()) event.displayText else event.title

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(Color(0xFF26273A))
                .cornerRadius(6.dp)
                .padding(6.dp)
                .clickable(actionStartActivity(openObsidianIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Freepace 日历块特征：左侧彩色垂直指示条
            Box(
                modifier = GlanceModifier
                    .width(3.5.dp)
                    .height(if (showExtraFields) 38.dp else 26.dp)
                    .background(accentColor)
                    .cornerRadius(2.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = displayTitle,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = timeStr,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFB0BEC5)),
                        fontSize = 10.sp
                    )
                )
                // 4x4 大组件展示扩展属性 (如上课位置、计价等)
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
                                color = ColorProvider(Color(0xFF80CBC4)),
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }

            Text(
                text = "Obsidian ↗",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9575CD)),
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * 桌面组件 1 (4×2 紧凑版)
 */
class CalendarWidget4x2 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val todayStr = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(today)
            val weekDates = CalendarUtils.getWeekDates(today, storageManager.getSettings().weekStartsOn)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val displayEvents = if (todayEvents.isEmpty()) events.sortedBy { it.start }.take(4) else todayEvents
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF181824))
                    .cornerRadius(16.dp)
                    .padding(8.dp)
            ) {
                CalendarWidgetShared.CalendarHeader(
                    todayStr = todayStr,
                    lunarText = "${lunar.text} ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                    onOpenAppIntent = mainIntent
                )

                Spacer(modifier = GlanceModifier.height(4.dp))
                CalendarWidgetShared.WeekdaysBar(weekDates = weekDates, today = today)
                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = if (isUpcoming) "近期日程 (${displayEvents.size})" else "今日日程 (${todayEvents.size})",
                    style = TextStyle(color = ColorProvider(Color(0xFF80CBC4)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )

                if (displayEvents.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (events.isEmpty()) "未扫描到日程文件" else "今日无日程安排 🎉",
                            style = TextStyle(color = ColorProvider(Color(0xFF78909C)), fontSize = 11.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(displayEvents) { ev ->
                            CalendarWidgetShared.EventItemRow(event = ev, storageManager = storageManager, showExtraFields = false)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面组件 1 (4×4 大卡片版 - Freepace 日历块同款豪华看板)
 */
class CalendarWidget4x4 : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            val todayStr = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(today)
            val weekDates = CalendarUtils.getWeekDates(today, storageManager.getSettings().weekStartsOn)
            val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }
            val displayEvents = if (todayEvents.isEmpty()) events.sortedBy { it.start }.take(8) else todayEvents
            val isUpcoming = todayEvents.isEmpty() && displayEvents.isNotEmpty()

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF181824))
                    .cornerRadius(16.dp)
                    .padding(12.dp)
            ) {
                CalendarWidgetShared.CalendarHeader(
                    todayStr = todayStr,
                    lunarText = "${lunar.text} (${lunar.fullLunarDate}) ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                    onOpenAppIntent = mainIntent,
                    titleSuffix = " 📅 日程看板"
                )

                Spacer(modifier = GlanceModifier.height(6.dp))
                CalendarWidgetShared.WeekdaysBar(weekDates = weekDates, today = today)
                Spacer(modifier = GlanceModifier.height(8.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUpcoming) "近期日程流 (${displayEvents.size})" else "今日日程流 (${todayEvents.size})",
                        style = TextStyle(color = ColorProvider(Color(0xFF80CBC4)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "共 ${events.size} 项日程",
                        style = TextStyle(color = ColorProvider(Color(0xFF90A4AE)), fontSize = 10.sp)
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
                            style = TextStyle(color = ColorProvider(Color(0xFF78909C)), fontSize = 12.sp)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(displayEvents) { ev ->
                            CalendarWidgetShared.EventItemRow(event = ev, storageManager = storageManager, showExtraFields = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 默认日历组件 (兼顾兼容性)
 */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CalendarWidget4x2().provideGlance(context, id)
    }
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

