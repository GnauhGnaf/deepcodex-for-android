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

    suspend fun setup(): Result<String> = setupMutex.withLock {
        withContext(Dispatchers.IO) {
        try {
            // Always ensure fake /proc exists (for upgrades from older versions)
            createFakeProc()

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

            // --- Step 1: Prepare directories & verify proot ---
            log.appendLine("[1/7] 准备环境...")
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

            // --- Step 2: Extract rootfs ---
            log.appendLine("[2/7] 解压 Alpine Linux 根文件系统...")
            val rootfsAsset = "rootfs/alpine-minirootfs-${
                when { abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"; else -> "x86_64" }
            }.tar"
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

            // Verify key files exist after extraction
            val pythonBin = File(rootfsDir, "usr/bin/python3.14")
            val python3Bin = File(rootfsDir, "usr/bin/python3")
            val busyboxBin = File(rootfsDir, "bin/busybox")
            val shLink = File(rootfsDir, "bin/sh")
            Log.d("LinuxEnv", "After extract: python3.14=${pythonBin.exists()}, python3=${python3Bin.exists()}, busybox=${busyboxBin.exists()}, sh=${shLink.exists()}")

            val hasPython = pythonBin.exists() || python3Bin.exists()

            if (!busyboxBin.exists()) {
                return@withContext Result.failure(RuntimeException("busybox 未在 rootfs 中找到"))
            }

            // --- Step 3: Create symlinks (must happen before apk installs) ---
            log.appendLine("[3/7] 创建符号链接...")
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
            Log.d("LinuxEnv", "Tar symlinks: $linksOk ok, $linksFail fail")

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
            } else {
                Log.w("LinuxEnv", "symlinks.list not found in rootfs!")
            }
            Log.d("LinuxEnv", "Extra symlinks: $extraLinks")
            log.appendLine("  ✓ ${linksOk + extraLinks} 个符号链接")

            File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            File(rootfsDir, "workspace").mkdirs()

            // --- Step 4: Install Python if not pre-installed ---
            if (!hasPython) {
                log.appendLine("[4/7] 安装 Python...")
                val installPy = execRaw("apk add --no-cache python3 2>&1", 300)
                Log.d("LinuxEnv", "apk add python3: exit=${installPy.exitCode}, out=${installPy.output.take(300)}")
                if (!installPy.success && !python3Bin.exists() && !pythonBin.exists()) {
                    return@withContext Result.failure(RuntimeException("Python 安装失败: ${installPy.output.take(200)}"))
                }
                log.appendLine("  ✓ Python 已安装")
            } else {
                log.appendLine("[4/7] Python 已预装")
            }

            // --- Step 5: Smoke test proot ---
            log.appendLine("[5/7] 测试 proot...")
            val rEcho = execRaw("echo proot_ok", 5)
            Log.d("LinuxEnv", "echo test: exit=${rEcho.exitCode} out=${rEcho.output}")
            if (!rEcho.success) {
                return@withContext Result.failure(RuntimeException("proot 基础功能异常: ${rEcho.output}"))
            }
            log.appendLine("  ✓ proot 基础功能正常")

            // Test a command that requires fork (ls)
            var forkOk = false
            for (attempt in 1..3) {
                val rLs = execRaw("ls /usr/bin/python* 2>&1", 8)
                Log.d("LinuxEnv", "ls test attempt $attempt: exit=${rLs.exitCode} out=${rLs.output}")
                if (rLs.success) { forkOk = true; break }
            }
            if (!forkOk) {
                Log.e("LinuxEnv", "fork still failing after retries, will try python3 directly")
            }

            // --- Step 6: Configure pip ---
            log.appendLine("[6/7] 配置 pip...")
            configurePip()
            pipMarker.writeText("ok")
            log.appendLine("  ✓ pip 已配置")

            // Step 7: Configure fonts + Test Python
            log.appendLine("[7/7] 安装中文字体...")
            configureFonts()
            fontsMarker.writeText("ok")
            log.appendLine("  ✓ 中文字体已安装")

            val pyCmd = if (pythonBin.exists()) "python3.14" else "python3"

            for (attempt in 1..3) {
                val rPy = execRaw("$pyCmd --version 2>&1", 10)
                Log.d("LinuxEnv", "$pyCmd test $attempt: exit=${rPy.exitCode} out=${rPy.output}")
                if (rPy.success) {
                    log.appendLine("  ✓ ${rPy.output.trim()}")
                    setupMarker.writeText(abi)

                    // LibreOffice is pre-installed in the rootfs
                    if (File(rootfsDir, "usr/lib/libreoffice/program/soffice").exists()) {
                        libreofficeMarker.writeText("ok")
                        log.appendLine("  ✓ LibreOffice 已就绪（预装）")
                    }

                    log.appendLine("初始化完成！")
                    return@withContext Result.success(log.toString())
                }
                if (attempt < 3) {
                    Log.d("LinuxEnv", "$pyCmd retry in 1s...")
                    kotlinx.coroutines.delay(1000)
                }
            }

            // Python didn't respond, but might still work later — check binary exists
            val anyPython = pythonBin.exists() || python3Bin.exists()
            if (anyPython) {
                Log.w("LinuxEnv", "Python binary exists but proot exec failed, marking setup as degraded")
                setupMarker.writeText("${abi}_degraded")
                log.appendLine("  ⚠ Python 已安装但无法通过 proot 启动（fork 问题）")

                // LibreOffice is pre-installed
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
        // Remove EXTERNALLY-MANAGED files that block pip install
        execRaw("find /usr/lib/python3* -name EXTERNALLY-MANAGED -delete 2>/dev/null || true", 10)

        // Write pip.conf — use mkdir + echo (heredoc unreliable through proot args)
        execRaw("mkdir -p /root/.config/pip", 3)
        execRaw("echo '[global]' > /root/.config/pip/pip.conf", 3)
        execRaw("echo 'break-system-packages = true' >> /root/.config/pip/pip.conf", 3)
        execRaw("echo 'index-url = https://pypi.tuna.tsinghua.edu.cn/simple' >> /root/.config/pip/pip.conf", 3)
        execRaw("echo 'trusted-host = pypi.tuna.tsinghua.edu.cn' >> /root/.config/pip/pip.conf", 3)
        execRaw("chmod 644 /root/.config/pip/pip.conf", 3)

        // Verify, fallback to pip config set if needed
        val pipCfg = execRaw("cat /root/.config/pip/pip.conf 2>&1", 5)
        Log.d("LinuxEnv", "pip.conf: ${pipCfg.output}")
        if (!pipCfg.success || !pipCfg.output.contains("break-system-packages")) {
            Log.w("LinuxEnv", "pip.conf echo write failed, trying pip config set")
            execRaw("pip config set global.break-system-packages true 2>&1 || true", 15)
            execRaw("pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple 2>&1 || true", 15)
        }
    }

    private fun configureFonts() {
        execRaw("apk add --no-cache font-wqy-zenhei 2>&1", 120)
        execRaw("fc-cache -fv 2>&1 || true", 10)
    }

    // Must be called from within Dispatchers.IO context
    private fun installLibreOfficeInternal(): Boolean {
        try {
            // Update repo first
            Log.d("LinuxEnv", "Running apk update for LibreOffice...")
            val update = execRaw("apk update 2>&1", 120)
            Log.d("LinuxEnv", "apk update: ${update.output.take(300)}")

            if (!update.success && !update.output.contains("OK")) {
                Log.w("LinuxEnv", "apk update had issues, trying install anyway...")
            }

            // Install libreoffice
            Log.d("LinuxEnv", "Running apk add libreoffice...")
            val install = execRaw("apk add --no-cache libreoffice 2>&1", 600)
            Log.d("LinuxEnv", "apk add libreoffice: exit=${install.exitCode}, out=${install.output.take(300)}")

            if (install.success) return true

            // Check if binary exists even if apk reported failure
            val check = execRaw("which libreoffice 2>&1 || echo NOT_FOUND", 5)
            return check.success && check.output.contains("libreoffice") && !check.output.contains("NOT_FOUND")
        } catch (e: Exception) {
            Log.e("LinuxEnv", "LibreOffice install failed", e)
            return false
        }
    }

    /**
     * Public method to install LibreOffice after initial setup.
     * For users who already have the Linux environment set up.
     */
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

                    onProgress("正在更新软件源...")
                    val result = installLibreOfficeInternal()
                    if (result) {
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

    data class ProcessResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int
    )
}
