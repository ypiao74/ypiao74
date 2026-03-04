#!/bin/bash
# Zoe - OpenClaw Agent Orchestrator
# 负责任务拆解、智能体派生、进度监控

set -e

WORKSPACE="/home/admin/.openclaw/workspace"
TASKS_FILE="$WORKSPACE/.clawdbot/active-tasks.json"
WORKTREES_DIR="$WORKSPACE/worktrees"
LOGS_DIR="$WORKSPACE/logs"

mkdir -p "$LOGS_DIR"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOGS_DIR/zoe.log"
}

# 派生新的智能体任务
spawn_agent() {
    local task_id="$1"
    local agent_type="$2"  # codex | claude | gemini
    local branch_name="$3"
    local description="$4"
    local context_file="$5"
    
    log "Spawning $agent_type agent for task: $task_id"
    
    # 创建 git worktree
    cd "$WORKSPACE"
    
    # 检查是否有远程仓库
    if git remote | grep -q origin; then
        git worktree add "$WORKTREES_DIR/$branch_name" -b "$branch_name" origin/main 2>/dev/null || {
            log "Worktree already exists, reusing..."
        }
    else
        # 没有远程仓库，基于当前分支创建
        git worktree add "$WORKTREES_DIR/$branch_name" -b "$branch_name" 2>/dev/null || {
            log "Worktree already exists, reusing..."
        }
    fi
    
    # 创建 tmux 会话
    local session_name="agent-$task_id"
    tmux new-session -d -s "$session_name" \
        -c "$WORKTREES_DIR/$branch_name" \
        "$WORKSPACE/scripts/run-agent.sh $agent_type $task_id"
    
    # 注册任务
    local timestamp=$(date +%s)
    jq --arg id "$task_id" \
       --arg session "$session_name" \
       --arg agent "$agent_type" \
       --arg desc "$description" \
       --arg branch "$branch_name" \
       --argjson started "$timestamp" \
       '.tasks += [{
           "id": $id,
           "tmuxSession": $session,
           "agent": $agent,
           "description": $desc,
           "branch": $branch,
           "startedAt": $started,
           "status": "running",
           "notifyOnComplete": true
       }]' "$TASKS_FILE" > "$TASKS_FILE.tmp" && mv "$TASKS_FILE.tmp" "$TASKS_FILE"
    
    log "Agent spawned: $session_name"
}

# 检查任务状态
check_task_status() {
    local task_id="$1"
    local session_name=$(jq -r ".tasks[] | select(.id == \"$task_id\") | .tmuxSession" "$TASKS_FILE")
    
    if tmux has-session -t "$session_name" 2>/dev/null; then
        echo "running"
    else
        echo "completed"
    fi
}

# 向运行中的智能体发送指令
send_instruction() {
    local task_id="$1"
    local instruction="$2"
    local session_name=$(jq -r ".tasks[] | select(.id == \"$task_id\") | .tmuxSession" "$TASKS_FILE")
    
    tmux send-keys -t "$session_name" "$instruction" Enter
    log "Sent instruction to $task_id: $instruction"
}

# 清理已完成的任务
cleanup_completed() {
    local completed_tasks=$(jq -r '.tasks[] | select(.status == "done") | .id' "$TASKS_FILE")
    
    for task_id in $completed_tasks; do
        local session_name=$(jq -r ".tasks[] | select(.id == \"$task_id\") | .tmuxSession" "$TASKS_FILE")
        tmux kill-session -t "$session_name" 2>/dev/null || true
        
        # 保留最近 7 天的任务记录，清理更早的
        local completed_at=$(jq -r ".tasks[] | select(.id == \"$task_id\") | .completedAt" "$TASKS_FILE")
        local now=$(date +%s)
        local age=$(( (now - completed_at) / 86400 ))
        
        if [ "$age" -gt 7 ]; then
            jq "del(.tasks[] | select(.id == \"$task_id\"))" "$TASKS_FILE" > "$TASKS_FILE.tmp" && mv "$TASKS_FILE.tmp" "$TASKS_FILE"
            log "Cleaned up old task: $task_id"
        fi
    done
}

# 主循环 - 被 cron 调用
monitor_loop() {
    log "Starting monitoring loop..."
    
    # 检查所有运行中的任务
    jq -r '.tasks[] | select(.status == "running") | .id' "$TASKS_FILE" | while read task_id; do
        local status=$(check_task_status "$task_id")
        
        if [ "$status" == "completed" ]; then
            log "Task $task_id completed, updating status..."
            # 这里可以添加 PR 创建、代码审查等逻辑
            jq "(.tasks[] | select(.id == \"$task_id\") | .status) = \"done\"" "$TASKS_FILE" > "$TASKS_FILE.tmp" && mv "$TASKS_FILE.tmp" "$TASKS_FILE"
            
            # 发送通知
            # send_telegram_notification "PR ready for review: $task_id"
        fi
    done
    
    cleanup_completed
    log "Monitoring loop completed"
}

# CLI 入口
case "${1:-}" in
    spawn)
        spawn_agent "$2" "$3" "$4" "$5" "$6"
        ;;
    send)
        send_instruction "$2" "$3"
        ;;
    monitor)
        monitor_loop
        ;;
    status)
        cat "$TASKS_FILE" | jq .
        ;;
    *)
        echo "Usage: $0 {spawn|send|monitor|status} [args...]"
        exit 1
        ;;
esac
