#!/bin/bash
# Cleanup Orphaned Worktrees
# 清理孤立的 worktree 和任务注册表

set -e

WORKSPACE="/home/admin/.openclaw/workspace"
WORKTREES_DIR="$WORKSPACE/worktrees"
TASKS_FILE="$WORKSPACE/.clawdbot/active-tasks.json"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [CLEANUP] $1"
}

log "Starting cleanup..."

# 获取所有活跃任务的分支名
active_branches=$(jq -r '.tasks[].branch' "$TASKS_FILE" 2>/dev/null || echo "")

# 遍历 worktrees 目录
if [ -d "$WORKTREES_DIR" ]; then
    for worktree in "$WORKTREES_DIR"/*; do
        if [ -d "$worktree" ]; then
            branch_name=$(basename "$worktree")
            
            # 检查是否在活跃任务列表中
            if ! echo "$active_branches" | grep -q "^$branch_name$"; then
                log "Found orphaned worktree: $branch_name"
                
                # 检查是否有未提交的更改
                cd "$worktree"
                if git status --porcelain | grep -q .; then
                    log "⚠️ Worktree $branch_name has uncommitted changes, skipping..."
                    continue
                fi
                
                # 删除 worktree
                cd "$WORKSPACE"
                git worktree remove "$worktree"
                log "Removed orphaned worktree: $branch_name"
            fi
        fi
    done
fi

log "Cleanup completed"
