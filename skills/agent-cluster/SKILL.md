# Agent Cluster Skill - Zoe 编排系统

## 功能

此技能提供 OpenClaw + Codex/Claude Code 智能体集群的编排能力。

## 命令

### 派生智能体

```
spawn agent <task_id> <type> <branch> <description>
```

示例：
```
spawn agent feat-auth codex feat/authentication "实现用户认证功能"
```

### 发送指令

```
send <task_id> <instruction>
```

示例：
```
send feat-auth "Stop. Focus on the API layer first, not the UI."
```

### 查看状态

```
status
```

### 手动监控

```
monitor
```

## 智能体选型

| 任务类型 | 推荐智能体 |
|---------|-----------|
| 后端逻辑/复杂 bug | Codex |
| 前端/UI 实现 | Claude Code |
| 设计稿生成 | Gemini |
| Git 操作 | Claude Code |
| 安全审查 | Gemini |

## 配置

在 `config/.env` 中设置：

```bash
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

## 目录结构

```
workspace/
├── .clawdbot/
│   └── active-tasks.json    # 任务注册表
├── scripts/
│   ├── zoe-orchestrator.sh  # 主编排脚本
│   ├── run-agent.sh         # 智能体运行脚本
│   ├── code-review.sh       # 代码审查脚本
│   ├── monitor-cron.sh      # 监控脚本
│   ├── cleanup-worktrees.sh # 清理脚本
│   └── notify-telegram.sh   # 通知脚本
├── worktrees/               # Git worktrees
├── logs/                    # 日志文件
└── reports/                 # 周报
```

## Cron 配置

安装 crontab：
```bash
crontab /home/admin/.openclaw/workspace/config/crontab.example
```
