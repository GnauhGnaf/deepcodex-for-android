package com.example.app.domain

import android.content.Context
import android.system.Os
import android.system.ErrnoException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
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
    private val setupMutex = Mutex()

    val isReady: Boolean get() = setupMarker.exists() && File(rootfsDir, "usr/bin/python3.14").exists()

    suspend fun setup(): Result<String> = setupMutex.withLock {
        withContext(Dispatchers.IO) {
        try {
            if (isReady && pipMarker.exists()) return@withContext Result.success("Linux 环境已就绪")

            // Quick pip fix for old installations that are "ready" but lack pip config
            if (isReady && !pipMarker.exists()) {
                configurePip()
                pipMarker.writeText("ok")
                return@withContext Result.success("Linux 环境已就绪（已补充 pip 配置）")
            }

            val log = StringBuilder()
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "x86_64"

            // --- Step 1: Prepare directories & verify proot ---
            log.appendLine("[1/6] 准备环境...")
            tmpDir.mkdirs(); envDir.mkdirs(); rootfsDir.mkdirs()
            Log.d("LinuxEnv", "ABI=$abi, proot=${prootBin.absolutePath}")

            if (!prootBin.exists()) return@withContext Result.failure(RuntimeException("proot 未找到"))
            if (!prootLoader.exists()) return@withContext Result.failure(RuntimeException("proot loader 未找到"))
            prootBin.setExecutable(true); prootLoader.setExecutable(true)

            val rootfsLoader = File(rootfsDir, "usr/libexec/proot/loader")
            rootfsLoader.parentFile!!.mkdirs()
            prootLoader.copyTo(rootfsLoader, overwrite = true)
            rootfsLoader.setExecutable(true)

            // --- Step 2: Extract rootfs ---
            log.appendLine("[2/6] 解压 Alpine Linux 根文件系统...")
            val rootfsAsset = "rootfs/alpine-minirootfs-${
                when { abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"; else -> "x86_64" }
            }.tar"
            Log.d("LinuxEnv", "Extracting $rootfsAsset")

            val symlinks = mutableListOf<Pair<File, String>>()

            try {
                appContext.assets.open(rootfsAsset).use { assetStream ->
                    TarArchiveInputStream(assetStream).use { tarIn ->
                        var entry = tarIn.nextEntry
                        var count = 0
                        while (entry != null) {
                            val destFile = File(rootfsDir, entry.name)
                            when {
                                entry.isDirectory -> destFile.mkdirs()
                                entry.isSymbolicLink -> {
                                    destFile.parentFile?.mkdirs()
                                    symlinks.add(destFile to entry.linkName)
                                }
                                else -> {
                                    destFile.parentFile?.mkdirs()
                                    FileOutputStream(destFile).use { tarIn.copyTo(it) }
                                    // Windows repack loses execute bits — restore for binaries/libs
                                    val name = entry.name
                                    if (entry.mode and 0b001_001_001 != 0
                                        || name.contains("/bin/") || name.contains("/sbin/")
                                        || name.endsWith(".so") || name.contains(".so.")
                                        || name == "bin/busybox" || name.startsWith("bin/")
                                        || name.startsWith("sbin/") || name.startsWith("lib/")
                                    ) {
                                        destFile.setExecutable(true)
                                    }
                                }
                            }
                            entry = tarIn.nextEntry; count++
                            if (count % 2000 == 0) Log.d("LinuxEnv", "  $count entries...")
                        }
                        Log.d("LinuxEnv", "Extracted: $count entries, ${symlinks.size} symlinks")
                    }
                }
            } catch (e: Exception) {
                Log.e("LinuxEnv", "Extraction failed", e)
                return@withContext Result.failure(RuntimeException("rootfs 解压失败: ${e.message}"))
            }

            // Verify key files exist after extraction
            val pythonBin = File(rootfsDir, "usr/bin/python3.14")
            val busyboxBin = File(rootfsDir, "bin/busybox")
            val shLink = File(rootfsDir, "bin/sh")
            Log.d("LinuxEnv", "After extract: python3.14=${pythonBin.exists()}, busybox=${busyboxBin.exists()}, sh=${shLink.exists()}")
            if (!pythonBin.exists()) {
                return@withContext Result.failure(RuntimeException("python3.14 未在 rootfs 中找到"))
            }

            // --- Step 3: Create symlinks ---
            log.appendLine("[3/6] 创建符号链接...")
            var linksOk = 0; var linksFail = 0
            for ((destFile, target) in symlinks) {
                try {
                    Os.symlink(target, destFile.absolutePath)
                    linksOk++
                } catch (e: ErrnoException) {
                    // Ignore EEXIST (already exists)
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

            // --- Step 4: Smoke test proot (echo is builtin, no fork needed) ---
            log.appendLine("[4/6] 测试 proot...")
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

            // --- Step 5: Configure pip ---
            log.appendLine("[5/6] 配置 pip...")
            configurePip()
            pipMarker.writeText("ok")
            log.appendLine("  ✓ pip 已配置")

            // Step 6: Test Python
            log.appendLine("[6/6] 测试 Python...")

            for (attempt in 1..3) {
                val rPy = execRaw("python3 --version 2>&1", 10)
                Log.d("LinuxEnv", "python3 test $attempt: exit=${rPy.exitCode} out=${rPy.output}")
                if (rPy.success) {
                    log.appendLine("  ✓ ${rPy.output.trim()}")
                    setupMarker.writeText(abi)
                    log.appendLine("初始化完成！")
                    return@withContext Result.success(log.toString())
                }
                if (attempt < 3) {
                    Log.d("LinuxEnv", "python3 retry in 1s...")
                    kotlinx.coroutines.delay(1000)
                }
            }

            // Python didn't respond, but might still work later — check binary exists
            if (pythonBin.exists()) {
                Log.w("LinuxEnv", "python3 binary exists but proot exec failed, marking setup as degraded")
                setupMarker.writeText("${abi}_degraded")
                log.appendLine("  ⚠ Python 已安装但无法通过 proot 启动（fork 问题）")
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
            "-b", "/proc",
            "-b", "/sys"
        )
        for ((host, guest) in bindMounts) {
            args.add("-b")
            args.add("$host:$guest")
        }
        args.addAll(listOf("-w", "/workspace", "/bin/sh", "-c", cmd))
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
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["HOME"] = "/root"
        pb.redirectErrorStream(true)

        Log.d("LinuxEnv", "cmd: ${args.takeLast(4).joinToString(" ")}")

        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        val exited = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)

        if (!exited) {
            process.destroyForcibly()
            return ProcessResult(false, output, -1)
        }
        val exitCode = process.exitValue()
        return ProcessResult(exitCode == 0, output, exitCode)
    }

    data class ProcessResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int
    )
}
