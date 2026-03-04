# 🚀 推送到 GitHub 指南

## ✅ 已完成配置

| 配置项 | 状态 | 说明 |
|--------|------|------|
| **SSH Key** | ✅ 已生成 | `~/.ssh/id_ed25519` |
| **远程仓库** | ✅ 已配置 | `git@github.com:ypiao74/ypiao74.git` |
| **GitHub Actions** | ✅ 已创建 | `.github/workflows/ci.yml` |
| **known_hosts** | ✅ 已添加 | GitHub 主机密钥 |

---

## ⚠️ 需要手动操作：添加 SSH Key 到 GitHub

### 步骤 1：复制 SSH 公钥

```bash
cat ~/.ssh/id_ed25519.pub
```

**公钥内容：**
```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPDwjQjGW+Mtd2GYjnjcqe9LAe7bVYEqk2xch9ZxfZPT ypiao74@github
```

### 步骤 2：添加到 GitHub

1. **访问：** https://github.com/settings/keys
2. **点击：** "New SSH key"
3. **标题：** `OpenClaw Server`
4. **粘贴：** 上面的公钥
5. **点击：** "Add SSH key"

---

## 📦 推送命令

### 测试 SSH 连接

```bash
ssh -T git@github.com
# 应该显示：Hi ypiao74! You've successfully authenticated
```

### 推送所有分支

```bash
cd /home/admin/.openclaw/workspace

# 推送 master 分支
git push -u origin master

# 推送所有分支
git push --all origin

# 推送标签
git push --tags origin
```

### 或者使用 HTTPS（需要 Token）

```bash
# 更改为 HTTPS
git remote set-url origin https://github.com/ypiao74/ypiao74.git

# 推送（会提示输入密码/token）
git push -u origin master

# 改回 SSH
git remote set-url origin git@github.com:ypiao74/ypiao74.git
```

---

## 📊 当前状态

### 本地分支

```
  feat/android-hello
  feat/counter
  feat/demo
  feat/hello-world
  feat/test
  feat/user-registration
* master
```

### 待推送的提交

```
88be4ad ci: Add GitHub Actions CI/CD workflow
2c89fed docs: Add Android Hello World test result report
d342b1d docs: Record real AI test success - Qwen-Coder generated Counter.tsx
... (共 11 个提交)
```

---

## 🔍 验证推送

```bash
# 查看远程分支
git branch -r

# 查看推送状态
git status

# 测试连接
git ls-remote origin
```

---

## 🎯 GitHub Actions

推送后自动触发：

1. **CI 测试** - 每次 push 到 master
2. **部署通知** - master 分支推送后

查看工作流：
- 访问：https://github.com/ypiao74/ypiao74/actions

---

## 📝 快速推送脚本

创建 `push.sh`：

```bash
#!/bin/bash
cd /home/admin/.openclaw/workspace
echo "🚀 Pushing to GitHub..."
git push -u origin master
git push --all origin
echo "✅ Push complete!"
```

---

## ⚠️ 常见问题

### Permission denied (publickey)

**原因：** SSH Key 未添加到 GitHub

**解决：**
1. 复制 `~/.ssh/id_ed25519.pub` 内容
2. 添加到 https://github.com/settings/keys

### Could not read from remote repository

**原因：** SSH 连接问题

**解决：**
```bash
ssh-keyscan github.com >> ~/.ssh/known_hosts
ssh -T git@github.com
```

---

**下一步：** 添加 SSH Key 到 GitHub，然后执行推送命令！
