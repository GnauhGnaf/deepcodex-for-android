package com.example.app.util

import android.util.Log
import com.example.app.domain.LinuxEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentService(
    private val linuxEnv: LinuxEnvironment
) {
    companion object {
        private const val TAG = "DocumentService"
    }

    fun isLibreOfficeInstalled(): Boolean = linuxEnv.isLibreOfficeReady

    suspend fun installLibreOffice(
        workspaceDir: File,
        onProgress: (String) -> Unit
    ): Result<String> {
        return linuxEnv.installLibreOffice(workspaceDir, onProgress)
    }

    suspend fun convertToPdf(
        file: File,
        workspaceDir: File,
        onProgress: (String) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val workspaceAbs = workspaceDir.canonicalPath
            val fileAbs = file.canonicalPath
            if (!fileAbs.startsWith(workspaceAbs)) {
                return@withContext Result.failure(
                    RuntimeException("文件不在工作区内")
                )
            }

            val relPath = fileAbs.removePrefix(workspaceAbs).removePrefix("/").removePrefix("\\")
            val prootFilePath = "/workspace/$relPath"
            val outputDir = "/workspace/.convert"
            val baseName = file.name.substringBeforeLast('.')
            val outputPath = "$outputDir/$baseName.pdf"

            execInLinux("rm -rf $outputDir && mkdir -p $outputDir", workspaceDir, 5)
            execInLinux("mkdir -p /tmp/lo_profile", workspaceDir, 3)

            val cmd = "exec soffice.bin -env:UserInstallation=file:///tmp/lo_profile --headless --convert-to pdf --outdir $outputDir \"$prootFilePath\" 2>&1"
            Log.d(TAG, "Running: $cmd")

            val result = execInLinux(cmd, workspaceDir, 120)
            Log.d(TAG, "Conversion result: exit=${result.exitCode}, out=${result.output.take(300)}")

            if (!result.success) {
                val checkCmd = "ls $outputDir/*.pdf 2>&1"
                val check = execInLinux(checkCmd, workspaceDir, 5)
                if (!check.success || !check.output.contains(".pdf")) {
                    return@withContext Result.failure(
                        RuntimeException("文档转换失败: ${result.output.take(200)}")
                    )
                }
            }

            val pdfFile = File(workspaceDir, ".convert/$baseName.pdf")
            if (!pdfFile.exists()) {
                val lsResult = execInLinux("ls $outputDir/ 2>&1", workspaceDir, 5)
                Log.d(TAG, "Output dir contents: ${lsResult.output}")
                return@withContext Result.failure(
                    RuntimeException("转换后的 PDF 文件未找到")
                )
            }

            Result.success(pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "conversion failed", e)
            Result.failure(e)
        }
    }

    private suspend fun execInLinux(
        cmd: String,
        workspaceDir: File,
        timeoutSeconds: Long
    ): LinuxEnvironment.ProcessResult {
        return linuxEnv.execCommand(cmd, workspaceDir, timeoutSeconds)
    }
}
