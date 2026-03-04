# 🚀 OpenClaw 真实模式 - 配置完成！

## ✅ 已自动完成

| 组件 | 状态 | 说明 |
|------|------|------|
| **Cron 监控** | ✅ 已安装 | 每 10 分钟自动检查任务 |
| **Claude Code CLI** | ✅ 已安装 | v2.1.66 |
| **Qwen-Coder 脚本** | ✅ 已就绪 | run-agent-qwen.sh |
| **Git Worktree** | ✅ 已配置 | 独立分支隔离 |
| **tmux 会话** | ✅ 运行中 | 2 个智能体后台运行 |
| **任务注册表** | ✅ 已创建 | .clawdbot/active-tasks.json |
| **日志系统** | ✅ 已配置 | logs/zoe.log |
| **清理脚本** | ✅ 已配置 | 自动清理 7 天前任务 |

---

## ⚠️ 最后一步：配置 API Key

### 方式 1：DashScope（推荐，国内）

```bash
# 1. 获取 API Key
# 访问：https://dashscope.console.aliyun.com/
# 登录 → API Key 管理 → 创建/复制 Key

# 2. 设置环境变量
echo 'export DASHSCOPE_API_KEY=sk-你的 key' >> ~/.bashrc
source ~/.bashrc

# 3. 验证
echo $DASHSCOPE_API_KEY
```

### 方式 2：Claude（国际）

```bash
# 1. 获取 API Key
# 访问：https://console.anthropic.com/

# 2. 设置环境变量
echo 'export ANTHROPIC_API_KEY=sk-ant-你的 key' >> ~/.bashrc
source ~/.bashrc
```

---

## 🧪 测试真实模式

配置 API Key 后，运行：

```bash
# 创建测试智能体
./scripts/zoe-orchestrator.sh spawn test-qwen qwen feat/test-qwen \
  "创建一个 Hello World React 组件"

# 查看状态
./scripts/zoe-orchestrator.sh status

# 查看日志
tail -f logs/zoe.log

# 查看生成的代码
cat worktrees/feat/test-qwen/response-test-qwen.md
```

---

## 📊 当前 Cron 配置

```bash
# 查看已安装的 Cron
crontab -l

# 输出：
*/10 * * * * /home/admin/.openclaw/workspace/scripts/monitor-cron.sh
0 8 * * * /home/admin/.openclaw/workspace/scripts/zoe-orchestrator.sh scan-sentry
0 22 * * * /home/admin/.openclaw/workspace/scripts/cleanup-worktrees.sh
0 9 * * 1 /home/admin/.openclaw/workspace/scripts/weekly-report.sh
```

---

## 🎯 使用命令

### 派生智能体

```bash
# 使用 Qwen-Coder（国内推荐）
./scripts/zoe-orchestrator.sh spawn <任务 ID> qwen <分支名> "<任务描述>"

# 示例
./scripts/zoe-orchestrator.sh spawn feat-login qwen feat/login "实现登录功能"
./scripts/zoe-orchestrator.sh spawn feat-user qwen feat/user "用户管理模块"
```

### 管理智能体

```bash
# 查看所有智能体
./scripts/zoe-orchestrator.sh status

# 发送指令给运行中的智能体
./scripts/zoe-orchestrator.sh send <任务 ID> "<指令>"

# 手动监控循环
./scripts/zoe-orchestrator.sh monitor
```

### 查看日志

```bash
# Zoe 编排日志
tail -f logs/zoe.log

# Cron 监控日志
tail -f logs/cron.log

# 查看特定智能体日志
tmux capture-pane -t agent-<任务 ID> -p
```

### 清理

```bash
# 停止单个智能体
tmux kill-session -t agent-<任务 ID>

# 停止所有智能体
tmux kill-server

# 清理已完成任务
./scripts/zoe-orchestrator.sh monitor
```

---

## 💰 成本参考

### DashScope Qwen-Coder

| 模型 | 输入 | 输出 | 免费额度 |
|------|------|------|---------|
| Qwen-Coder-Plus | ¥0.02/K tokens | ¥0.06/K tokens | 新用户赠送 |

**估算：**
- 小功能（100 行代码）：~¥0.5-1
- 中等功能（500 行）：~¥2-5
- 大功能（2000 行）：~¥10-20

**月均：** ¥50-200（取决于使用频率）

---

## 📈 24 小时自动运作流程

```
┌─────────────────────────────────────────────────────────────┐
│ 00:00 - 06:00  夜间静默期                                    │
│ - Cron 每 10 分钟检查任务状态                                 │
│ - 智能体继续运行（如有）                                     │
│ - 不打扰用户                                                 │
├─────────────────────────────────────────────────────────────┤
│ 08:00          早间扫描（需配置 Sentry）                      │
│ - 扫描错误日志                                              │
│ - 派生修复智能体                                            │
├─────────────────────────────────────────────────────────────┤
│ 09:00 - 18:00  工作时间                                      │
│ - 实时监控任务                                              │
│ - 需要时发送 Telegram 通知                                   │
│ - PR 就绪时通知审查                                         │
├─────────────────────────────────────────────────────────────┤
│ 22:00          夜间清理                                      │
│ - 清理 7 天前已完成任务                                       │
│ - 清理孤立 worktree                                         │
├─────────────────────────────────────────────────────────────┤
│ 周一 09:00     周报生成                                      │
│ - 生成上周工作总结                                          │
│ - 统计任务/智能体使用情况                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚠️ 注意事项

1. **API Key 安全**
   - 不要提交到 Git
   - 已添加到 ~/.bashrc（仅本机访问）

2. **用量监控**
   - 定期查看：https://dashscope.console.aliyun.com/
   - 设置用量告警

3. **Cron 日志**
   - 查看：`tail -f logs/cron.log`
   - 位置：`/var/log/syslog`（系统日志）

4. **智能体并发**
   - 建议最多 4 个并发（取决于内存）
   - 每个智能体约占用 500MB-1GB 内存

---

## 🎉 恭喜！

你现在拥有：
- ✅ 完整的智能体编排系统
- ✅ 24 小时自动监控
- ✅ 真实的 AI 编码能力（配置 API Key 后）
- ✅ 自动清理和维护

**下一步：** 配置 API Key，然后创建你的第一个真实智能体任务！

---

## 📞 获取帮助

- 查看文档：`cat config/CHINESE-AI-SETUP.md`
- Zoe 提示词：`cat config/zoe-prompts.md`
- 快速开始：`cat QUICKSTART.md`
- 完整文档：`cat AGENT-CLUSTER-README.md`
