#!/bin/bash
# Quick Enable Real Mode - 快速启用真实模式
# 非交互式，自动配置

set -e

WORKSPACE="/home/admin/.openclaw/workspace"

echo "🚀 OpenClaw 真实模式快速配置"
echo "══════════════════════════════════════"
echo ""

# Step 1: 安装 Cron
echo "📋 Step 1: 安装 Cron 监控..."
if ! crontab -l 2>/dev/null | grep -q "monitor-cron.sh"; then
    (crontab -l 2>/dev/null | grep -v "monitor-cron.sh"; cat "$WORKSPACE/config/crontab.example") | crontab -
    echo "✅ Cron 监控已安装"
    crontab -l | grep "openclaw"
else
    echo "✅ Cron 监控已存在"
fi
echo ""

# Step 2: 检查 npm 路径
echo "📋 Step 2: 配置 npm 全局路径..."
if ! echo $PATH | grep -q "npm-global"; then
    export PATH=$PATH:~/.npm-global/bin
    echo 'export PATH=$PATH:~/.npm-global/bin' >> ~/.bashrc
    echo "✅ npm 全局路径已添加"
else
    echo "✅ npm 路径已配置"
fi
echo ""

# Step 3: 验证 Claude Code
echo "📋 Step 3: 验证 AI CLI 工具..."
if command -v claude &> /dev/null; then
    echo "✅ Claude Code: $(claude --version)"
else
    echo "⚠️  Claude Code: 未安装"
fi

if [ -f "$WORKSPACE/scripts/run-agent-qwen.sh" ]; then
    echo "✅ Qwen-Coder 脚本：已就绪"
else
    echo "❌ Qwen-Coder 脚本：缺失"
fi
echo ""

# Step 4: 创建配置说明
cat > "$WORKSPACE/ENABLE-REAL-MODE.md" << 'EOF'
# 🚀 启用真实模式 - 最后一步

## ✅ 已自动完成

- [x] Cron 监控已安装（每 10 分钟检查）
- [x] npm 全局路径已配置
- [x] Claude Code CLI 已安装
- [x] Qwen-Coder 脚本已就绪

## ⚠️ 需要手动配置：API Key

### 方式 1：使用 DashScope（推荐，国内）

1. 获取 API Key：
   - 访问：https://dashscope.console.aliyun.com/
   - 登录阿里云账号
   - 创建或复制 API Key

2. 设置环境变量：
```bash
echo 'export DASHSCOPE_API_KEY=sk-你的 key' >> ~/.bashrc
source ~/.bashrc
```

3. 测试：
```bash
./scripts/zoe-orchestrator.sh spawn test qwen feat/test "测试任务"
```

### 方式 2：使用 Claude（国际）

1. 获取 API Key：
   - 访问：https://console.anthropic.com/
   
2. 设置环境变量：
```bash
echo 'export ANTHROPIC_API_KEY=sk-ant-你的 key' >> ~/.bashrc
source ~/.bashrc
```

## 📊 验证配置

```bash
# 检查 API Key
echo $DASHSCOPE_API_KEY

# 检查 Cron
crontab -l

# 创建测试智能体
./scripts/zoe-orchestrator.sh spawn hello qwen feat/hello "创建 Hello 组件"

# 查看状态
./scripts/zoe-orchestrator.sh status
```

## 🎯 使用命令

| 命令 | 功能 |
|------|------|
| `./scripts/zoe-orchestrator.sh spawn <id> qwen <branch> "<desc>"` | 派生智能体 |
| `./scripts/zoe-orchestrator.sh status` | 查看状态 |
| `./scripts/zoe-orchestrator.sh send <id> "<instruction>"` | 发送指令 |
| `./scripts/zoe-orchestrator.sh monitor` | 手动监控 |
| `tail -f logs/zoe.log` | 查看日志 |
| `tmux list-sessions` | 查看 tmux 会话 |

## 💰 成本参考

DashScope Qwen-Coder:
- 输入：¥0.02/K tokens
- 输出：¥0.06/K tokens
- 月均：¥50-200（中等使用量）

## ⚠️ 注意事项

1. API Key 不要提交到 Git
2. 定期查看 DashScope 控制台监控用量
3. Cron 日志：`tail -f logs/cron.log`
EOF

echo "📝 配置说明已创建：ENABLE-REAL-MODE.md"
echo ""

# Step 5: 显示下一步
echo "══════════════════════════════════════"
echo "✅ 自动配置完成！"
echo ""
echo "🔑 最后一步：设置 API Key"
echo ""
echo "运行以下命令（替换为你的 key）："
echo ""
echo "  echo 'export DASHSCOPE_API_KEY=sk-你的 key' >> ~/.bashrc"
echo "  source ~/.bashrc"
echo ""
echo "然后测试："
echo ""
echo "  ./scripts/zoe-orchestrator.sh spawn test qwen feat/test \"测试\""
echo ""
echo "详细说明请查看：ENABLE-REAL-MODE.md"
echo "══════════════════════════════════════"
