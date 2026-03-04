#!/bin/bash
# Test Setup Script
# 验证智能体集群系统是否正确配置

set -e

WORKSPACE="/home/admin/.openclaw/workspace"

echo "🔍 Testing Agent Cluster Setup..."
echo ""

# 检查必要工具
check_tool() {
    if command -v "$1" &> /dev/null; then
        echo "✅ $1: installed"
    else
        echo "⚠️  $1: not found (optional)"
    fi
}

echo "=== Tool Check ==="
check_tool git
check_tool tmux
check_tool jq
check_tool gh
check_tool codex
check_tool claude
check_tool gemini
echo ""

# 检查目录结构
echo "=== Directory Structure ==="
for dir in .clawdbot scripts config worktrees logs reports; do
    if [ -d "$WORKSPACE/$dir" ]; then
        echo "✅ $dir/"
    else
        echo "❌ $dir/ missing"
        mkdir -p "$WORKSPACE/$dir"
        echo "   Created $dir/"
    fi
done
echo ""

# 检查脚本可执行权限
echo "=== Script Permissions ==="
for script in "$WORKSPACE"/scripts/*.sh; do
    if [ -x "$script" ]; then
        echo "✅ $(basename $script): executable"
    else
        echo "⚠️  $(basename $script): not executable"
        chmod +x "$script"
        echo "   Fixed permissions"
    fi
done
echo ""

# 检查任务注册表
echo "=== Task Registry ==="
if [ -f "$WORKSPACE/.clawdbot/active-tasks.json" ]; then
    echo "✅ active-tasks.json exists"
    echo "   Content:"
    cat "$WORKSPACE/.clawdbot/active-tasks.json" | jq .
else
    echo "❌ active-tasks.json missing"
fi
echo ""

# 检查 Zoe 提示词
echo "=== Zoe Prompts ==="
if [ -f "$WORKSPACE/config/zoe-prompts.md" ]; then
    echo "✅ zoe-prompts.md exists"
else
    echo "❌ zoe-prompts.md missing"
fi
echo ""

# 测试 Zoe 状态命令
echo "=== Zoe Status Test ==="
"$WORKSPACE/scripts/zoe-orchestrator.sh" status
echo ""

echo "🎉 Setup test completed!"
echo ""
echo "Next steps:"
echo "1. Configure Telegram notifications (optional): edit config/.env.example"
echo "2. Install cron jobs: crontab config/crontab.example"
echo "3. Spawn your first agent: ./scripts/zoe-orchestrator.sh spawn <id> <type> <branch> <desc>"
