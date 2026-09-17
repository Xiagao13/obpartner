package com.obpartner.app.model

/**
 * 农历与节气节假日详情信息
 * Lunar, Solar Terms and Holiday Details (Matches Freepace getLunarDetails output)
 *
 * @param text 展示文本（节日 > 节气 > 农历初几/月份） / Display text (Festival > Term > Day/Month)
 * @param holidayStatus 调休工作日/放假状态 ("work" 表示班, "holiday" 表示休, null 表示普通日) / Holiday status
 * @param fullLunarDate 完整农历日期文本 (如 "甲辰年八月十五") / Full Chinese lunar date string
 */
data class LunarDetails(
    val text: String,
    val holidayStatus: String? = null,
    val fullLunarDate: String = ""
)
