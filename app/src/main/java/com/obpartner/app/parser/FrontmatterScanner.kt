package com.obpartner.app.parser

import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

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

        return try {
            val loaded = yaml.load<Any>(yamlContent)
            if (loaded is Map<*, *>) {
                loaded.entries.associate { (k, v) ->
                    k.toString() to (v ?: "")
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

