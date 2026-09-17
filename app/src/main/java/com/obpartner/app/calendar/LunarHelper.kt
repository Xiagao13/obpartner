package com.obpartner.app.calendar

import com.nlf.calendar.HolidayUtil
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import com.obpartner.app.model.LunarDetails
import java.util.Calendar
import java.util.Date

/**
 * 农历与节气节假日计算辅助类 (1:1 精确复刻 Freepace getLunarDetails)
 * Lunar and Solar Term Helper (Faithfully ported 1:1 from Freepace getLunarDetails)
 */
object LunarHelper {

    /**
     * 获取指定日期的农历、节气与节假日详细信息
     * Get Lunar Date, Term, Festival and Holiday information for a specific date
     *
     * @param date 公历日期 / Gregorian date
     * @return 农历详情包装对象 / Lunar details wrapper
     */
    fun getLunarDetails(date: Date): LunarDetails {
        val lunar = Lunar.fromDate(date)
        val solar = Solar.fromDate(date)

        val festivals = lunar.festivals
        val solarFestivals = solar.festivals
        val jieQi = lunar.jieQi

        var text = ""

        // 1. 节日优先级最高 / Festivals have highest priority
        if (festivals.isNotEmpty()) {
            text = festivals[0]
        } else if (solarFestivals.isNotEmpty()) {
            text = solarFestivals[0]
        }
        // 2. 二十四节气 / Solar Terms
        else if (!jieQi.isNullOrEmpty()) {
            text = jieQi
        }
        // 3. 农历日/月（初一显示月份，其余显示初几或廿几） / Lunar Day or Month
        else {
            val dayInChinese = lunar.dayInChinese
            text = if (dayInChinese == "初一") {
                lunar.monthInChinese + "月"
            } else {
                dayInChinese
            }
        }

        // 4. 国家法定调休与放假检查 / Official Work/Holiday Status
        val cal = Calendar.getInstance().apply { time = date }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val holiday = HolidayUtil.getHoliday(year, month, day)
        val holidayStatus = when {
            holiday == null -> null
            holiday.isWork -> "work"     // 调休上班（显示“班”）
            else -> "holiday"            // 法定放假（显示“休”）
        }

        val fullLunar = "${lunar.yearInGanZhi}年${lunar.monthInChinese}月${lunar.dayInChinese}"

        return LunarDetails(
            text = text,
            holidayStatus = holidayStatus,
            fullLunarDate = fullLunar
        )
    }
}
