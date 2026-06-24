package com.example.app.domain

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import com.example.app.data.api.models.ToolDef
import com.example.app.data.api.models.FunctionSpec

object ToolDefinitions {

    val allTools: List<ToolDef> = listOf(
        readFileTool, writeFileTool, listFilesTool, searchFilesTool, runCommandTool
    )

    val readFileTool: ToolDef
        get() = ToolDef(
            function = FunctionSpec(
                name = "read_file",
                description = "读取指定文件的内容",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("文件路径"))
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("path")) }
                }
            )
        )

    val writeFileTool: ToolDef
        get() = ToolDef(
            function = FunctionSpec(
                name = "write_file",
                description = "创建或覆盖文件",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("文件路径"))
                        }
                        putJsonObject("content") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("文件内容"))
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("path"))
                        add(JsonPrimitive("content"))
                    }
                }
            )
        )

    val listFilesTool: ToolDef
        get() = ToolDef(
            function = FunctionSpec(
                name = "list_files",
                description = "列出目录中的文件和子目录",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("目录路径，留空为根目录"))
                        }
                    }
                }
            )
        )

    val searchFilesTool: ToolDef
        get() = ToolDef(
            function = FunctionSpec(
                name = "search_files",
                description = "用 glob 模式搜索文件，如 **/*.kt",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("pattern") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("glob 模式"))
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("pattern")) }
                }
            )
        )

    val runCommandTool: ToolDef
        get() = ToolDef(
            function = FunctionSpec(
                name = "run_command",
                description = "在工作区目录执行 Shell 命令",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("command") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("要执行的 Shell 命令"))
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("command")) }
                }
            )
        )
}
