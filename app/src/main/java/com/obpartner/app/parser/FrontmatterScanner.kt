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
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
