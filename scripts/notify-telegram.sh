#!/bin/bash
# Telegram Notification Script
# 发送通知到 Telegram

TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-}"

send_notification() {
    local message="$1"
    local parse_mode="${2:-Markdown}"
    
    if [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$TELEGRAM_CHAT_ID" ]; then
        echo "Telegram credentials not configured"
        return 1
    fi
    
    curl -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
        -d "chat_id=$TELEGRAM_CHAT_ID" \
        -d "text=$message" \
        -d "parse_mode=$parse_mode"
}

# CLI usage
case "${1:-}" in
    pr-ready)
        send_notification "🎉 *PR Ready for Review*

PR #$2 is ready for merge.

✅ All CI checks passed
✅ Code reviews completed
✅ Screenshots attached (if UI changes)

Review: $3"
        ;;
    
    agent-error)
        send_notification "⚠️ *Agent Error*

Task: $2
Error: $3

Please check the logs."
        ;;
    
    daily-summary)
        send_notification "📊 *Daily Summary*

Tasks completed: $2
PRs merged: $3
Issues found: $4"
        ;;
    
    *)
        send_notification "$1"
        ;;
esac
