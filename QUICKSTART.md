# 🚀 快速开始指南

## 前置要求

### 必须安装

```bash
# Alibaba Cloud Linux / CentOS / RHEL
sudo yum install -y tmux

# Ubuntu / Debian
sudo apt-get install -y tmux

# macOS
brew install tmux
```

### 可选工具（根据需求安装）

```bash
# GitHub CLI - 用于 PR 创建和审查
gh auth login

# Codex CLI
npm install -g @openai/codex

# Claude Code CLI
npm install -g @anthropic-ai/claude-code
```

## 第一步：测试系统

```bash
cd /home/admin/.openclaw/workspace
./scripts/test-setup.sh
```

## 第二步：配置环境变量（可选）

如果需要使用 Telegram 通知：

```bash
cp config/.env.example config/.env
# 编辑 config/.env，填入你的 Telegram Bot Token 和 Chat ID
```

## 第三步：安装 Cron 监控（可选）

```bash
crontab config/crontab.example
```

## 第四步：派生你的第一个智能体

### 模式 1：模拟模式（无需安装 Codex/Claude）

系统会自动检测 CLI 工具，如果未安装则运行模拟模式：

```bash
./scripts/zoe-orchestrator.sh spawn feat-hello codex feat/hello "测试智能体功能"
```

### 模式 2：真实模式（需要安装对应 CLI）

```bash
# 使用 Codex
./scripts/zoe-orchestrator.sh spawn feat-auth codex feat/authentication "实现用户认证功能"

# 使用 Claude Code
./scripts/zoe-orchestrator.sh spawn feat-ui claude feat/button-style "优化按钮样式"

# 使用 Gemini
./scripts/zoe-orchestrator.sh spawn feat-design gemini feat/dashboard "生成仪表盘设计稿"
```

## 第五步：管理智能体

### 查看任务状态

```bash
./scripts/zoe-orchestrator.sh status
```

### 向运行中的智能体发送指令

```bash
# 如果智能体方向跑偏，可以实时纠正
./scripts/zoe-orchestrator.sh send feat-auth "Stop. Focus on the API layer first, not the UI."

# 补充上下文
./scripts/zoe-orchestrator.sh send feat-auth "The schema is in src/types/user.ts. Use that."
```

### 手动触发监控

```bash
./scripts/zoe-orchestrator.sh monitor
```

## 第六步：查看日志

```bash
# Zoe 编排日志
tail -f logs/zoe.log

# 监控日志
tail -f logs/monitor.log

# 所有日志
ls -la logs/
```

## 完整示例流程

```bash
# 1. 派生一个 Codex 智能体
./scripts/zoe-orchestrator.sh spawn feat-login codex feat/login "实现登录功能"

# 2. 查看状态
./scripts/zoe-orchestrator.sh status

# 3. 等待 10 分钟（或手动监控）
./scripts/zoe-orchestrator.sh monitor

# 4. 智能体完成后，检查任务注册表
cat .clawdbot/active-tasks.json | jq .

# 5. 查看生成的 PR（如果有 gh CLI）
gh pr list
```

## 常见问题

### Q: tmux 会话立即退出？
A: 确保 `run-agent.sh` 中的命令不会立即退出。模拟模式会自动等待。

### Q: 如何停止智能体？
```bash
tmux kill-session -t agent-<task_id>
```

### Q: 如何清理所有任务？
```bash
echo '{"tasks":[],"metadata":{"version":"1.0","createdAt":"'$(date +%Y-%m-%d)'","maxConcurrentAgents":4}}' > .clawdbot/active-tasks.json
```

### Q: 如何修改监控频率？
编辑 `config/crontab.example`，修改 `*/10` 为其他值。

---

**下一步**: 运行 `./scripts/test-setup.sh` 开始！
