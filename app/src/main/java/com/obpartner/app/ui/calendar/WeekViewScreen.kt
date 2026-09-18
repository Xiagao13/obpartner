package com.obpartner.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.calendar.ColorUtils
import com.obpartner.app.calendar.EventOverlapCalculator
import com.obpartner.app.calendar.LunarHelper
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.AppSettings
import com.obpartner.app.model.CalendarEvent
import com.obpartner.app.ui.theme.AccentPrimary
import com.obpartner.app.ui.theme.HolidayBadgeColor
import com.obpartner.app.ui.theme.WorkBadgeColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * 周视图组件 (全功能对齐 Freepace WeekView，完整保留左右横滑翻周、24h时间轴、全天置顶、重叠分栏、农历与调休角标)
 * Week View Component (Faithfully aligned with Freepace WeekView, supporting horizontal swiping, 24h timeline, all-day pin, overlap lanes, lunar info and holiday badges)
 */
@Composable
fun WeekViewScreen(
    events: List<CalendarEvent>,
    settings: AppSettings,
    onEventClick: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }

    // 虚拟分页以支持无限左右滑动翻周 / Virtual paging for infinite smooth week swiping
    val initialPage = 5000
    val pagerState = rememberPagerState(initialPage = initialPage) { 10000 }

    val baseCalendar = remember {
        Calendar.getInstance().apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    // 计算当前页对应的周日期 / Calculate dates of the week for current page
    fun getDatesForPage(page: Int): List<Date> {
        val pageOffset = page - initialPage
        val cal = Calendar.getInstance().apply {
            time = baseCalendar.time
            add(Calendar.WEEK_OF_YEAR, pageOffset)
        }
        return CalendarUtils.getWeekDates(cal.time, settings.weekStartsOn)
    }

    val verticalScrollState = rememberScrollState()
    val hourHeight = 60.dp
    val density = LocalDensity.current

    // 初始化时自动滚动到默认开始时间 (如 8:00) / Auto scroll to default start hour
    LaunchedEffect(Unit) {
        val targetScrollPx = with(density) { (settings.defaultStartHour * hourHeight.toPx()).toInt() }
        verticalScrollState.scrollTo(targetScrollPx)
    }

    // 当前时间红线每分钟刷新 / Red line timer updates every minute
    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000L)
            currentTime = Date()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 水平滑动分页器（左右滑动切换周） / HorizontalPager for smooth week swiping
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val weekDates = remember(page, settings.weekStartsOn) { getDatesForPage(page) }

            // 按日归类事件 / Group events by date
            val allDayEventsByDate = remember(weekDates, events) {
                weekDates.associateWith { date ->
                    events.filter { it.isAllDay && CalendarUtils.isSameDay(Date(it.start), date) }
                }
            }

            val timedEventsByDate = remember(weekDates, events) {
                weekDates.associateWith { date ->
                    val dayEvents = events.filter { !it.isAllDay && CalendarUtils.isSameDay(Date(it.start), date) }
                    EventOverlapCalculator.calculateOverlaps(dayEvents)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 顶部周表头（星期、公历日期、农历文字与班/休角标） / Top week header
                WeekHeaderRow(weekDates = weekDates)

                // 2. 全天/跨天日程吸顶栏 (All-Day Events Pinned Bar)
                val hasAllDay = allDayEventsByDate.values.any { it.isNotEmpty() }
                if (hasAllDay) {
                    AllDayEventsRow(
                        weekDates = weekDates,
                        allDayEventsByDate = allDayEventsByDate,
                        onEventClick = { event ->
                            storageManager.createOpenObsidianIntent(event.path, event.vaultRelativePath).let { context.startActivity(it) }
                            onEventClick(event)
                        }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // 3. 24 小时时间刻度与日程内容区 (支持垂直滑动) / 24-hour timeline grid
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // 左侧时间刻度列 (00:00 - 24:00) / Left time axis
                        Column(
                            modifier = Modifier
                                .width(52.dp)
                                .padding(end = 4.dp)
                        ) {
                            for (hour in 0..24) {
                                Box(
                                    modifier = Modifier
                                        .height(hourHeight)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d:00", hour),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 7 天时间网格列 / 7-day columns
                        Row(modifier = Modifier.weight(1f)) {
                            weekDates.forEachIndexed { colIndex, date ->
                                val isToday = CalendarUtils.isSameDay(date, currentTime)
                                val dayEvents = timedEventsByDate[date] ?: emptyList()

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(hourHeight * 24)
                                        .border(
                                            width = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                ) {
                                    val colWidth = maxWidth

                                    // 背景小时网格横线 / Hour horizontal dividing lines
                                    for (h in 1..23) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .offset(y = hourHeight * h)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        )
                                    }

                                    // 今日当前时间红线指示器 / Red line indicator for current time
                                    if (isToday) {
                                        val cal = Calendar.getInstance().apply { time = currentTime }
                                        val currentMinuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                                        val redLineY = hourHeight * (currentMinuteOfDay / 60f)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .offset(y = redLineY)
                                                .background(Color.Red)
                                        )
                                    }

                                    // 渲染计算好重叠泳道的日程卡片 (1:1 对齐 Freepace calculateEventOverlaps 泳道并列)
                                    dayEvents.forEach { event ->
                                        val eventCal = Calendar.getInstance().apply { time = Date(event.start) }
                                        val startMinutes = eventCal.get(Calendar.HOUR_OF_DAY) * 60 + eventCal.get(Calendar.MINUTE)
                                        val durationMinutes = ((event.end - event.start) / 60000L).coerceAtLeast(25L)

                                        val topOffset = hourHeight * (startMinutes / 60f)
                                        val itemHeight = (hourHeight * (durationMinutes / 60f)).coerceAtLeast(26.dp)

                                        // 核心并列排布算法：宽度 = 总宽 / overlapCount，X轴偏移 = 宽度 * overlapIndex
                                        val overlapCount = kotlin.math.max(1, event.overlapCount)
                                        val overlapIndex = event.overlapIndex.coerceIn(0, overlapCount - 1)
                                        val itemWidth = colWidth / overlapCount
                                        val xOffset = itemWidth * overlapIndex

                                        val bgColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "bg")
                                        val borderColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "border")
                                        val textColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "text")
                                        val displayTitle = if (event.displayText.isNotBlank()) event.displayText else event.title

                                        Box(
                                            modifier = Modifier
                                                .width(itemWidth)
                                                .offset(x = xOffset, y = topOffset)
                                                .padding(horizontal = 0.5.dp)
                                                .height(itemHeight)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(bgColor)
                                                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    // 点击直达打开 Obsidian 笔记 / Click opens Obsidian directly
                                                    storageManager.createOpenObsidianIntent(event.path, event.vaultRelativePath)
                                                        .let { context.startActivity(it) }
                                                    onEventClick(event)
                                                }
                                                .padding(3.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = displayTitle,
                                                    color = textColor,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${CalendarUtils.formatTime(Date(event.start))} - ${CalendarUtils.formatTime(Date(event.end))}",
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 8.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                // Freepace 扩展内容与属性字段展示
                                                if (settings.showContent && itemHeight >= 46.dp) {
                                                    val fieldKeys = settings.displayFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                                    for (fKey in fieldKeys) {
                                                        val fVal = event.extraData[fKey]?.toString()
                                                        if (!fVal.isNullOrBlank() && fVal != displayTitle) {
                                                            Text(
                                                                text = "$fKey: $fVal",
                                                                color = Color.White.copy(alpha = 0.75f),
                                                                fontSize = 7.5.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
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

                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeaderRow(weekDates: List<Date>) {
    val today = remember { Date() }
    val dayNameFormat = SimpleDateFormat("E", Locale.CHINESE)
    val dayNumberFormat = SimpleDateFormat("d", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, top = 8.dp, bottom = 8.dp)
    ) {
        weekDates.forEach { date ->
            val isToday = CalendarUtils.isSameDay(date, today)
            val lunar = remember(date) { LunarHelper.getLunarDetails(date) }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 星期几 / Weekday
                Text(
                    text = dayNameFormat.format(date),
                    fontSize = 11.sp,
                    color = if (isToday) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 公历日号 / Gregorian Day Number
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isToday) AccentPrimary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumberFormat.format(date),
                        fontSize = 13.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                // 农历文字与休/班角标 / Lunar info and holiday badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = lunar.text,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    lunar.holidayStatus?.let { status ->
                        val isWork = status == "work"
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isWork) WorkBadgeColor else HolidayBadgeColor)
                                .padding(horizontal = 2.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = if (isWork) "班" else "休",
                                fontSize = 8.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllDayEventsRow(
    weekDates: List<Date>,
    allDayEventsByDate: Map<Date, List<CalendarEvent>>,
    onEventClick: (CalendarEvent) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp, top = 4.dp, bottom = 4.dp)
        ) {
            weekDates.forEach { date ->
                val dayAllDayEvents = allDayEventsByDate[date] ?: emptyList()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                ) {
                    dayAllDayEvents.forEach { event ->
                        val bgColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "bg")
                        val borderColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "border")
                        val textColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "text")
                        val displayTitle = if (event.displayText.isNotBlank()) event.displayText else event.title

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                                .clickable { onEventClick(event) }
                                .padding(2.dp)
                        ) {
                            Text(
                                text = displayTitle,
                                fontSize = 9.sp,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                }
            }
        }
    }
}
