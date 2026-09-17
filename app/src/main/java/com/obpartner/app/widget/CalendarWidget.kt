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
import androidx.glance.appwidget.action.actionStartActivity
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
import com.obpartner.app.calendar.LunarHelper
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.CalendarEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * 桌面组件 1：日周月视图日历微件
 * Desktop Widget 1: Calendar View Widget (Supports Day/Week/Month views and click-to-open Obsidian)
 */
class CalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storageManager = StorageManager(context)
        val (events, _, _) = storageManager.scanVault()
        val today = Date()
        val lunar = LunarHelper.getLunarDetails(today)

        provideContent {
            CalendarWidgetContent(
                context = context,
                events = events,
                today = today,
                lunarText = "${lunar.text} ${if (lunar.holidayStatus == "work") "(班)" else if (lunar.holidayStatus == "holiday") "(休)" else ""}",
                storageManager = storageManager
            )
        }
    }

    @Composable
    private fun CalendarWidgetContent(
        context: Context,
        events: List<CalendarEvent>,
        today: Date,
        lunarText: String,
        storageManager: StorageManager
    ) {
        val todayStr = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(today)
        val weekDates = CalendarUtils.getWeekDates(today, storageManager.getSettings().weekStartsOn)
        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // 筛选本周与今日日程
        val todayEvents = events.filter { CalendarUtils.isSameDay(Date(it.start), today) }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E1E2E))
                .padding(12.dp)
        ) {
            // 1. 顶部栏：当前日期、农历信息与打开主程序按钮
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = todayStr,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "农历: $lunarText",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFB0BEC5)),
                            fontSize = 11.sp
                        )
                    )
                }

                // 点击打开 OBpartner 主界面
                Text(
                    text = "打开日历 ↗",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF7C4DFF)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.clickable(actionStartActivity(mainActivityIntent))
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 2. 本周日期快速导航指示栏 (Mon ~ Sun)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                val dayFmt = SimpleDateFormat("d", Locale.getDefault())
                val weekFmt = SimpleDateFormat("E", Locale.CHINESE)

                weekDates.forEach { date ->
                    val isCurrent = CalendarUtils.isSameDay(date, today)
                    val dateLunar = LunarHelper.getLunarDetails(date)

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 1.dp)
                            .background(if (isCurrent) Color(0xFF7C4DFF) else Color(0xFF2A2B3D)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = weekFmt.format(date),
                            style = TextStyle(color = ColorProvider(Color(0xFFB0BEC5)), fontSize = 9.sp)
                        )
                        Text(
                            text = dayFmt.format(date),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        dateLunar.holidayStatus?.let { status ->
                            Text(
                                text = if (status == "work") "班" else "休",
                                style = TextStyle(
                                    color = ColorProvider(if (status == "work") Color(0xFF90A4AE) else Color(0xFFFF5252)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 3. 今日日程列表 (点击笔记直达 Obsidian 打开)
            Text(
                text = "今日日程 (${todayEvents.size})",
                style = TextStyle(color = ColorProvider(Color(0xFF80CBC4)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )

            if (todayEvents.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "今日暂无日程安排 🎉",
                        style = TextStyle(color = ColorProvider(Color(0xFF78909C)), fontSize = 12.sp)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(todayEvents) { event ->
                        val openObsidianIntent = storageManager.createOpenObsidianIntent(event.path)
                        val timeStr = if (event.isAllDay) "全天" else
                            "${CalendarUtils.formatTime(Date(event.start))} - ${CalendarUtils.formatTime(Date(event.end))}"

                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(Color(0xFF2E2F45))
                                .padding(8.dp)
                                .clickable(actionStartActivity(openObsidianIntent)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = event.title,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = timeStr,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFB0BEC5)),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Text(
                                text = "Obsidian ↗",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF7C4DFF)),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}
