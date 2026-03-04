#!/bin/bash
# Push to GitHub Script
# 推送到 GitHub 的脚本

set -e

WORKSPACE="/home/admin/.openclaw/workspace"
cd "$WORKSPACE"

echo "╔════════════════════════════════════════════════════════╗"
echo "║        🚀 Pushing to GitHub: ypiao74/ypiao74          ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# Check connection
echo "📡 Testing connection..."
if git ls-remote origin > /dev/null 2>&1; then
    echo "✅ Connected to GitHub"
else
    echo "❌ Cannot connect to GitHub"
    echo ""
    echo "Please check:"
    echo "1. SSH key is added to GitHub: https://github.com/settings/keys"
    echo "2. Or use HTTPS with Personal Access Token"
    echo ""
    echo "To use HTTPS:"
    echo "  git remote set-url origin https://github.com/ypiao74/ypiao74.git"
    echo "  git push -u origin master"
    exit 1
fi

echo ""
echo "📦 Pushing master branch..."
git push -u origin master

echo ""
echo "📦 Pushing all branches..."
git push --all origin

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║              ✅ Push Complete!                         ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "📍 View your repository:"
echo "   https://github.com/ypiao74/ypiao74"
echo ""
echo "📊 View Actions:"
echo "   https://github.com/ypiao74/ypiao74/actions"
echo ""
