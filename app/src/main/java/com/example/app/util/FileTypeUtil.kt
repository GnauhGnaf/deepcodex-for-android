package com.example.app.util

import java.io.File

enum class FileCategory {
    TEXT, IMAGE, PDF, DOCX, XLSX, PPTX, VIDEO, UNKNOWN
}

object FileTypeUtil {
    private val textExtensions = setOf(
        "txt", "md", "csv", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf",
        "c", "cpp", "cxx", "h", "hpp", "py", "java", "kt", "kts", "js", "ts", "jsx", "tsx",
        "html", "htm", "css", "scss", "less", "sh", "bash", "zsh",
        "rs", "go", "rb", "php", "swift", "sql", "gradle", "properties",
        "cmake", "makefile", "dockerfile", "gitignore", "env", "log", "diff", "patch"
    )

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
    private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "webm", "3gp", "flv", "m4v")

    fun categorize(file: File): FileCategory {
        val ext = file.extension.lowercase()
        return when {
            ext == "pdf" -> FileCategory.PDF
            ext == "docx" || ext == "doc" -> FileCategory.DOCX
            ext == "xlsx" || ext == "xls" -> FileCategory.XLSX
            ext == "pptx" || ext == "ppt" -> FileCategory.PPTX
            ext in textExtensions -> FileCategory.TEXT
            ext in imageExtensions -> FileCategory.IMAGE
            ext in videoExtensions -> FileCategory.VIDEO
            else -> FileCategory.UNKNOWN
        }
    }
}
