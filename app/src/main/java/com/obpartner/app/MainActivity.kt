package com.obpartner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.CalendarEvent
import com.obpartner.app.model.HabitItem
import com.obpartner.app.model.TaskItem
import com.obpartner.app.notification.NotificationHelper
import com.obpartner.app.ui.calendar.DayViewScreen
import com.obpartner.app.ui.calendar.MonthViewScreen
import com.obpartner.app.ui.calendar.WeekViewScreen
import com.obpartner.app.ui.settings.SettingsScreen
import com.obpartner.app.ui.tasks.TodayTasksScreen
import com.obpartner.app.ui.theme.OBpartnerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 Activity
 * Main Activity
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OBpartnerTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storageManager = remember { StorageManager(context) }
    val notificationHelper = remember { NotificationHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(storageManager.getSettings()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 日历, 1: 任务, 2: 设置
    var calendarSubView by remember { mutableStateOf("week") } // "day", "week", "month"

    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    var habits by remember { mutableStateOf<List<HabitItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            val (e, t, h) = withContext(Dispatchers.IO) {
                storageManager.scanVault()
            }
            events = e
            tasks = t
            habits = h
            isLoading = false

            // 自动为日程注册闹钟提醒 / Auto schedule alarms for upcoming events
            e.forEach { event ->
                notificationHelper.scheduleEventAlarm(event)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedTab == 0) {
                        // 日历子视图切换单选按钮 (日 / 周 / 月)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = calendarSubView == "day",
                                onClick = { calendarSubView = "day" },
                                label = { Text("日", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = calendarSubView == "week",
                                onClick = { calendarSubView = "week" },
                                label = { Text("周", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = calendarSubView == "month",
                                onClick = { calendarSubView = "month" },
                                label = { Text("月", fontSize = 12.sp) }
                            )
                        }
                    } else if (selectedTab == 1) {
                        Text("今日面板 (Today's Panel)", fontSize = 18.sp)
                    } else {
                        Text("系统设置 (Settings)", fontSize = 18.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新扫描")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("日历") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("待办") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> {
                    when (calendarSubView) {
                        "day" -> DayViewScreen(
                            events = events,
                            settings = settings,
                            onEventClick = { /* Clicked */ }
                        )
                        "week" -> WeekViewScreen(
                            events = events,
                            settings = settings,
                            onEventClick = { /* Clicked */ }
                        )
                        "month" -> MonthViewScreen(
                            events = events,
                            settings = settings,
                            onEventClick = { /* Clicked */ }
                        )
                    }
                }
                1 -> {
                    TodayTasksScreen(
                        tasks = tasks,
                        habits = habits,
                        onTaskUpdated = { refreshData() }
                    )
                }
                2 -> {
                    SettingsScreen(
                        onSettingsSaved = {
                            settings = storageManager.getSettings()
                            refreshData()
                        }
                    )
                }
            }
        }
    }
}
