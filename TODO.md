# TODO

## 待完成功能

### 1. 增加中文字体（PPTX/文档转 PDF 无文字问题）
- [ ] rootfs 内嵌 `font-noto-cjk`（思源黑体 + 思源宋体），覆盖 宋体/微软雅黑/黑体/楷体/仿宋
- [ ] TarExtractor 权限修复已做（`contains("/lib/")`, `contains("/libexec/")`）
- [ ] DocumentService 改为 `soffice.bin` 全路径 + `SAL_USE_VCLPLUGIN=svp`
- [ ] LinuxEnvironment.setup() chmod LO 全部可执行文件
- 当前状态：build_rootfs.py 已添加 `font-noto-cjk`，但网络不稳定未完成重建，APK 太大(2.2GB)安装失败

### 2. 增加联网搜索功能
- [ ] 新增 `web_search` 工具 — 搜索网页（DuckDuckGo / SerpAPI）
- [ ] 新增 `web_fetch` 工具 — 抓取 URL 内容（curl 已有）
- [ ] ToolDefinitions + ToolExecutor 注册
- [ ] 系统提示词更新

### 3. 消融项目体积（目标 < 1GB APK）
- [ ] 按架构分 APK（arm64 不含 x86_64 rootfs）
- [ ] rootfs 瘦身：移除不必要的包（如所有 Python data science 包）
- [ ] 考虑 AAB 格式分发
- [ ] 字体按需下载 vs 内嵌（权衡）

## 已修复
- [x] OCR max_tokens 4096
- [x] 系统提示词含完整 16 skill 表格
- [x] SetupViewModel 简化、进度条修复、环境就绪前禁止进入
- [x] LibreOffice 预装于 rootfs（writer + impress + calc）
- [x] DocumentService 修复 soffice 命令路径/权限
- [x] TarExtractor 权限启发式规则扩展
