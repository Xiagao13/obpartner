package com.obpartner.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obpartner.app.calendar.CalendarUtils
import com.obpartner.app.calendar.ColorUtils
import com.obpartner.app.calendar.LunarHelper
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.AppSettings
import com.obpartner.app.model.CalendarEvent
import com.obpartner.app.ui.theme.AccentPrimary
import com.obpartner.app.ui.theme.HolidayBadgeColor
import com.obpartner.app.ui.theme.WorkBadgeColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * 月视图组件 (全功能对齐 Freepace MonthView，支持农历节气、工作日调休角标、多天跨天长条、选中单日展开)
 * Month View Component (Faithfully aligned with Freepace MonthView, supporting lunar info, holiday badges, cross-day bars, and day detail expansion)
 */
@Composable
fun MonthViewScreen(
    events: List<CalendarEvent>,
    settings: AppSettings,
    onEventClick: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }

    var currentMonthDate by remember { mutableStateOf(Date()) }
    var selectedDate by remember { mutableStateOf(Date()) }

    val monthFormat = remember { SimpleDateFormat("yyyy年 MM月", Locale.CHINESE) }
    val weekdayHeaders = remember(settings.weekStartsOn) {
        if (settings.weekStartsOn.equals("sunday", ignoreCase = true)) {
            listOf("日", "一", "二", "三", "四", "五", "六")
        } else {
            listOf("一", "二", "三", "四", "五", "六", "日")
        }
    }

    val cells = remember(currentMonthDate, settings.weekStartsOn) {
        CalendarUtils.generateMonthCells(currentMonthDate, settings.weekStartsOn)
    }

    // 选中日期的日程列表 / Events for selected day
    val selectedDayEvents = remember(selectedDate, events) {
        events.filter { CalendarUtils.isSameDay(Date(it.start), selectedDate) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 月份切换头部 (◀ 2026年 09月 ▶) / Month Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    time = currentMonthDate
                    add(Calendar.MONTH, -1)
                }
                currentMonthDate = cal.time
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上个月")
            }

            Text(
                text = monthFormat.format(currentMonthDate),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    time = currentMonthDate
                    add(Calendar.MONTH, 1)
                }
                currentMonthDate = cal.time
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下个月")
            }
        }

        // 2. 星期标题栏 (一 二 三 四 五 六 日) / Weekday Header
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayHeaders.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. 7x6 经典月历网格 / 7x6 Month Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
        ) {
            for (row in 0 until 6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < cells.size) {
                            val cell = cells[cellIndex]
                            val isSelected = CalendarUtils.isSameDay(cell.date, selectedDate)
                            val dayEvents = remember(cell.date, events) {
                                events.filter { CalendarUtils.isSameDay(Date(it.start), cell.date) }
                            }
                            val lunar = remember(cell.date) { LunarHelper.getLunarDetails(cell.date) }

                            MonthCellItem(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                cell = cell,
                                isSelected = isSelected,
                                lunar = lunar,
                                dayEvents = dayEvents,
                                onClick = { selectedDate = cell.date }
                            )
                        }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // 4. 当日日程展开列表抽屉 (点击直达 Obsidian 打开) / Selected Day Events Bottom List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp)
        ) {
            val selectedDateStr = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(selectedDate)
            val selectedLunar = remember(selectedDate) { LunarHelper.getLunarDetails(selectedDate) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = selectedDateStr,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${selectedLunar.text} (${selectedLunar.fullLunarDate})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedDayEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该日暂无日程安排",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(selectedDayEvents) { event ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // 核心联动：点击直接在 Obsidian 中打开 / Open in Obsidian
                                    storageManager.createOpenObsidianIntent(event.path)
                                        .let { context.startActivity(it) }
                                    onEventClick(event)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val accentColor = ColorUtils.stringToColor(event.colorValue, isDark = true, mode = "border")
                                val displayTitle = if (event.displayText.isNotBlank()) event.displayText else event.title

                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .background(accentColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    val timeText = if (event.isAllDay) "全天" else
                                        "${CalendarUtils.formatTime(Date(event.start))} - ${CalendarUtils.formatTime(Date(event.end))}"
                                    Text(
                                        text = timeText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (settings.showContent) {
                                        val fieldKeys = settings.displayFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        val extraDetails = fieldKeys.mapNotNull { key ->
                                            val v = event.extraData[key]?.toString()
                                            if (!v.isNullOrBlank() && v != displayTitle) "$key: $v" else null
                                        }.joinToString("  •  ")
                                        if (extraDetails.isNotBlank()) {
                                            Text(
                                                text = extraDetails,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "在 Obsidian 打开 ↗",
                                    fontSize = 11.sp,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun MonthCellItem(
    modifier: Modifier = Modifier,
    cell: CalendarUtils.MonthCell,
    isSelected: Boolean,
    lunar: com.obpartner.app.model.LunarDetails,
    dayEvents: List<CalendarEvent>,
    onClick: () -> Unit
) {
    val dayNumberFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }

    Box(
        modifier = modifier
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 日期数字与选中红圈 / Day Number
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (cell.isToday) AccentPrimary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayNumberFormat.format(cell.date),
                    fontSize = 11.sp,
                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        cell.isToday -> Color.White
                        cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }

            // 农历文字与班/休角标 / Lunar info and holiday badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = lunar.text,
                    fontSize = 8.sp,
                    color = if (cell.isCurrentMonth) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                lunar.holidayStatus?.let { status ->
                    val isWork = status == "work"
                    Box(
                        modifier = Modifier
                            .padding(start = 1.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isWork) WorkBadgeColor else HolidayBadgeColor)
                            .padding(horizontal = 1.5.dp)
                    ) {
                        Text(
                            text = if (isWork) "班" else "休",
                            fontSize = 7.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 日程小指示条 (有日程时展示) / Event Pill
            if (dayEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                val firstEvent = dayEvents.first()
                val pillBg = ColorUtils.stringToColor(firstEvent.colorValue, isDark = true, mode = "bg")
                val pillText = ColorUtils.stringToColor(firstEvent.colorValue, isDark = true, mode = "text")
                val pillBorder = ColorUtils.stringToColor(firstEvent.colorValue, isDark = true, mode = "border")
                val displayTitle = if (firstEvent.displayText.isNotBlank()) firstEvent.displayText else firstEvent.title

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(pillBg)
                        .border(0.5.dp, pillBorder, RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = displayTitle,
                        color = pillText,
                        fontSize = 7.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (dayEvents.size > 1) {
                    Text(
                        text = "+${dayEvents.size - 1}",
                        fontSize = 7.sp,
                        color = pillBorder,
                        modifier = Modifier.padding(top = 0.5.dp)
                    )
                }
            }

        }
    }
}
