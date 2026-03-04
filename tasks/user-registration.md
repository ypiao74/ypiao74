# 任务：用户注册功能

## 业务背景
客户是电商平台，需要新用户注册流程来转化访客。

## 技术范围
- 涉及文件：
  - `src/api/auth/register.ts` - 注册 API
  - `src/components/RegisterForm.tsx` - 注册表单
  - `src/types/user.ts` - 用户类型定义
  - `src/utils/validation.ts` - 表单验证
- API 变更：是（新增 /api/auth/register）
- 数据库变更：是（users 表）
- UI 变更：是（注册页面）

## 验收标准
- [ ] 用户可以填写邮箱、密码、用户名注册
- [ ] 密码强度验证（至少 8 位，包含大小写和数字）
- [ ] 邮箱唯一性检查
- [ ] 注册成功后自动登录
- [ ] 错误提示友好

## 推荐智能体
**Codex** - 涉及后端 API + 前端表单 + 类型定义，需要跨文件推理

## 上下文文件
- `src/types/user.ts` - 用户数据结构
- `src/api/auth/login.ts` - 参考登录 API 结构
- `src/components/LoginForm.tsx` - 参考登录表单样式
