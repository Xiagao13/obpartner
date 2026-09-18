package com.obpartner.app.parser

import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 极速 Frontmatter 提取引擎
 * Fast Frontmatter Scanner Engine
 *
 * 仅流式读取 Markdown 文件前部（在第二个 --- 处即刻终止），毫秒级提取 YAML 元数据，
 * 避免读取整个大文件，极大提升在手机端扫描数百篇笔记的性能。
 */
object FrontmatterScanner {

    private val yaml = Yaml()

    /**
     * 从输入流中提取 Frontmatter 键值映射
     * Extract Frontmatter key-value map from InputStream
     */
    fun scanFrontmatter(inputStream: InputStream): Map<String, Any> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()

        // 第一行必须是 --- / First line must be ---
        if (line == null || line.trim() != "---") {
            return emptyMap()
        }

        val yamlBuilder = StringBuilder()
        var foundClosing = false

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: break
            if (currentLine.trim() == "---") {
                foundClosing = true
                break
            }
            yamlBuilder.append(currentLine).append("\n")
        }

        if (!foundClosing) {
            return emptyMap()
        }

        val yamlContent = yamlBuilder.toString()
        if (yamlContent.isBlank()) return emptyMap()

        return parseYamlMap(yamlContent)
    }

    /**
     * 从完整 Markdown 文本中分离 Frontmatter 字典与正文
     * Extract Frontmatter map and body content from full Markdown text
     */
    fun extractFrontmatterAndBody(fullText: String): Pair<Map<String, Any>, String> {
        val trimmed = fullText.trimStart()
        if (!trimmed.startsWith("---")) {
            return Pair(emptyMap(), fullText)
        }

        // 寻找第二个 --- 闭合标签
        val secondDashIndex = trimmed.indexOf("\n---", startIndex = 3)
        if (secondDashIndex == -1) {
            return Pair(emptyMap(), fullText)
        }

        val yamlContent = trimmed.substring(3, secondDashIndex).trim()
        val afterClosing = trimmed.substring(secondDashIndex + 4)
        val bodyContent = if (afterClosing.startsWith("\n")) afterClosing.substring(1) else afterClosing

        val fm = parseYamlMap(yamlContent)
        return Pair(fm, bodyContent)
    }

    private fun parseYamlMap(yamlContent: String): Map<String, Any> {
        if (yamlContent.isBlank()) return emptyMap()
        return try {
            val loaded = yaml.load<Any>(yamlContent)
            if (loaded is Map<*, *>) {
                loaded.entries.associate { (k, v) ->
                    val finalVal: Any = if (v is Date) {
                        // SnakeYAML 规范默认将无时区的时间戳强制按 UTC 零时区解析，
                        // 但 Obsidian 用户笔记中记录的全部为本地设备时间 (如 10:00:00 代表上午10点而非UTC上午10点)。
                        // 此处将其还原为无时区原始时间字符串，交由系统按本地时区解析，彻底消除 8 小时时差 (避免 10:00 变成 18:00)。
                        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        utcFormat.format(v)
                    } else {
                        v ?: ""
                    }
                    k.toString() to finalVal
                }
            } else {
                parseFrontmatterFallback(yamlContent)
            }
        } catch (_: Exception) {
            parseFrontmatterFallback(yamlContent)
        }
    }

    /**
     * 降级正则解析器 (当 YAML 存在特殊格式或语法不规范时启用，确保 100% 不漏掉日程与任务)
     * Fallback regex parser for frontmatter
     */
    private fun parseFrontmatterFallback(content: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val lines = content.lines()
        val linePattern = java.util.regex.Pattern.compile("^([A-Za-z0-9_\\-\\u4e00-\\u9fa5]+)\\s*:\\s*(.*)$")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) continue

            val matcher = linePattern.matcher(line)
            if (matcher.find()) {
                val key = matcher.group(1)?.trim() ?: continue
                var value = matcher.group(2)?.trim() ?: ""
                // 去除可能的外层引号
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    if (value.length >= 2) {
                        value = value.substring(1, value.length - 1)
                    }
                }
                result[key] = value
            }
        }
        return result
    }
}

