# 🤖 OpenClaw + Codex/Claude Code 智能体集群

一人开发团队的完整搭建方案

## 🎯 核心价值

> 你不再直接管理 Claude Code，而是管理一个**管理 Claude Code/Codex 智能体集群的 OpenClaw 编排层**。

git 历史看起来像是雇了一个开发团队，实际上只有一个人。

## 📁 已创建的文件结构

```
workspace/
├── .clawdbot/
│   └── active-tasks.json       # 任务注册表
├── scripts/
│   ├── zoe-orchestrator.sh     # Zoe 主编排脚本
│   ├── run-agent.sh            # 智能体运行脚本
│   ├── code-review.sh          # 多模型代码审查
│   ├── monitor-cron.sh         # 10 分钟监控循环
│   ├── cleanup-worktrees.sh    # 清理孤立 worktree
│   ├── weekly-report.sh        # 周报生成
│   └── notify-telegram.sh      # Telegram 通知
├── config/
│   ├── crontab.example         # Cron 配置模板
│   └── zoe-prompts.md          # Zoe 提示词系统
├── worktrees/                  # Git worktrees (动态创建)
├── logs/                       # 日志文件
└── reports/                    # 周报
```

## 🚀 快速开始

### 1. 安装 Cron 任务

```bash
crontab /home/admin/.openclaw/workspace/config/crontab.example
```

### 2. 配置 Telegram 通知（可选）

编辑 `scripts/notify-telegram.sh` 或设置环境变量：

```bash
export TELEGRAM_BOT_TOKEN="your_bot_token"
export TELEGRAM_CHAT_ID="your_chat_id"
```

### 3. 派生第一个智能体

```bash
./scripts/zoe-orchestrator.sh spawn feat-login codex feat/login "实现用户登录功能"
```

### 4. 查看任务状态

```bash
./scripts/zoe-orchestrator.sh status
```

### 5. 向运行中的智能体发送指令

```bash
./scripts/zoe-orchestrator.sh send feat-login "Stop. Focus on the API layer first."
```

## 📊 8 步工作流

```
1. 需求接收 → 与 Zoe 共同拆解
2. 派生智能体 → git worktree + tmux 会话
3. 循环监控 → 每 10 分钟检查状态
4. 智能体创建 PR → 自动提交 + gh pr create
5. 自动化代码审查 → Codex + Claude + Gemini
6. 自动化测试 → CI 流水线
7. 人工审查 → Telegram 通知，5-10 分钟完成
8. 合并与清理 → 每日自动清理
```

## 🧠 智能体选型指南

| Agent | 适用场景 | 特点 |
|-------|---------|------|
| **Codex** | 后端逻辑、复杂 bug、多文件重构 | 速度较慢但更彻底，90% 任务量 |
| **Claude Code** | 前端工作、git 操作 | 速度更快，权限问题更少 |
| **Gemini** | UI 设计稿生成 | 设计感强，先出规格稿 |
| **Zoe** | 任务路由、上下文管理 | 编排层，不写代码 |

## 💰 成本参考

- Claude: ~$100/月
- Codex: ~$90/月
- 最低可从 $20/月起步

## ⚠️ 当前瓶颈

**内存** - 每个智能体需要独立的 worktree + node_modules

- 16GB 内存：4-5 个智能体并发
- 128GB 内存：可支持更多并发

## 🔧 自定义

### 添加新的智能体类型

编辑 `scripts/run-agent.sh`，添加新的 case 分支。

### 修改监控频率

编辑 `config/crontab.example`，修改 cron 表达式。

### 添加新的审查规则

编辑 `scripts/code-review.sh`，添加新的检查点。

## 📝 任务注册表格式

```json
{
  "id": "feat-custom-templates",
  "tmuxSession": "codex-templates",
  "agent": "codex",
  "description": "Custom email templates",
  "branch": "feat/custom-templates",
  "startedAt": 1740268800,
  "status": "running",
  "notifyOnComplete": true
}
```

## 🎯 Zoe 的自我改进机制

当智能体失败时，Zoe 会：

1. 分析失败原因
2. 结合业务上下文重构提示词
3. 记录成功模式
4. 持续优化

奖励信号：CI 通过 + 三方审查通过 + 人工合并

---

**原文参考**: https://x.com/elvissun/status/2025920521871716562
