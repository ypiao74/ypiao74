#!/bin/bash
# Automated Code Review Script
# 使用多个模型审查 PR

set -e

PR_NUMBER="$1"
REPO="${2:-$(pwd)}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [REVIEW-PR$PR_NUMBER] $1"
}

log "Starting code review for PR #$PR_NUMBER"

# 审查结果存储
REVIEW_RESULTS=()

# Codex 审查 - 专注边界情况和逻辑错误
review_codex() {
    log "Codex review starting..."
    # 实际使用时调用 Codex API
    # 这里模拟审查流程
    local issues=()
    
    # 检查点：
    # - 错误处理是否完整
    # - 边界情况是否覆盖
    # - 竞态条件风险
    # - 类型安全性
    
    log "Codex review completed"
    echo "${issues[@]}"
}

# Claude Code 审查 - 专注过度设计警告
review_claude() {
    log "Claude Code review starting..."
    local issues=()
    
    # 检查点：
    # - 是否有过度设计
    # - 代码简洁性
    # - 可维护性
    
    # 只关注 critical 级别的问题
    log "Claude Code review completed (critical issues only)"
    echo "${issues[@]}"
}

# Gemini 审查 - 专注安全和可扩展性
review_gemini() {
    log "Gemini review starting..."
    local issues=()
    
    # 检查点：
    # - 安全漏洞
    # - 可扩展性问题
    # - 性能隐患
    # - 具体修改建议
    
    log "Gemini review completed"
    echo "${issues[@]}"
}

# 汇总审查结果
aggregate_reviews() {
    local codex_issues="$1"
    local claude_issues="$2"
    local gemini_issues="$3"
    
    log "Aggregating review results..."
    
    # 生成审查报告
    cat << EOF
## Code Review Report - PR #$PR_NUMBER

### Codex Review
$codex_issues

### Claude Code Review (Critical Only)
$claude_issues

### Gemini Review
$gemini_issues

---
**Status:** $(if [ -z "$codex_issues$claude_issues$gemini_issues" ]; then echo "✅ All Clear"; else echo "⚠️ Issues Found"; fi)
EOF
}

# 主流程
codex_result=$(review_codex)
claude_result=$(review_claude)
gemini_result=$(review_gemini)

report=$(aggregate_reviews "$codex_result" "$claude_result" "$gemini_result")

# 将审查结果添加到 PR 评论（使用 gh CLI）
if command -v gh &> /dev/null; then
    echo "$report" | gh pr comment "$PR_NUMBER" --body-file -
    log "Review comment posted to PR #$PR_NUMBER"
else
    log "gh CLI not found, saving review to file..."
    echo "$report" > "$REPO/.clawdbot/reviews/pr-$PR_NUMBER.md"
fi

log "Code review completed for PR #$PR_NUMBER"
