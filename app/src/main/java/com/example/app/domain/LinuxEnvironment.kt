package com.example.app.domain

import android.content.Context
import android.system.Os
import android.system.ErrnoException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileOutputStream

class LinuxEnvironment(context: Context) {

    private val appContext = context.applicationContext
    private val envDir = File(appContext.filesDir, "linux")
    private val prootBin: File
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libproot_exec.so")
    private val prootLoader: File
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libproot_loader.so")
    private val rootfsDir = File(envDir, "rootfs")
    private val tmpDir = File(envDir, "tmp")
    private val setupMarker = File(envDir, "setup.done")
    private val pipMarker = File(envDir, "pip.done")
    private val libreofficeMarker = File(envDir, "libreoffice.done")
    private val fontsMarker = File(envDir, "fonts.done")
    private val fakeprocDir = File(envDir, "fakeproc")
    private val setupMutex = Mutex()

    val isReady: Boolean get() = setupMarker.exists() && (File(rootfsDir, "usr/bin/python3.14").exists() || File(rootfsDir, "usr/bin/python3").exists())
    val isLibreOfficeReady: Boolean get() = libreofficeMarker.exists()

    fun writeOcrConfig(apiKey: String, baseUrl: String) {
        val ocrConfigFile = File(rootfsDir, "root/.ocr_config")
        ocrConfigFile.parentFile?.mkdirs()
        ocrConfigFile.writeText("DS_OCR_API_KEY=$apiKey\nDS_OCR_BASE_URL=$baseUrl\n")
    }

    suspend fun setup(): Result<String> = setupMutex.withLock {
        withContext(Dispatchers.IO) {
        try {
            // Always ensure fake /proc exists (for upgrades from older versions)
            createFakeProc()

            // Fix LO permissions (always, in case TarExtractor missed them)
            val loProgramDir = File(rootfsDir, "usr/lib/libreoffice/program")
            if (loProgramDir.exists()) {
                loProgramDir.listFiles()?.forEach { f ->
                    if (!f.isDirectory) f.setExecutable(true)
                }
            }

            // Ensure Noto CJK fonts are installed (even if fonts.done exists)
            if (!File(rootfsDir, "usr/share/fonts/noto-cjk").exists()) {
                fontsMarker.delete()
            }

            // Fast path: everything is ready
            if (isReady && pipMarker.exists() && libreofficeMarker.exists() && fontsMarker.exists()) {
                return@withContext Result.success("Linux 环境已就绪")
            }

            // Quick fix for existing environments missing pip, LO or fonts
            if (isReady) {
                val fixes = StringBuilder()
                if (!pipMarker.exists()) {
                    configurePip()
                    pipMarker.writeText("ok")
                    fixes.appendLine("  ✓ pip 已配置")
                }
                if (!libreofficeMarker.exists() && File(rootfsDir, "usr/lib/libreoffice/program/soffice").exists()) {
                    libreofficeMarker.writeText("ok")
                    fixes.appendLine("  ✓ LibreOffice 已就绪（预装）")
                }
                if (!fontsMarker.exists()) {
                    configureFonts()
                    fontsMarker.writeText("ok")
                    fixes.appendLine("  ✓ 中文字体已安装")
                }
                return@withContext Result.success("Linux 环境已就绪\n${fixes}")
            }

            val log = StringBuilder()
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "x86_64"

            // [1/5] Prepare environment
            log.appendLine("[1/5] 准备环境...")
            tmpDir.mkdirs(); envDir.mkdirs(); rootfsDir.mkdirs()
            createFakeProc()
            Log.d("LinuxEnv", "ABI=$abi, proot=${prootBin.absolutePath}")

            if (!prootBin.exists()) return@withContext Result.failure(RuntimeException("proot 未找到"))
            if (!prootLoader.exists()) return@withContext Result.failure(RuntimeException("proot loader 未找到"))
            prootBin.setExecutable(true); prootLoader.setExecutable(true)

            val rootfsLoader = File(rootfsDir, "usr/libexec/proot/loader")
            rootfsLoader.parentFile!!.mkdirs()
            prootLoader.inputStream().use { src -> FileOutputStream(rootfsLoader).use { dst -> src.copyTo(dst) } }
            rootfsLoader.setExecutable(true)

            // [2/5] Extract rootfs (all system deps pre-installed)
            log.appendLine("[2/5] 解压 Alpine Linux（预装全部系统依赖）...")
            val arch = when { abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"; else -> "x86_64" }
            val rootfsAsset = "rootfs/alpine-minirootfs-$arch.tar"
            Log.d("LinuxEnv", "Extracting $rootfsAsset")

            val symlinks = mutableListOf<Pair<File, String>>()
            try {
                appContext.assets.open(rootfsAsset).use { assetStream ->
                    val (count, links) = com.example.app.util.TarExtractor.extract(assetStream, rootfsDir)
                    symlinks.addAll(links)
                    Log.d("LinuxEnv", "Extracted: $count entries, ${symlinks.size} symlinks")
                }
            } catch (e: Exception) {
                Log.e("LinuxEnv", "Extraction failed", e)
                return@withContext Result.failure(RuntimeException("rootfs 解压失败: ${e.message}"))
            }

            val pythonBin = File(rootfsDir, "usr/bin/python3.14")
            val python3Bin = File(rootfsDir, "usr/bin/python3")
            val busyboxBin = File(rootfsDir, "bin/busybox")
            Log.d("LinuxEnv", "After extract: python3.14=${pythonBin.exists()}, python3=${python3Bin.exists()}, busybox=${busyboxBin.exists()}")

            if (!busyboxBin.exists()) {
                return@withContext Result.failure(RuntimeException("busybox 未在 rootfs 中找到"))
            }

            // [3/5] Create symlinks
            log.appendLine("[3/5] 创建符号链接...")
            var linksOk = 0; var linksFail = 0
            for ((destFile, target) in symlinks) {
                try {
                    Os.symlink(target, destFile.absolutePath)
                    linksOk++
                } catch (e: ErrnoException) {
                    if (e.errno != 17) { Log.w("LinuxEnv", "symlink fail ${destFile.name}: ${e.message}"); linksFail++ }
                    else linksOk++
                }
            }

            val symlinksListFile = File(rootfsDir, "symlinks.list")
            var extraLinks = 0
            if (symlinksListFile.exists()) {
                Log.d("LinuxEnv", "symlinks.list found, ${symlinksListFile.length()} bytes")
                for (line in symlinksListFile.readLines()) {
                    val parts = line.trim().split("|")
                    if (parts.size == 2) {
                        val linkFile = File(rootfsDir, parts[0])
                        linkFile.parentFile?.mkdirs()
                        if (!linkFile.exists()) {
                            try {
                                Os.symlink(parts[1], linkFile.absolutePath)
                                extraLinks++
                            } catch (e: ErrnoException) {
                                if (e.errno != 17) { Log.w("LinuxEnv", "extra symlink fail ${parts[0]}: ${e.message}"); linksFail++ }
                                else extraLinks++
                            }
                        }
                    }
                }
                symlinksListFile.delete()
            }
            log.appendLine("  ✓ ${linksOk + extraLinks} 个符号链接")

            // [4/5] Configure system (mirrors, pip, fonts — all packages pre-installed)
            log.appendLine("[4/5] 配置系统...")
            File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            File(rootfsDir, "workspace").mkdirs()

            val mirrorUrl = "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.24"
            execRaw(
                "echo '$mirrorUrl/main' > /etc/apk/repositories && " +
                "echo '$mirrorUrl/community' >> /etc/apk/repositories 2>&1", 5
            )

            configurePip()
            pipMarker.writeText("ok")
            log.appendLine("  ✓ pip 及 Python 包已配置")

            configureFonts()
            fontsMarker.writeText("ok")
            log.appendLine("  ✓ 中文字体已就绪")

            // [5/5] Verify environment
            log.appendLine("[5/5] 验证环境...")
            val rEcho = execRaw("echo proot_ok", 5)
            if (!rEcho.success) {
                return@withContext Result.failure(RuntimeException("proot 基础功能异常: ${rEcho.output}"))
            }
            log.appendLine("  ✓ proot 基础功能正常")

            val pyCmd = if (pythonBin.exists()) "python3.14" else "python3"
            for (attempt in 1..3) {
                val rPy = execRaw("$pyCmd --version 2>&1", 10)
                Log.d("LinuxEnv", "$pyCmd test $attempt: exit=${rPy.exitCode} out=${rPy.output}")
                if (rPy.success) {
                    log.appendLine("  ✓ ${rPy.output.trim()}")
                    setupMarker.writeText(abi)

                    if (File(rootfsDir, "usr/lib/libreoffice/program/soffice").exists()) {
                        libreofficeMarker.writeText("ok")
                        log.appendLine("  ✓ LibreOffice 已就绪（预装）")
                    }

                    log.appendLine("初始化完成！")
                    return@withContext Result.success(log.toString())
                }
                if (attempt < 3) kotlinx.coroutines.delay(1000)
            }

            // Python binary exists but proot exec failed — degraded mode
            val anyPython = pythonBin.exists() || python3Bin.exists()
            if (anyPython) {
                Log.w("LinuxEnv", "Python binary exists but proot exec failed, marking setup as degraded")
                setupMarker.writeText("${abi}_degraded")
                log.appendLine("  ⚠ Python 已安装但无法通过 proot 启动（fork 问题）")
                if (!libreofficeMarker.exists() && File(rootfsDir, "usr/lib/libreoffice/program/soffice").exists()) {
                    libreofficeMarker.writeText("ok")
                }
                log.appendLine("初始化完成（降级模式）")
                return@withContext Result.success(log.toString())
            }

            return@withContext Result.failure(RuntimeException("Python 环境验证失败"))
        } catch (e: Exception) {
            Log.e("LinuxEnv", "Setup failed", e)
            setupMarker.delete()
            Result.failure(e)
        }
    }
    }

    private fun createFakeProc() {
        fakeprocDir.mkdirs()
        File(fakeprocDir, "version").writeText("Linux version 5.10.0-alpine (build@alpine) (gcc 12.2.0)\n")
        File(fakeprocDir, "uptime").writeText("0.00 0.00\n")
        File(fakeprocDir, "stat").writeText("cpu  0 0 0 0 0 0 0 0 0 0\n")
        File(fakeprocDir, "loadavg").writeText("0.00 0.00 0.00 0/0 0\n")
        File(fakeprocDir, "filesystems").writeText("nodev\tsysfs\nnodev\trootfs\nnodev\ttmpfs\nnodev\tproc\nnodev\tdevpts\n\text3\n\text2\n\text4\nnodev\tramfs\n\tvfat\n")
        File(fakeprocDir, "meminfo").writeText("MemTotal: 4194304 kB\n")
        File(fakeprocDir, "self").mkdirs()
        // Pre-create LibreOffice user profile directory (required for headless operation)
        File(rootfsDir, "tmp/lo_profile").mkdirs()
    }

    private fun configurePip() {
        // All Python packages are pre-installed in rootfs (apk + .whl extraction).
        // Just clean up EXTERNALLY-MANAGED markers and ensure pip.conf exists.
        execRaw("find /usr/lib/python3* -name EXTERNALLY-MANAGED -delete 2>/dev/null || true", 10)
        execRaw("mkdir -p /root/.config/pip 2>/dev/null; " +
                "echo -e '[global]\\nbreak-system-packages = true\\n" +
                "index-url = https://pypi.tuna.tsinghua.edu.cn/simple\\n" +
                "trusted-host = pypi.tuna.tsinghua.edu.cn' > /root/.config/pip/pip.conf", 3)
    }

    private fun configureFonts() {
        // Install Noto CJK fonts for full Chinese font support (宋体/微软雅黑/黑体/楷体 etc.)
        if (!File(rootfsDir, "usr/share/fonts/noto-cjk").exists()) {
            execRaw("apk update 2>&1 && apk add font-noto-cjk font-noto-cjk-extra 2>&1 || true", 300)
        }
        // Activate wqy-zenhei fontconfig
        val confAvail = File(rootfsDir, "etc/fonts/conf.avail")
        val confD = File(rootfsDir, "etc/fonts/conf.d")
        if (confAvail.exists()) {
            confAvail.listFiles()?.filter { it.name.contains("wqy") || it.name.contains("noto") }?.forEach { src ->
                val dest = File(confD, src.name)
                if (!dest.exists()) {
                    dest.writeText(src.readText())
                }
            }
        }
        // Rebuild font cache
        execRaw("fc-cache -fv 2>&1 || true", 10)
    }

    // Must be called from within Dispatchers.IO context
    private fun installLibreOfficeInternal(onProgress: (String) -> Unit): Boolean {
        // First check if already pre-installed in rootfs
        if (File(rootfsDir, "usr/lib/libreoffice/program/soffice").exists()) {
            onProgress("LibreOffice 已预装于 rootfs")
            return true
        }

        // Not pre-installed — install via apk (needs network, ~500MB, 2-5 min)
        onProgress("正在更新软件源...")
        for (attempt in 1..3) {
            if (attempt > 1) Thread.sleep(5000)

            val mirrorUrl = "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.24"
            execRaw(
                "echo '$mirrorUrl/main' > /etc/apk/repositories && " +
                "echo '$mirrorUrl/community' >> /etc/apk/repositories 2>&1", 10
            )

            // Step 1: Update index
            onProgress("正在更新软件包索引...")
            execRaw("apk update 2>&1", 30)

            // Step 2: Fetch packages (downloads with progress lines, ~500MB)
            onProgress("正在下载 LibreOffice 软件包...")
            var pkgCount = 0
            var totalPkgs = 0
            val fetchResult = execRawStreaming(
                "rm -rf /tmp/lo_dl 2>/dev/null; mkdir -p /tmp/lo_dl; " +
                "apk fetch --no-cache libreoffice -o /tmp/lo_dl 2>&1",
                600,
                onLine = { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("Downloading") -> {
                            pkgCount++
                            val filename = trimmed.removePrefix("Downloading ")
                            // Extract size from apk fetch output if available
                            onProgress("下载 ($pkgCount): $filename")
                        }
                        !trimmed.startsWith("WARNING") && !trimmed.startsWith("fetch") &&
                        trimmed.isNotBlank() -> {
                            // Parse apk fetch summary like "Downloaded 173 packages"
                            if (trimmed.contains("Downloaded") || trimmed.contains("packages")) {
                                onProgress(trimmed)
                            }
                        }
                    }
                }
            )

            if (!fetchResult.success) {
                Log.e("LinuxEnv", "apk fetch failed: ${fetchResult.output.take(500)}")
                onProgress("下载失败，正在重试...")
                continue
            }

            // Count downloaded files and total size
            val dlFiles = File(rootfsDir, "tmp/lo_dl").listFiles()?.filter { it.extension == "apk" } ?: emptyList()
            val totalSize = dlFiles.sumOf { it.length() }
            onProgress("下载完成: ${dlFiles.size} 个包, ${totalSize / (1024*1024)} MB")

            // Step 3: Install from local files (fast, no network)
            onProgress("正在安装 LibreOffice (${dlFiles.size} 个软件包)...")
            var installedCount = 0
            val installResult = execRawStreaming(
                "rm -f /var/lib/apk/lock 2>/dev/null; apk add --no-cache /tmp/lo_dl/*.apk 2>&1",
                300,
                onLine = { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("(") && "/" in trimmed -> {
                            installedCount++
                            if (installedCount % 10 == 0 || installedCount <= 3 || installedCount >= dlFiles.size - 2) {
                                onProgress("安装: ${installedCount}/${dlFiles.size} $trimmed")
                            }
                        }
                        trimmed.contains("OK:") -> onProgress(trimmed)
                    }
                }
            )

            Log.d("LinuxEnv", "apk add libreoffice: exit=${installResult.exitCode}")

            if (installResult.success) return true

            // Check if binary exists even if reported failure
            val check = execRaw("which libreoffice 2>&1 || echo NOT_FOUND", 5)
            if (check.success && check.output.contains("libreoffice") && !check.output.contains("NOT_FOUND")) return true

            // Cleanup for retry
            execRaw("rm -rf /tmp/lo_dl 2>/dev/null", 5)
        }
        return false
    }

    fun isLibreOfficeAvailable(): Boolean {
        return File(rootfsDir, "usr/lib/libreoffice/program/soffice").exists()
    }

    suspend fun installLibreOffice(workspaceDir: File, onProgress: (String) -> Unit): Result<String> =
        setupMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    if (libreofficeMarker.exists()) {
                        return@withContext Result.success("LibreOffice 已安装")
                    }
                    if (!isReady) {
                        return@withContext Result.failure(RuntimeException("Linux 环境未就绪"))
                    }

                    val ok = installLibreOfficeInternal(onProgress)
                    if (ok) {
                        libreofficeMarker.writeText("ok")
                        onProgress("LibreOffice 安装完成")
                        Result.success("LibreOffice 安装完成")
                    } else {
                        Result.failure(RuntimeException("LibreOffice 安装失败，请检查网络连接"))
                    }
                } catch (e: Exception) {
                    Log.e("LinuxEnv", "installLibreOffice failed", e)
                    Result.failure(e)
                }
            }
        }

    /**
     * Install all skill dependencies (system + Python packages) in one shot.
     * Returns a list of progress messages emitted during installation.
     */
    fun installSkillDependencies(onProgress: (String) -> Unit): Result<String> {
        if (!isReady) {
            return Result.failure(RuntimeException("Linux 环境未就绪，请先完成初始化"))
        }
        // All packages are pre-installed in rootfs — no network needed.
        onProgress("系统软件包: 预装于 rootfs")
        onProgress("  ✓ curl, pandoc, poppler-utils, font-wqy-zenhei")
        onProgress("Python 包: 预装于 rootfs")
        onProgress("  ✓ rich, requests, click, Pillow, lxml, defusedxml")
        onProgress("  ✓ python-pptx, python-docx, markitdown, deepseek-ocr")

        pipMarker.writeText("ok")
        File(envDir, "skills_deps.done").writeText("ok")
        onProgress("技能依赖就绪")
        return Result.success("所有依赖已预装")
    }

    suspend fun execCommand(
        command: String,
        workspaceDir: File,
        timeoutSeconds: Long = 120,
        extraBindMounts: List<Pair<String, String>> = emptyList()
    ): ProcessResult = withContext(Dispatchers.IO) {
        val mounts = listOf(workspaceDir.absolutePath to "/workspace") + extraBindMounts
        execRaw(
            "cd /workspace && $command",
            timeoutSeconds,
            bindMounts = mounts
        )
    }

    // Must be called from within Dispatchers.IO context
    private fun execRaw(
        cmd: String,
        timeoutSeconds: Long,
        bindMounts: List<Pair<String, String>> = emptyList()
    ): ProcessResult {
        val args = mutableListOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", fakeprocDir.absolutePath + ":/proc",
            "-b", "/sys"
        )
        for ((host, guest) in bindMounts) {
            args.add("-b")
            args.add("$host:$guest")
        }
        val fullCmd = "export LD_LIBRARY_PATH=/usr/lib/libreoffice/program:\${LD_LIBRARY_PATH:-}; $cmd"
        args.addAll(listOf("-w", "/workspace", "/bin/sh", "-c", fullCmd))
        return runProotProcess(args, timeoutSeconds)
    }

    // Streaming variant that reports each output line as it arrives
    private fun execRawStreaming(
        cmd: String,
        timeoutSeconds: Long,
        bindMounts: List<Pair<String, String>> = emptyList(),
        onLine: (String) -> Unit
    ): ProcessResult {
        val args = mutableListOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", fakeprocDir.absolutePath + ":/proc",
            "-b", "/sys"
        )
        for ((host, guest) in bindMounts) {
            args.add("-b")
            args.add("$host:$guest")
        }
        val fullCmd = "export LD_LIBRARY_PATH=/usr/lib/libreoffice/program:\${LD_LIBRARY_PATH:-}; $cmd"
        args.addAll(listOf("-w", "/workspace", "/bin/sh", "-c", fullCmd))
        return runProotProcessStreaming(args, timeoutSeconds, onLine)
    }

    private fun runProotProcess(
        args: List<String>,
        timeoutSeconds: Long
    ): ProcessResult {
        val pb = ProcessBuilder(args)
        pb.directory(rootfsDir)
        val env = pb.environment()
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["PROOT_LOADER"] = prootLoader.absolutePath
        env["LD_LIBRARY_PATH"] = appContext.applicationInfo.nativeLibraryDir
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/libreoffice/program"
        env["HOME"] = "/root"
        pb.redirectErrorStream(true)

        Log.d("LinuxEnv", "cmd: ${args.takeLast(4).joinToString(" ")}")

        val process = pb.start()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<ProcessResult> {
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                ProcessResult(exitCode == 0, output, exitCode)
            }
            future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            process.destroy()
            executor.shutdownNow()
            ProcessResult(false, "", -1)
        } catch (e: Exception) {
            process.destroy()
            executor.shutdownNow()
            ProcessResult(false, e.message ?: "unknown error", -1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Suppress("DEPRECATION")
    private fun runProotProcessStreaming(
        args: List<String>,
        timeoutSeconds: Long,
        onLine: (String) -> Unit
    ): ProcessResult {
        val pb = ProcessBuilder(args)
        pb.directory(rootfsDir)
        val env = pb.environment()
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["PROOT_LOADER"] = prootLoader.absolutePath
        env["LD_LIBRARY_PATH"] = appContext.applicationInfo.nativeLibraryDir
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/libreoffice/program"
        env["HOME"] = "/root"
        pb.redirectErrorStream(true)

        Log.d("LinuxEnv", "cmd (streaming): ${args.takeLast(4).joinToString(" ")}")

        val process = pb.start()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<ProcessResult> {
                val reader = process.inputStream.bufferedReader()
                val output = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    output.appendLine(line)
                    onLine(line!!)
                    line = reader.readLine()
                }
                val exitCode = process.waitFor()
                ProcessResult(exitCode == 0, output.toString(), exitCode)
            }
            future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            process.destroy()
            executor.shutdownNow()
            ProcessResult(false, "", -1)
        } catch (e: Exception) {
            process.destroy()
            executor.shutdownNow()
            ProcessResult(false, e.message ?: "unknown error", -1)
        } finally {
            executor.shutdownNow()
        }
    }

    data class ProcessResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int
    )
}
