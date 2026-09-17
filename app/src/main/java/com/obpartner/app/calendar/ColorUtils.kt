package com.obpartner.app.calendar

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 颜色生成与主题感知工具类 (1:1 复刻 Freepace stringToColor 算法)
 * Color Generation and Theme-Aware Utility (Faithfully ported 1:1 from Freepace stringToColor)
 */
object ColorUtils {

    /**
     * 根据字符串（分类、标签、学生名等）生成一致且具有美感的主题颜色
     * Generate a consistent color from a string (Theme Aware)
     *
     * @param str 输入字符串 (如 "宋雨宸" 或 "#FF5722") / Input string or hex color
     * @param isDark 是否为暗黑模式 / Dark mode flag
     * @param mode "bg" (柔和背景色), "text" (高对比度文字色), "border" (边框色)
     */
    fun stringToColor(str: String, isDark: Boolean = true, mode: String = "bg"): Color {
        if (str.isBlank() || str.equals("default", ignoreCase = true)) {
            return when (mode) {
                "text" -> if (isDark) Color(0xFFECEFF1) else Color(0xFF263238)
                "border" -> if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
                else -> if (isDark) Color(0xFF2E2F45) else Color(0xFFEDE7F6)
            }
        }

        // 1. 如果是显式十六进制颜色 (如 #7C4DFF)
        if (str.startsWith("#") && (str.length == 7 || str.length == 9)) {
            try {
                val parsed = Color(android.graphics.Color.parseColor(str))
                return when (mode) {
                    "text" -> parsed
                    "border" -> parsed
                    else -> parsed.copy(alpha = if (isDark) 0.25f else 0.15f)
                }
            } catch (_: Exception) {
            }
        }

        // 2. 哈希算法计算色相 HSL (Notion / Linear 同款美学算法)
        var hash = 0
        for (ch in str) {
            hash = ch.code + ((hash shl 5) - hash)
        }
        val h = (abs(hash) % 360).toFloat()

        return when (mode) {
            "text" -> {
                val s = 0.65f
                val l = if (isDark) 0.85f else 0.35f
                hslToColor(h, s, l)
            }
            "border" -> {
                val s = 0.50f
                val l = if (isDark) 0.45f else 0.65f
                hslToColor(h, s, l)
            }
            else -> { // "bg"
                val s = 0.55f
                val l = if (isDark) 0.22f else 0.92f
                hslToColor(h, s, l)
            }
        }
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (rPrime, gPrime, bPrime) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(
            red = (rPrime + m).coerceIn(0f, 1f),
            green = (gPrime + m).coerceIn(0f, 1f),
            blue = (bPrime + m).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}
