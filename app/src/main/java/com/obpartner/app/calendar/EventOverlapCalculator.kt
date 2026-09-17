package com.obpartner.app.calendar

import com.obpartner.app.model.CalendarEvent
import kotlin.math.max

/**
 * 日程重叠排布算法 (1:1 精确复刻 Freepace calculateEventOverlaps)
 * Event Overlap Layout Calculator (Faithfully ported 1:1 from Freepace calculateEventOverlaps)
 *
 * 用于在周视图和日视图中，将同一时间段内有时间冲突的多条日程动态分栏，计算各自的泳道（Lane）
 * 与宽度分栏数（OverlapCount），实现并列排布且互不遮挡。
 */
object EventOverlapCalculator {

    private data class IntermediateEvent(
        val event: CalendarEvent,
        val laneIndex: Int,
        val maxLanesAtEvent: Int
    )

    /**
     * 计算重叠事件以进行正确的布局（泳道）
     * Calculate overlapping events for proper layout (lanes)
     *
     * @param events 当天或当前区间的待排布事件列表 / Events list for current day
     * @return 计算完 overlapIndex 与 overlapCount 的事件列表 / Events with calculated layout indices
     */
    fun calculateOverlaps(events: List<CalendarEvent>): List<CalendarEvent> {
        if (events.isEmpty()) return emptyList()

        // 1. 按开始时间升序排列 / Sort by start time ascending
        val sortedEvents = events.sortedBy { it.start }
        val intermediateList = mutableListOf<IntermediateEvent>()
        val lanes = mutableListOf<Long>() // 记录每个泳道当前被占用的最晚结束时间戳

        // 2. 分配泳道 / Assign lanes
        for (event in sortedEvents) {
            val eventStart = event.start
            val eventEnd = if (event.end > event.start) event.end else event.start + 3600000L

            var assignedLane = -1
            for (i in lanes.indices) {
                if (lanes[i] <= eventStart) {
                    assignedLane = i
                    break
                }
            }

            if (assignedLane == -1) {
                assignedLane = lanes.size
                lanes.add(eventEnd)
            } else {
                lanes[assignedLane] = max(lanes[assignedLane], eventEnd)
            }

            // 计算该事件发生时刻的并发泳道数 / Calculate overlap count at this event
            var maxLanesAtEvent = 1
            for (i in lanes.indices) {
                if (lanes[i] > eventStart) {
                    maxLanesAtEvent = max(maxLanesAtEvent, i + 1)
                }
            }

            intermediateList.add(
                IntermediateEvent(
                    event = event,
                    laneIndex = assignedLane,
                    maxLanesAtEvent = maxLanesAtEvent
                )
            )
        }

        // 3. 最终宽度与重叠数调整 / Final width and overlap adjustments
        return intermediateList.map { current ->
            val eventStart = current.event.start
            val eventEnd = if (current.event.end > current.event.start) current.event.end else current.event.start + 3600000L
            var maxOverlap = 1

            for (other in intermediateList) {
                val otherStart = other.event.start
                val otherEnd = if (other.event.end > other.event.start) other.event.end else other.event.start + 3600000L

                // 判断两事件在时间上是否发生重叠 / Check if time intervals overlap
                if (otherStart < eventEnd && otherEnd > eventStart) {
                    maxOverlap = max(maxOverlap, other.maxLanesAtEvent)
                }
            }

            current.event.copy(
                laneIndex = current.laneIndex,
                overlapIndex = current.laneIndex,
                overlapCount = maxOverlap
            )
        }
    }
}
