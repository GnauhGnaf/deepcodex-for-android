# Shell 脚本技能

在 Alpine Linux (BusyBox ash) 环境中编写 shell 脚本的指南。

## Shell 类型

环境使用 `/bin/sh` → BusyBox ash（兼容 POSIX sh，非 bash）。
- 使用 `#!/bin/sh` shebang
- 不要使用 bash 特有语法（如 `[[ ]]`、数组、`source`）
- 用 `[ ]` 替代 `[[ ]]`，用 `.` 替代 `source`

## 基本模板

```sh
#!/bin/sh
set -e  # 出错即停

echo "开始处理..."
# your code here
echo "完成。"
```

## 文件操作

- 逐行读取：`while IFS= read -r line; do ...; done < file`
- 查找文件：`find . -name "*.txt" -type f`
- 统计行数：`wc -l file`

## 注意事项

- 不要用 `&&` 链过长的命令，分开写更清晰
- 善用 `grep`、`sed`、`awk` 处理文本
- `chmod +x script.sh` 给执行权限
