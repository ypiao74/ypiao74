# 🇨🇳 国内 AI 模型配置指南

## 已配置：通义千问 Qwen-Coder

### ✅ 优势

- **国内访问** - 阿里云 DashScope，无网络障碍
- **中文友好** - 原生中文支持，理解更准确
- **已有 API Key** - 当前对话就在用，无需新配置
- **代码能力强** - Qwen-Coder 专为编程优化

---

## 🔑 API Key 配置

### 查看当前 API Key

```bash
# OpenClaw 已配置的 DashScope API Key
cat ~/.openclaw/config/models.json
```

### 获取/管理 API Key

1. 访问：https://dashscope.console.aliyun.com/
2. 登录阿里云账号
3. 进入 **API Key 管理**
4. 创建或查看现有 Key

### 设置环境变量

```bash
# 临时设置
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx

# 永久设置（添加到 ~/.bashrc）
echo 'export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx' >> ~/.bashrc
source ~/.bashrc
```

---

## 🤖 使用 Qwen-Coder 智能体

### 派生智能体

```bash
# 使用 qwen 类型（自动调用 Qwen-Coder）
./scripts/zoe-orchestrator.sh spawn feat-login qwen feat/login "实现登录功能"

# 或直接使用默认（未指定类型时用 Qwen）
./scripts/zoe-orchestrator.sh spawn feat-register default feat/register "实现注册功能"
```

### 查看状态

```bash
./scripts/zoe-orchestrator.sh status
```

### 查看日志

```bash
# 查看智能体日志
tmux capture-pane -t agent-feat-login -p

# 查看 Zoe 日志
tail -f logs/zoe.log
```

---

## 📊 支持的国内模型

| 模型 | 提供商 | 脚本参数 | API |
|------|--------|---------|-----|
| **Qwen-Coder** | 阿里云 | `qwen` | DashScope ✅ |
| **CodeGeeX** | 智谱 AI | `codegeex` | 需配置 |
| **通义灵码** | 阿里云 | `lingma` | DashScope |

---

## 💰 定价（DashScope）

| 模型 | 输入 | 输出 | 免费额度 |
|------|------|------|---------|
| Qwen-Coder-Plus | ¥0.02/K tokens | ¥0.06/K tokens | 新用户赠送 |
| Qwen-Max | ¥0.04/K tokens | ¥0.12/K tokens | 每月免费 |

**月均成本：** ¥50-200（取决于使用量）

---

## 🔧 脚本说明

### run-agent-qwen.sh

```bash
#!/bin/bash
# 使用 Qwen-Coder API 实现智能体功能

# 功能：
# 1. 读取项目上下文
# 2. 调用 DashScope API
# 3. 解析响应并创建文件
# 4. 支持实时指令干预
```

### 工作流程

```
1. 读取项目文件作为上下文
   ↓
2. 构建提示词（任务 + 项目结构）
   ↓
3. 调用 Qwen-Coder API
   ↓
4. 解析响应，提取代码块
   ↓
5. 创建/修改文件
   ↓
6. 等待进一步指令
```

---

## 🧪 测试示例

```bash
# 1. 设置 API Key
export DASHSCOPE_API_KEY=sk-xxxxx

# 2. 创建测试任务
cd /home/admin/.openclaw/workspace
./scripts/zoe-orchestrator.sh spawn test-qwen qwen feat/test-qwen "创建一个简单的计算器组件"

# 3. 查看状态
./scripts/zoe-orchestrator.sh status

# 4. 查看生成的代码
cat worktrees/feat/test-qwen/response-test-qwen.md
```

---

## ⚠️ 注意事项

1. **API Key 安全** - 不要提交到 Git
2. **费用监控** - 定期查看 DashScope 控制台
3. **速率限制** - 默认 QPS 限制，大批量需申请
4. **上下文长度** - Qwen-Coder 支持 128K tokens

---

## 🚀 进阶使用

### 批量任务

```bash
# 同时派生多个智能体
./scripts/zoe-orchestrator.sh spawn feat-auth qwen feat/auth "认证模块"
./scripts/zoe-orchestrator.sh spawn feat-user qwen feat/user "用户管理"
./scripts/zoe-orchestrator.sh spawn feat-role qwen feat/role "权限管理"

# 最多支持 4 个并发
```

### 自定义提示词

编辑 `config/zoe-prompts.md` 定制任务描述模板。

---

## 📞 支持

- DashScope 文档：https://help.aliyun.com/zh/dashscope/
- OpenClaw 文档：/opt/openclaw/docs
- 社区：https://discord.com/invite/clawd
