# 项目初始化技能

在 Android proot 环境中快速搭建常见项目结构。

## Python 项目

```
my_project/
├── main.py          # 入口文件
├── lib/             # 自定义模块
│   └── utils.py
├── tests/           # 测试文件（可选）
│   └── test_main.py
└── README.md        # 项目说明
```

初始化步骤：
1. `write_file` 创建各文件
2. 在 `main.py` 中写入口逻辑
3. 如需额外模块，放在 `lib/` 下并用相对导入
4. `run_command python3 main.py` 运行验证

## Shell 脚本项目

直接创建独立的 `.sh` 文件即可，无需复杂目录结构。多个脚本放在同一目录下。

## C/C++ 项目

1. 先 `apk add build-base` 安装编译工具
2. 创建 `.c`/`.cpp` 源文件
3. `gcc -o program source.c` 编译
4. `./program` 运行

## 一般原则

- 先规划目录结构，再逐个创建文件
- 每个文件写完就运行验证
- 保持简单，不要过度设计
