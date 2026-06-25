package com.example.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.domain.LinuxEnvironment
import com.example.app.util.DocumentService
import com.example.app.util.FileExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

@Composable
fun OfficeViewerDialog(
    file: File,
    ext: String,
    onDismiss: () -> Unit,
    linuxEnv: LinuxEnvironment? = null,
    workspaceDir: File? = null
) {
    val context = LocalContext.current

    // PDF rendering state
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Fallback state
    var html by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun renderPage(pageIndex: Int) {
        val r = renderer ?: return
        if (pageIndex < 0 || pageIndex >= r.pageCount) return
        val page = r.openPage(pageIndex)
        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        pageBitmap?.recycle()
        pageBitmap = bmp
        currentPage = pageIndex
    }

    // Cleanup PDF resources
    fun cleanupPdf() {
        pageBitmap?.recycle()
        pageBitmap = null
        renderer?.close()
        renderer = null
        fd?.close()
        fd = null
        pdfFile?.delete()
        pdfFile = null
    }

    LaunchedEffect(file) {
        if (linuxEnv != null && linuxEnv.isReady && workspaceDir != null) {
            val docSvc = DocumentService(linuxEnv)

            if (docSvc.isLibreOfficeInstalled()) {
                val result = docSvc.convertToPdf(file, workspaceDir)
                if (result.isSuccess) {
                    val pf = result.getOrThrow()
                    pdfFile = pf
                    withContext(Dispatchers.IO) {
                        try {
                            val descriptor = ParcelFileDescriptor.open(pf, ParcelFileDescriptor.MODE_READ_ONLY)
                            fd = descriptor
                            val r = PdfRenderer(descriptor)
                            renderer = r
                            pageCount = r.pageCount
                            if (pageCount > 0) {
                                val page = r.openPage(0)
                                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                pageBitmap = bmp
                            }
                        } catch (e: Exception) {
                            error = "无法打开文档: ${e.message}"
                        }
                    }
                    loading = false
                    return@LaunchedEffect
                }
                // Fall through to basic parsing on conversion failure
            } else {
                error = "LibreOffice 未安装。点击下方按钮安装（约200MB），或使用基础模式查看。"
                loading = false
                return@LaunchedEffect
            }
        }

        // Fallback: basic XML parsing
        withContext(Dispatchers.IO) {
            try {
                html = when (ext.lowercase()) {
                    "docx", "doc" -> renderDocxBasic(file)
                    "xlsx", "xls" -> renderXlsxBasic(file)
                    "pptx", "ppt" -> renderPptxBasic(file)
                    else -> "<p>不支持的文件格式</p>"
                }
            } catch (e: Exception) {
                error = "无法解析文档: ${e.message}"
            }
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose { cleanupPdf() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            ext.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { FileExportUtil.exportFile(context, file) }) {
                        Text("下载", fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                HorizontalDivider()

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在加载…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (error!!.contains("未安装"))
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error
                            )
                            if (error!!.contains("未安装") && linuxEnv != null && workspaceDir != null) {
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = {
                                        loading = true
                                        error = null
                                        scope.launch {
                                            val docSvc = DocumentService(linuxEnv)
                                            val installResult = docSvc.installLibreOffice(workspaceDir) {}
                                            if (installResult.isSuccess) {
                                                val convertResult = docSvc.convertToPdf(file, workspaceDir)
                                                if (convertResult.isSuccess) {
                                                    val pf = convertResult.getOrThrow()
                                                    pdfFile = pf
                                                    withContext(Dispatchers.IO) {
                                                        try {
                                                            val descriptor = ParcelFileDescriptor.open(pf, ParcelFileDescriptor.MODE_READ_ONLY)
                                                            fd = descriptor
                                                            val r = PdfRenderer(descriptor)
                                                            renderer = r
                                                            pageCount = r.pageCount
                                                            if (pageCount > 0) {
                                                                val page = r.openPage(0)
                                                                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                                page.close()
                                                                pageBitmap = bmp
                                                            }
                                                        } catch (e: Exception) {
                                                            error = "无法打开文档: ${e.message}"
                                                        }
                                                    }
                                                    loading = false
                                                } else {
                                                    error = "转换失败: ${convertResult.exceptionOrNull()?.message}"
                                                    loading = false
                                                }
                                            } else {
                                                error = installResult.exceptionOrNull()?.message ?: "安装失败"
                                                loading = false
                                            }
                                        }
                                    }) {
                                        Text("安装 LibreOffice")
                                    }
                                    TextButton(onClick = {
                                        error = null
                                        loading = true
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    html = when (ext.lowercase()) {
                                                        "docx", "doc" -> renderDocxBasic(file)
                                                        "xlsx", "xls" -> renderXlsxBasic(file)
                                                        "pptx", "ppt" -> renderPptxBasic(file)
                                                        else -> ""
                                                    }
                                                } catch (e: Exception) {
                                                    error = "解析失败: ${e.message}"
                                                }
                                            }
                                            loading = false
                                        }
                                    }) {
                                        Text("基础模式")
                                    }
                                }
                            }
                        }
                    }
                } else if (pageBitmap != null) {
                    // PDF page display
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "第 ${currentPage + 1} 页",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Page navigation
                    if (pageCount > 1) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { renderPage(currentPage - 1) },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一页")
                            }
                            Text(
                                "${currentPage + 1} / $pageCount",
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick = { renderPage(currentPage + 1) },
                                enabled = currentPage < pageCount - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "下一页")
                            }
                        }
                    }
                } else if (html.isNotEmpty()) {
                    // Fallback WebView rendering
                    AndroidView(
                        factory = {
                            WebView(it).apply {
                                setBackgroundColor(0xFFFFFFFF.toInt())
                                isVerticalScrollBarEnabled = true
                                settings.apply {
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    javaScriptEnabled = false
                                    textZoom = 150
                                }
                            }
                        },
                        update = { wv -> wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── Basic fallback renderers ─────────────────────────────────

private fun renderDocxBasic(file: File): String {
    val sb = StringBuilder()
    ZipInputStream(file.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                parseDocxXml(zis.readBytes().toString(Charsets.UTF_8), sb)
                break
            }
            entry = zis.nextEntry
        }
    }
    val body = sb.toString().ifEmpty { "<p class=\"empty\">文档无文本内容</p>" }
    return wrapBasicHtml("DOCX", body)
}

private fun parseDocxXml(xml: String, sb: StringBuilder) {
    val paraRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
    val runRegex = Regex("<w:r[ >](.*?)</w:r>", RegexOption.DOT_MATCHES_ALL)
    val textRegex = Regex("<w:t[^>]*>([^<]*)</w:t>")
    val styleRegex = Regex("<w:pStyle[^>]*w:val=\"([^\"]*)\"")
    val boldRegex = Regex("<w:b[ />]")
    val italicRegex = Regex("<w:i[ />]")
    val numPrRegex = Regex("<w:numPr[ >]")

    for (para in paraRegex.findAll(xml)) {
        val paraXml = para.groupValues[1]
        val style = styleRegex.find(paraXml)?.groupValues?.get(1) ?: ""
        val isList = numPrRegex.containsMatchIn(paraXml)
        val runs = runRegex.findAll(paraXml).toList()
        if (runs.isEmpty()) { sb.append("<p class=\"spacer\"></p>"); continue }

        val paraText = StringBuilder()
        for (run in runs) {
            val runXml = run.groupValues[1]
            val isBold = boldRegex.containsMatchIn(runXml)
            val isItalic = italicRegex.containsMatchIn(runXml)
            val text = textRegex.findAll(runXml).map { it.groupValues[1].decodeXml() }.joinToString("")
            if (text.isBlank()) continue
            var wrapped = text
            if (isBold && isItalic) wrapped = "<b><i>$wrapped</i></b>"
            else if (isBold) wrapped = "<b>$wrapped</b>"
            else if (isItalic) wrapped = "<i>$wrapped</i>"
            paraText.append(wrapped)
        }

        val text = paraText.toString().trim()
        if (text.isEmpty()) { sb.append("<p class=\"spacer\"></p>"); continue }
        when {
            isList -> sb.append("<p class=\"list\"><span class=\"bullet\">•</span>$text</p>")
            style.startsWith("Heading") -> {
                val level = style.removePrefix("Heading").toIntOrNull()?.coerceIn(1..6) ?: 1
                sb.append("<h$level>$text</h$level>")
            }
            else -> sb.append("<p>$text</p>")
        }
    }
}

private fun renderXlsxBasic(file: File): String {
    val sharedStrings = mutableListOf<String>()
    var tableHtml = ""
    ZipInputStream(file.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            when {
                entry.name == "xl/sharedStrings.xml" -> {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    Regex("<t[^>]*>([^<]*)</t>").findAll(xml)
                        .map { it.groupValues[1].decodeXml() }
                        .forEach { sharedStrings.add(it) }
                }
                entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") && tableHtml.isEmpty() -> {
                    tableHtml = parseSheetXml(zis.readBytes().toString(Charsets.UTF_8), sharedStrings)
                }
            }
            entry = zis.nextEntry
        }
    }
    if (tableHtml.isEmpty()) tableHtml = "<p class=\"empty\">无法提取表格数据</p>"
    return wrapBasicHtml("XLSX", tableHtml)
}

private fun parseSheetXml(xml: String, sharedStrings: List<String>): String {
    val sb = StringBuilder("<table>")
    val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    val cellRegex = Regex("<c[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
    val valueRegex = Regex("<v>(.*?)</v>")
    val typeRegex = Regex("t=\"([^\"]*)\"")
    for (row in rowRegex.findAll(xml)) {
        sb.append("<tr>")
        for (cell in cellRegex.findAll(row.groupValues[1])) {
            val cellXml = cell.groupValues[1]
            val type = typeRegex.find(cellXml)?.groupValues?.get(1) ?: ""
            val value = valueRegex.find(cellXml)?.groupValues?.get(1) ?: ""
            val display = when {
                type == "s" -> sharedStrings.getOrElse(value.toIntOrNull() ?: -1) { "" }
                type == "b" -> if (value == "1") "TRUE" else "FALSE"
                else -> value
            }
            sb.append("<td>${display.encodeHtml()}</td>")
        }
        sb.append("</tr>")
    }
    sb.append("</table>")
    return sb.toString()
}

private fun renderPptxBasic(file: File): String {
    val slides = mutableMapOf<Int, String>()
    ZipInputStream(file.inputStream()).use { zis ->
        var entry = zis.nextEntry
        val slideRegex = Regex("ppt/slides/slide(\\d+)\\.xml")
        while (entry != null) {
            val match = slideRegex.find(entry.name)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 0
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                val text = Regex("<a:t[^>]*>([^<]*)</a:t>").findAll(xml)
                    .map { it.groupValues[1].decodeXml() }.joinToString(" ").trim()
                if (text.isNotBlank()) slides[num] = text
            }
            entry = zis.nextEntry
        }
    }
    if (slides.isEmpty()) return wrapBasicHtml("PPTX", "<p class=\"empty\">无法提取幻灯片内容</p>")
    val sb = StringBuilder()
    slides.toSortedMap().forEach { (num, text) ->
        sb.append("<div class=\"slide\"><div class=\"slide-num\">幻灯片 $num</div>")
        text.split("  ").filter { it.isNotBlank() }.forEach { sb.append("<p>$it</p>") }
        sb.append("</div>")
    }
    return wrapBasicHtml("PPTX", sb.toString())
}

// ── HTML helpers ──────────────────────────────────────────────

private fun wrapBasicHtml(type: String, body: String): String = """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#fff;color:#1a1a1a;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.6;padding:16px 14px 40px;-webkit-text-size-adjust:none}
h1{font-size:1.6em;font-weight:700;margin:14px 0 6px;color:#111}
h2{font-size:1.4em;font-weight:600;margin:12px 0 4px;color:#222}
h3{font-size:1.2em;font-weight:600;margin:10px 0 4px;color:#333}
h4,h5,h6{font-size:1.1em;font-weight:600;margin:8px 0 4px;color:#444}
p{margin:0 0 6px;text-align:justify}
p.spacer{height:8px}
p.list{padding-left:16px;text-indent:-12px;margin-left:4px}
.bullet{color:#666;margin-right:6px}
.empty{color:#888;font-style:italic;text-align:center;padding:40px 0}
table{border-collapse:collapse;width:100%;margin:8px 0;font-size:13px;overflow-x:auto;display:block}
tr:nth-child(even){background:#f5f5f5}
tr:nth-child(odd){background:#fff}
td{border:1px solid #d0d0d0;padding:5px 8px;min-width:40px;white-space:nowrap}
tr:first-child td{background:#e8e8e8;font-weight:600;border-color:#c0c0c0}
.slide{border:1px solid #e0e0e0;border-radius:8px;padding:16px;margin:12px 0;background:#fafafa;box-shadow:0 1px 3px rgba(0,0,0,0.06)}
.slide-num{font-size:12px;color:#888;font-weight:600;margin-bottom:8px;text-transform:uppercase;letter-spacing:.5px}
</style></head>
<body><div class="notice" style="background:#fff3cd;border:1px solid #ffc107;padding:8px 12px;border-radius:6px;margin-bottom:12px;font-size:12px;color:#856404">
&#9888; 基础渲染模式 — 建议安装 LibreOffice 获得完整格式支持
</div>$body</body></html>
""".trimIndent()

private fun String.encodeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun String.decodeXml(): String =
    replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&#10;", "\n").replace("&#13;", "\r")
