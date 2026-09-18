package com.obpartner.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
 * 日视图组件 (24小时详细时间轴与日程排布)
 * Day View Component (Detailed 24-hour timeline and event layout)
 */
@Composable
fun DayViewScreen(
    events: List<CalendarEvent>,
    settings: AppSettings,
    onEventClick: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }

    var currentDate by remember { mutableStateOf(Date()) }
    val verticalScrollState = rememberScrollState()
    val hourHeight = 64.dp
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        val targetScrollPx = with(density) { (settings.defaultStartHour * hourHeight.toPx()).toInt() }
        verticalScrollState.scrollTo(targetScrollPx)
    }

    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000L)
            currentTime = Date()
        }
    }

    val dayEvents = remember(currentDate, events) {
        events.filter { CalendarUtils.isSameDay(Date(it.start), currentDate) }
    }

    val allDayEvents = remember(dayEvents) { dayEvents.filter { it.isAllDay } }
    val timedEvents = remember(dayEvents) {
        val regular = dayEvents.filter { !it.isAllDay }
        EventOverlapCalculator.calculateOverlaps(regular)
    }

    val lunar = remember(currentDate) { LunarHelper.getLunarDetails(currentDate) }
    val isToday = CalendarUtils.isSameDay(currentDate, currentTime)

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 日期切换导航 / Day Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    time = currentDate
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                currentDate = cal.time
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前一天")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val titleFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
                Text(
                    text = titleFormat.format(currentDate),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${lunar.text}  (${lunar.fullLunarDate})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    lunar.holidayStatus?.let { status ->
                        val isWork = status == "work"
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isWork) WorkBadgeColor else HolidayBadgeColor)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isWork) "班" else "休",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    time = currentDate
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                currentDate = cal.time
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "后一天")
            }
        }

        // 2. 全天日程栏 / All-Day Events
        if (allDayEvents.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "全天日程",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    allDayEvents.forEach { ev ->
                        val bgColor = ColorUtils.stringToColor(ev.colorValue, isDark = true, mode = "bg")
                        val borderColor = ColorUtils.stringToColor(ev.colorValue, isDark = true, mode = "border")
                        val textColor = ColorUtils.stringToColor(ev.colorValue, isDark = true, mode = "text")
                        val displayTitle = if (ev.displayText.isNotBlank()) ev.displayText else ev.title

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                                .clickable {
                                    storageManager.createOpenObsidianIntent(ev.path, ev.vaultRelativePath).let { context.startActivity(it) }
                                    onEventClick(ev)
                                }
                                .padding(6.dp)
                        ) {
                            Text(text = displayTitle, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // 3. 24 小时时间轴 / 24-Hour Timeline
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧时间刻度 / Left Time Axis
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .padding(end = 6.dp)
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
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 右侧日程排布区 / Events Column (支持 BoxWithConstraints 多泳道重叠并列)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(hourHeight * 24)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    val colWidth = maxWidth

                    for (h in 1..23) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .offset(y = hourHeight * h)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        )
                    }

                    if (isToday) {
                        val cal = Calendar.getInstance().apply { time = currentTime }
                        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        val redLineY = hourHeight * (minuteOfDay / 60f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .offset(y = redLineY)
                                .background(Color.Red)
                        )
                    }

                    timedEvents.forEach { event ->
                        val eventCal = Calendar.getInstance().apply { time = Date(event.start) }
                        val startMinutes = eventCal.get(Calendar.HOUR_OF_DAY) * 60 + eventCal.get(Calendar.MINUTE)
                        val durationMinutes = ((event.end - event.start) / 60000L).coerceAtLeast(30L)

                        val topOffset = hourHeight * (startMinutes / 60f)
                        val itemHeight = (hourHeight * (durationMinutes / 60f)).coerceAtLeast(32.dp)

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
                                .padding(horizontal = 1.dp)
                                .height(itemHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                                .clickable {
                                    storageManager.createOpenObsidianIntent(event.path, event.vaultRelativePath).let { context.startActivity(it) }
                                    onEventClick(event)
                                }
                                .padding(6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = displayTitle,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${CalendarUtils.formatTime(Date(event.start))} - ${CalendarUtils.formatTime(Date(event.end))}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Freepace 扩展内容与属性字段展示
                                if (settings.showContent && itemHeight >= 55.dp) {
                                    val fieldKeys = settings.displayFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    for (fKey in fieldKeys) {
                                        val fVal = event.extraData[fKey]?.toString()
                                        if (!fVal.isNullOrBlank() && fVal != displayTitle) {
                                            Text(
                                                text = "$fKey: $fVal",
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 9.5.sp,
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
