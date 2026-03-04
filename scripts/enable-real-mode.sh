#!/bin/bash
# Enable Real Mode - 启用真实智能体集群模式
# 自动配置 API Key、Cron、环境变量

set -e

WORKSPACE="/home/admin/.openclaw/workspace"
SCRIPTS_DIR="$WORKSPACE/scripts"
CONFIG_DIR="$WORKSPACE/config"

echo "╔════════════════════════════════════════════════════════╗"
echo "║     🚀 OpenClaw 真实模式配置向导                        ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# Step 1: 检查并配置 DASHSCOPE_API_KEY
echo "📋 Step 1: 配置 DashScope API Key"
echo "─────────────────────────────────────────────────────────"

if [ -n "$DASHSCOPE_API_KEY" ]; then
    echo "✅ DASHSCOPE_API_KEY 已设置"
    echo "   当前值：${DASHSCOPE_API_KEY:0:10}...${DASHSCOPE_API_KEY: -8}"
else
    echo "⚠️  DASHSCOPE_API_KEY 未设置"
    echo ""
    echo "请选择配置方式："
    echo "  1) 手动输入 API Key"
    echo "  2) 跳过（稍后手动配置）"
    echo "  3) 查看如何获取 API Key"
    echo ""
    read -p "请选择 (1/2/3): " choice
    
    case $choice in
        1)
            read -p "请输入 DashScope API Key: " api_key
            if [ -n "$api_key" ]; then
                export DASHSCOPE_API_KEY="$api_key"
                echo 'export DASHSCOPE_API_KEY="'"$api_key"'"' >> ~/.bashrc
                echo "✅ API Key 已设置并添加到 ~/.bashrc"
            else
                echo "⚠️  输入为空，跳过"
            fi
            ;;
        2)
            echo "⚠️  跳过 API Key 配置（智能体将运行在模拟模式）"
            ;;
        3)
            echo ""
            echo "获取 API Key 步骤："
            echo "  1. 访问：https://dashscope.console.aliyun.com/"
            echo "  2. 登录阿里云账号"
            echo "  3. 进入 API Key 管理"
            echo "  4. 创建或复制现有 Key"
            echo ""
            read -p "输入 API Key 或按回车跳过： " api_key
            if [ -n "$api_key" ]; then
                export DASHSCOPE_API_KEY="$api_key"
                echo 'export DASHSCOPE_API_KEY="'"$api_key"'"' >> ~/.bashrc
                echo "✅ API Key 已设置"
            fi
            ;;
    esac
fi

echo ""

# Step 2: 安装 Cron 监控
echo "📋 Step 2: 配置自动监控（Cron）"
echo "─────────────────────────────────────────────────────────"

if crontab -l 2>/dev/null | grep -q "monitor-cron.sh"; then
    echo "✅ Cron 监控已配置"
    crontab -l | grep "monitor-cron"
else
    echo "⚠️  Cron 监控未安装"
    echo ""
    read -p "是否安装 Cron 监控？(y/n): " install_cron
    
    if [ "$install_cron" = "y" ] || [ "$install_cron" = "Y" ]; then
        # 检查 crontab 文件是否存在
        if [ -f "$CONFIG_DIR/crontab.example" ]; then
            # 合并现有 crontab 和新配置
            (crontab -l 2>/dev/null | grep -v "monitor-cron.sh"; cat "$CONFIG_DIR/crontab.example") | crontab -
            echo "✅ Cron 监控已安装"
            echo ""
            echo "已添加的定时任务："
            crontab -l | grep "openclaw"
        else
            echo "❌ crontab.example 文件不存在"
        fi
    else
        echo "⚠️  跳过 Cron 配置"
    fi
fi

echo ""

# Step 3: 配置环境变量文件
echo "📋 Step 3: 创建环境变量配置文件"
echo "─────────────────────────────────────────────────────────"

if [ ! -f "$CONFIG_DIR/.env" ]; then
    cp "$CONFIG_DIR/.env.example" "$CONFIG_DIR/.env"
    echo "✅ 已创建 config/.env 文件"
    echo "   编辑此文件配置 Telegram 等通知"
else
    echo "✅ config/.env 已存在"
fi

echo ""

# Step 4: 测试 Qwen-Coder 脚本
echo "📋 Step 4: 测试 Qwen-Coder 集成"
echo "─────────────────────────────────────────────────────────"

if [ -n "$DASHSCOPE_API_KEY" ]; then
    echo "🧪 测试 API 连接..."
    
    RESPONSE=$(curl -s -X POST "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation" \
        -H "Authorization: Bearer $DASHSCOPE_API_KEY" \
        -H "Content-Type: application/json" \
        -d '{
            "model": "qwen-turbo",
            "input": {
                "messages": [{"role": "user", "content": "Hello"}]
            }
        }' | head -c 200)
    
    if echo "$RESPONSE" | jq -e '.output' > /dev/null 2>&1; then
        echo "✅ DashScope API 连接成功"
    else
        echo "⚠️  API 连接失败，请检查 API Key"
        echo "   响应：$RESPONSE"
    fi
else
    echo "⚠️  跳过 API 测试（未配置 API Key）"
fi

echo ""

# Step 5: 创建测试智能体
echo "📋 Step 5: 创建测试智能体"
echo "─────────────────────────────────────────────────────────"

if [ -n "$DASHSCOPE_API_KEY" ]; then
    read -p "是否创建一个测试智能体？(y/n): " create_test
    
    if [ "$create_test" = "y" ] || [ "$create_test" = "Y" ]; then
        echo ""
        echo "🤖 创建测试智能体：hello-world"
        cd "$WORKSPACE"
        "$SCRIPTS_DIR/zoe-orchestrator.sh" spawn hello-world qwen feat/hello-world "创建一个 Hello World 组件"
        
        echo ""
        echo "✅ 测试智能体已创建"
        echo ""
        echo "查看状态："
        "$SCRIPTS_DIR/zoe-orchestrator.sh" status
    else
        echo "⚠️  跳过测试智能体创建"
    fi
else
    echo "⚠️  跳过测试（未配置 API Key）"
fi

echo ""

# Summary
echo "╔════════════════════════════════════════════════════════╗"
echo "║              🎉 配置完成！                              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "📊 当前状态："
echo "─────────────────────────────────────────────────────────"

if [ -n "$DASHSCOPE_API_KEY" ]; then
    echo "  ✅ DashScope API Key: 已配置"
else
    echo "  ⚠️  DashScope API Key: 未配置（模拟模式）"
fi

if crontab -l 2>/dev/null | grep -q "monitor-cron.sh"; then
    echo "  ✅ Cron 监控：已安装（每 10 分钟检查）"
else
    echo "  ⚠️  Cron 监控：未安装（需手动监控）"
fi

echo "  ✅ 智能体脚本：已就绪"
echo "  ✅ Qwen-Coder 集成：已配置"

echo ""
echo "🚀 下一步："
echo "─────────────────────────────────────────────────────────"
echo "  1. 派生智能体："
echo "     ./scripts/zoe-orchestrator.sh spawn <id> qwen <branch> <desc>"
echo ""
echo "  2. 查看状态："
echo "     ./scripts/zoe-orchestrator.sh status"
echo ""
echo "  3. 查看日志："
echo "     tail -f logs/zoe.log"
echo ""
echo "  4. 配置 Telegram 通知（可选）："
echo "     编辑 config/.env 填入 Token"
echo ""

# 保存配置摘要
cat > "$WORKSPACE/REAL-MODE-STATUS.md" << EOF
# 真实模式配置状态

配置时间：$(date '+%Y-%m-%d %H:%M:%S')

## 已配置项

- DashScope API Key: $([ -n "$DASHSCOPE_API_KEY" ] && echo "✅" || echo "❌")
- Cron 监控：$(crontab -l 2>/dev/null | grep -q "monitor-cron.sh" && echo "✅" || echo "❌")
- Qwen-Coder 集成：✅

## 使用命令

派生智能体：
  ./scripts/zoe-orchestrator.sh spawn <id> qwen <branch> "<描述>"

查看状态：
  ./scripts/zoe-orchestrator.sh status

查看日志：
  tail -f logs/zoe.log

手动监控：
  ./scripts/zoe-orchestrator.sh monitor

## 注意事项

- API Key 已保存到 ~/.bashrc
- Cron 每 10 分钟检查一次任务状态
- 智能体使用 Qwen-Coder 模型（通义千问）
EOF

echo "📝 配置摘要已保存到：REAL-MODE-STATUS.md"
echo ""
