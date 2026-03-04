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
        # Codex Agent
        log "Launching Codex agent..."
        # 如果有 codex CLI，使用它；否则模拟
        if command -v codex &> /dev/null; then
            codex --task "$TASK_ID" --workdir "$WORKDIR"
        else
            log "Codex CLI not found, running in simulation mode..."
            # 模拟 Codex 工作流程
            log "Reading task context..."
            log "Analyzing codebase..."
            log "Implementing changes..."
            log "Running tests..."
            log "Creating PR..."
        fi
        ;;
    
    claude)
        # Claude Code
        log "Launching Claude Code agent..."
        if command -v claude &> /dev/null; then
            claude --task "$TASK_ID" --workdir "$WORKDIR"
        else
            log "Claude Code CLI not found, running in simulation mode..."
            log "Reading task context..."
            log "Analyzing codebase..."
            log "Implementing changes..."
            log "Running tests..."
            log "Creating PR..."
        fi
        ;;
    
    gemini)
        # Gemini Code Assist
        log "Launching Gemini agent..."
        if command -v gemini &> /dev/null; then
            gemini --task "$TASK_ID" --workdir "$WORKDIR"
        else
            log "Gemini CLI not found, running in simulation mode..."
            log "Generating design specs..."
            log "Creating HTML/CSS mockup..."
        fi
        ;;
    
    *)
        log "Unknown agent type: $AGENT_TYPE"
        exit 1
        ;;
esac

log "Agent $AGENT_TYPE-$TASK_ID completed"
