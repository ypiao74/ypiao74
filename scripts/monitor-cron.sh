#!/bin/bash
# Monitoring Cron Job
# 每 10 分钟运行一次，检查所有智能体任务状态

set -e

WORKSPACE="/home/admin/.openclaw/workspace"
TASKS_FILE="$WORKSPACE/.clawdbot/active-tasks.json"
LOGS_DIR="$WORKSPACE/logs"

mkdir -p "$LOGS_DIR"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [MONITOR] $1" | tee -a "$LOGS_DIR/monitor.log"
}

log "=== Monitoring cycle started ==="

# 检查每个运行中的任务
task_count=$(jq '.tasks | length' "$TASKS_FILE")
running_count=$(jq '[.tasks[] | select(.status == "running")] | length' "$TASKS_FILE")

log "Total tasks: $task_count, Running: $running_count"

# 遍历运行中的任务
jq -r '.tasks[] | select(.status == "running") | "\(.id)|\(.tmuxSession)|\(.branch)|\(.startedAt)"' "$TASKS_FILE" | while IFS='|' read -r task_id session branch started; do
    log "Checking task: $task_id (session: $session)"
    
    # 检查 tmux 会话是否存活
    if ! tmux has-session -t "$session" 2>/dev/null; then
        log "⚠️ Session $session not found, marking task as completed"
        
        # 更新任务状态
        local completed_at=$(date +%s)
        jq --arg id "$task_id" --argjson completed "$completed_at" \
           '(.tasks[] | select(.id == $id)) |= (. + {status: "done", completedAt: $completed})' \
           "$TASKS_FILE" > "$TASKS_FILE.tmp" && mv "$TASKS_FILE.tmp" "$TASKS_FILE"
        
        # 检查 CI 状态
        cd "$WORKSPACE/worktrees/$branch"
        if command -v gh &> /dev/null; then
            ci_status=$(gh pr checks --repo "$(git remote get-url origin | sed 's/.*:\(.*\).git/\1/')" 2>/dev/null | grep -c "✓" || echo "0")
            log "CI checks passed: $ci_status"
        fi
        
        # 发送通知
        log "📬 PR ready for review: $task_id"
        # 这里可以调用 Telegram 通知
        # curl -X POST "https://api.telegram.org/bot$TOKEN/sendMessage" \
        #     -d "chat_id=$CHAT_ID&text=🎉 PR #$task_id ready for review"
        
        continue
    fi
    
    # 检查运行时间（超过 2 小时警告）
    local now=$(date +%s)
    local runtime=$(( (now - started) / 60 ))
    if [ "$runtime" -gt 120 ]; then
        log "⚠️ Task $task_id running for $runtime minutes, may need attention"
    fi
done

# 清理 7 天前的已完成任务
log "Cleaning up old completed tasks..."
local now=$(date +%s)
jq --argjson now "$now" '
    .tasks = [.tasks[] | 
        if .status == "done" and ((.completedAt // 0) | type) == "number" then
            if ($now - .completedAt) > 604800 then empty else . end
        else . end
    ]
' "$TASKS_FILE" > "$TASKS_FILE.tmp" && mv "$TASKS_FILE.tmp" "$TASKS_FILE"

log "=== Monitoring cycle completed ==="
