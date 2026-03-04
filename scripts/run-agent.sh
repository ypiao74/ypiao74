#!/bin/bash
# Run Agent Script
# 在独立的 tmux 会话中运行 Codex/Claude Code 智能体

set -e

AGENT_TYPE="$1"
TASK_ID="$2"
WORKDIR="$(pwd)"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$AGENT_TYPE-$TASK_ID] $1"
}

log "Starting $AGENT_TYPE agent for task: $TASK_ID"
log "Working directory: $WORKDIR"

# 根据代理类型选择执行命令
case "$AGENT_TYPE" in
    codex)
        # Codex 已停用，使用 Claude Code 替代
        log "Launching AI agent (Codex → Claude Code)..."
        if command -v claude &> /dev/null; then
            # 检查 API Key
            if [ -z "$ANTHROPIC_API_KEY" ]; then
                log "⚠️  ANTHROPIC_API_KEY not set, running in simulation mode..."
            else
                log "✅ Using Claude Code (Codex replacement)..."
                claude --dangerously-skip-permissions \
                       --message "$TASK_ID" \
                       --workdir "$WORKDIR"
                exit 0
            fi
        fi
        
        # 模拟模式
        log "AI CLI not available, running in simulation mode..."
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
        ;;
    
    claude)
        # Claude Code
        log "Launching Claude Code agent..."
        if command -v claude &> /dev/null; then
            # 检查 API Key
            if [ -z "$ANTHROPIC_API_KEY" ]; then
                log "⚠️  ANTHROPIC_API_KEY not set, running in simulation mode..."
            else
                log "✅ Claude Code CLI found, starting real agent..."
                # 使用 --dangerously-skip-permissions 自动确认
                claude --dangerously-skip-permissions \
                       --message "$TASK_ID" \
                       --workdir "$WORKDIR"
                exit 0
            fi
        fi
        
        # 模拟模式（没有 CLI 或没有 API Key）
        log "Claude Code CLI not found or no API key, running in simulation mode..."
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
        ;;
    
    gemini)
        # Gemini Code Assist
        log "Launching Gemini agent..."
        if command -v gemini &> /dev/null; then
            gemini --task "$TASK_ID" --workdir "$WORKDIR"
        else
            log "Gemini CLI not found, running in simulation mode..."
            log "Generating design specs..."
            sleep 2
            log "Creating HTML/CSS mockup..."
            log "Agent idle. Send instructions or 'exit' to stop."
            while true; do
                read -t 60 input || true
                if [ "$input" == "exit" ]; then
                    break
                fi
            done
        fi
        ;;
    
    *)
        log "Unknown agent type: $AGENT_TYPE"
        exit 1
        ;;
esac

log "Agent $AGENT_TYPE-$TASK_ID completed"
