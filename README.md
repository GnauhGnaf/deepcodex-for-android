# Codeeps

> 运行在 Android 上的 AI 编程助手 —— Powered by DeepSeek & Alpine Linux

[中文](#中文) | [English](#english)

---

## 中文

### 简介

Codeeps 是一款 Android 原生 AI 编程助手应用。它通过 DeepSeek API 提供流式对话，并在设备上运行一个完整的 **Alpine Linux** 环境（基于 proot），让 AI 能够直接读写文件、执行命令、安装软件包，真正在手机上完成编码任务。

### 主要功能

- **AI 对话** — 支持 DeepSeek-V4 系列模型，流式 SSE 响应，含推理过程展示
- **工具调用** — AI 可调用 5 个内置工具：读/写文件、列出目录、搜索文件、执行 Shell 命令
- **Linux 沙箱** — 基于 proot 的 Alpine Linux 环境，预装 Python 3.14、LibreOffice 25.8
- **代码执行** — 在安全沙箱中运行 Python、编译 C/C++、安装 pip 包
- **文件查看** — 支持文本（语法高亮）、图片、PDF、Office 文档（DOCX/XLSX/PPTX）、视频
- **对话历史** — 持久化存储，每轮对话独立工作区，支持切换和删除
- **技能系统** — 16 个预装 Claude Code 技能，覆盖 Python 开发、文档生成、PPT 制作、OCR 等
- **文档转换** — LibreOffice headless 模式将 Office 文档转为 PDF 渲染，保持原始排版

### 技术路线

| 层级 | 技术选型 |
|------|----------|
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| 网络 | OkHttp 4.12（SSE 流式对话） |
| AI API | DeepSeek API（兼容 OpenAI 协议） |
| 序列化 | kotlinx-serialization-json |
| 本地存储 | DataStore Preferences（设置）+ JSON 文件（对话） |
| Linux 沙箱 | proot（ptrace 用户态虚拟化） |
| 根文件系统 | Alpine Linux 3.24 minirootfs |
| Office 处理 | LibreOffice 25.8 headless → Android PdfRenderer |
| 图片加载 | Coil Compose |
| Markdown | multiplatform-markdown-renderer 0.28 |

**架构**: MVVM，手写依赖容器（`AppContainer`），Kotlin Coroutines + StateFlow 响应式状态管理。

### Linux 环境原理

```
Android App
  └─ ProcessBuilder 启动 proot
       └─ proot (libproot_exec.so) ptrace 拦截系统调用
            └─ Alpine Linux rootfs (Python + LibreOffice + 字体)
                 ├─ /proc → 伪造的静态 proc 文件
                 ├─ /dev, /sys → 绑定挂载
                 └─ /workspace → 映射到对话独立工作区
```

首次启动时自动完成 7 步初始化：解压根文件系统 → 创建符号链接 → 配置 DNS → 测试 proot → 配置 pip（清华镜像）→ 安装中文字体 → 验证 Python。

### 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (需先配置签名)
# 创建 keystore 并在 build.gradle.kts 中添加 signingConfigs
./gradlew assembleRelease
```

**要求**: Android Studio Hedgehog+ | JDK 17 | Android SDK 34 | Gradle 8.4+

### 权限

仅需 `INTERNET` 权限。无需 Root。

### 开源声明

本项目基于以下开源项目构建：

| 项目 | 用途 | 许可证 |
|------|------|--------|
| [Claude Code](https://github.com/anthropics/claude-code) | AI 编码助手架构参考、Skill 技能系统、工具调用模式 | MIT |
| [Codex CLI](https://github.com/anthropics/claude-code) | 开源 AI 编码代理，技能管理与工作区隔离设计 | MIT |
| [proot](https://github.com/proot-me/proot) | Linux 用户态沙箱（ptrace 虚拟化） | GPL-2.0 |
| [Alpine Linux](https://alpinelinux.org/) | minirootfs 根文件系统 | MIT |
| [LibreOffice](https://www.libreoffice.org/) | Office 文档转换引擎 | MPL-2.0 |
| [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) | Compose Markdown 渲染 | Apache-2.0 |

### 免责声明

- 本项目为个人学习与实验作品，**不保证任何形式的稳定性或可靠性**，使用风险由用户自行承担
- 本应用通过 DeepSeek API 提供服务，使用前需自行获取 API Key，API 费用与限额以 DeepSeek 官方为准
- 本应用在设备本地执行 AI 生成的 Shell 命令，虽有限制措施但无法完全避免风险，请谨慎审查和执行
- 本项目与 DeepSeek、Anthropic 无任何关联或 endorsement 关系
- 所有商标和品牌名称均为其各自所有者的财产

---

## English

### Overview

Codeeps is a native Android AI coding assistant. It provides streaming conversations via the DeepSeek API and runs a full **Alpine Linux** environment on-device (via proot), enabling the AI to read/write files, execute commands, and install packages — a complete coding workspace in your pocket.

### Key Features

- **AI Chat** — Streaming SSE responses with DeepSeek-V4 series models, reasoning display
- **Tool Calling** — 5 built-in tools: read_file, write_file, list_files, search_files, run_command
- **Linux Sandbox** — proot-based Alpine Linux with Python 3.14 and LibreOffice 25.8 pre-installed
- **Code Execution** — Run Python, compile C/C++, install pip packages in a safe sandbox
- **File Viewer** — Text (syntax highlighting), images, PDF, Office documents (DOCX/XLSX/PPTX), video
- **Conversation History** — Persistent storage with per-conversation isolated workspaces
- **Skill System** — 16 bundled Claude Code skills for Python dev, docs generation, PPT creation, OCR, etc.
- **Document Conversion** — LibreOffice headless conversion to PDF, rendered via Android PdfRenderer

### Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| Network | OkHttp 4.12 with SSE support |
| AI API | DeepSeek API (OpenAI-compatible) |
| Serialization | kotlinx-serialization-json |
| Local Storage | DataStore Preferences (settings) + JSON files (conversations) |
| Linux Sandbox | proot (ptrace-based userspace virtualization) |
| Rootfs | Alpine Linux 3.24 minirootfs |
| Office | LibreOffice 25.8 headless → Android PdfRenderer |
| Images | Coil Compose |
| Markdown | multiplatform-markdown-renderer 0.28 |

**Architecture**: MVVM with a hand-rolled dependency container (`AppContainer`), Kotlin Coroutines + StateFlow for reactive state.

### How the Linux Environment Works

```
Android App
  └─ ProcessBuilder launches proot
       └─ proot (libproot_exec.so) intercepts syscalls via ptrace
            └─ Alpine Linux rootfs (Python + LibreOffice + CJK fonts)
                 ├─ /proc → static fake proc files (avoids SELinux issues)
                 ├─ /dev, /sys → bind mounts
                 └─ /workspace → mapped to per-conversation workspace dir
```

On first launch, a 7-step initialization runs: extract rootfs → create symlinks → configure DNS → smoke-test proot → configure pip (Tsinghua mirror) → install CJK fonts → verify Python.

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
# Create a keystore and add signingConfigs to build.gradle.kts
./gradlew assembleRelease
```

**Requirements**: Android Studio Hedgehog+ | JDK 17 | Android SDK 34 | Gradle 8.4+

### Permissions

Only `INTERNET` permission required. No root access needed.

### Open Source Attribution

This project builds upon the following open-source projects:

| Project | Usage | License |
|---------|-------|---------|
| [Claude Code](https://github.com/anthropics/claude-code) | AI coding agent architecture, skill system, tool-calling patterns | MIT |
| [Codex CLI](https://github.com/anthropics/claude-code) | Open-source AI coding agent, skill management & workspace isolation design | MIT |
| [proot](https://github.com/proot-me/proot) | Userspace Linux sandbox (ptrace virtualization) | GPL-2.0 |
| [Alpine Linux](https://alpinelinux.org/) | minirootfs base filesystem | MIT |
| [LibreOffice](https://www.libreoffice.org/) | Office document conversion engine | MPL-2.0 |
| [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) | Compose Markdown rendering | Apache-2.0 |

### Disclaimer

- This project is a personal learning experiment. **No warranty of stability or reliability is provided.** Use at your own risk.
- This application uses the DeepSeek API. You must obtain your own API key; API fees and quotas are subject to DeepSeek's official policies.
- This application executes AI-generated shell commands locally on your device. While safeguards are in place, risks cannot be fully eliminated. Review commands carefully before execution.
- This project is not affiliated with or endorsed by DeepSeek or Anthropic.
- All trademarks and brand names are the property of their respective owners.

---

<p align="center">
  <sub>Built with Kotlin · Compose · Alpine Linux · DeepSeek</sub>
</p>
