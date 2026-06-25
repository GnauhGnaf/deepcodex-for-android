# Python 开发技能

在 Android proot Alpine Linux 环境中开发 Python 项目的标准流程。

## 项目初始化

写 Python 脚本时，遵循以下规范：
- 使用 `#!/usr/bin/env python3` shebang
- 添加 UTF-8 编码声明（如有中文）
- 包含简要的模块文档字符串
- 写完整的、可直接运行的 `.py` 文件，而非代码片段

## pip 包管理

环境已配置好 pip（清华镜像 + break-system-packages），安装包直接用：
```
pip install <package>
```
如果失败，尝试：
```
pip install <package> --break-system-packages
```
不要创建虚拟环境，直接安装到系统。

## 常用推荐包

- `rich` — 美化终端输出（表格、颜色、进度条）
- `requests` — HTTP 请求
- `click` — CLI 参数解析

## 测试

写完代码后总是用 `python3 script.py` 运行验证。
