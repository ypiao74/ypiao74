#!/bin/bash
# Run Agent with Qwen-Coder (DashScope)
# 使用通义千问代码模型作为智能体

set -e

AGENT_TYPE="$1"
TASK_ID="$2"
WORKDIR="$(pwd)"
DASHSCOPE_API_KEY="${DASHSCOPE_API_KEY:-$DASHSCOPE_API_KEY}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$AGENT_TYPE-$TASK_ID] $1"
}

log "Starting Qwen-Coder agent for task: $TASK_ID"
log "Working directory: $WORKDIR"

# 检查 API Key
if [ -z "$DASHSCOPE_API_KEY" ]; then
    log "⚠️  DASHSCOPE_API_KEY not set, running in simulation mode..."
    log "Reading task context..."
    sleep 2
    log "Analyzing codebase..."
    sleep 2
    log "Implementing changes..."
    sleep 2
    log "Running tests..."
    sleep 2
    log "Creating PR..."
    log "Agent idle. Send instructions or 'exit' to stop."
    while true; do
        read -t 60 input || true
        if [ "$input" == "exit" ]; then
            break
        fi
    done
    exit 0
fi

log "✅ DASHSCOPE_API_KEY found, starting Qwen-Coder agent..."

# 读取项目文件作为上下文
CONTEXT=""
if [ -d "src" ]; then
    CONTEXT=$(find src -name "*.ts" -o -name "*.tsx" -o -name "*.js" | head -10 | xargs cat 2>/dev/null | head -5000)
fi

# 构建提示词
PROMPT="你是一个专业的 AI 编程助手。请帮我完成以下任务：

任务：$TASK_ID
工作目录：$WORKDIR

请：
1. 分析任务需求
2. 创建或修改必要的代码文件
3. 确保代码符合 TypeScript/React 最佳实践
4. 输出完整的文件内容

当前项目结构：
$(find . -type f \( -name "*.ts" -o -name "*.tsx" -o -name "*.json" \) | head -20)

请开始实现："

# 调用 Qwen-Coder API
log "Calling Qwen-Coder API..."

RESPONSE=$(curl -s -X POST "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation" \
    -H "Authorization: Bearer $DASHSCOPE_API_KEY" \
    -H "Content-Type: application/json" \
    -d "{
        \"model\": \"qwen-coder-plus\",
        \"input\": {
            \"messages\": [
                {
                    \"role\": \"system\",
                    \"content\": \"你是一个专业的 AI 编程助手，擅长 TypeScript、React、Node.js 开发。你会生成高质量、可维护的代码。\"
                },
                {
                    \"role\": \"user\",
                    \"content\": \"$PROMPT\"
                }
            ]
        },
        \"parameters\": {
            \"max_tokens\": 4000,
            \"temperature\": 0.7,
            \"top_p\": 0.9
        }
    }")

# 解析响应
if echo "$RESPONSE" | jq -e '.output.choices[0].message.content' > /dev/null 2>&1; then
    CODE=$(echo "$RESPONSE" | jq -r '.output.choices[0].message.content')
    log "✅ Received response from Qwen-Coder"
    
    # 保存响应到文件
    echo "$CODE" > "$WORKDIR/response-$TASK_ID.md"
    log "Response saved to response-$TASK_ID.md"
    
    # 提取代码块并创建文件（简单实现）
    echo "$CODE" | grep -oP '```[\s\S]*?```' | while read -r block; do
        lang=$(echo "$block" | head -1 | sed 's/```//')
        case "$lang" in
            *typescript*|*ts*)
                filename=$(echo "$block" | grep -oP 'filename: \K\S+' || echo "generated.ts")
                echo "$block" | sed '1d;$d' > "$WORKDIR/$filename"
                log "Created file: $filename"
                ;;
            *tsx*)
                filename=$(echo "$block" | grep -oP 'filename: \K\S+' || echo "generated.tsx")
                echo "$block" | sed '1d;$d' > "$WORKDIR/$filename"
                log "Created file: $filename"
                ;;
        esac
    done
else
    log "❌ API call failed: $RESPONSE"
    exit 1
fi

log "Qwen-Coder agent completed"

# 保持会话活跃
log "Agent idle. Send instructions or 'exit' to stop."
while true; do
    read -t 60 input || true
    if [ "$input" == "exit" ]; then
        break
    fi
done
