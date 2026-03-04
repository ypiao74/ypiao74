import { Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { db } from '../../db';

const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-change-in-production';
const JWT_EXPIRY = '24h';

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

interface User {
  id: string;
  email: string;
  username: string;
  passwordHash: string;
  createdAt: Date;
}

/**
 * 用户注册 API
 * POST /api/auth/register
 */
export async function registerHandler(req: Request, res: Response): Promise<void> {
  try {
    const { username, email, password }: RegisterRequest = req.body;

    // 验证输入
    if (!username || !email || !password) {
      res.status(400).json({ error: 'Username, email, and password are required' });
      return;
    }

    // 验证邮箱格式
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      res.status(400).json({ error: 'Invalid email format' });
      return;
    }

    // 验证用户名
    if (username.length < 3 || username.length > 30) {
      res.status(400).json({ error: 'Username must be between 3 and 30 characters' });
      return;
    }

    const usernameRegex = /^[a-zA-Z0-9_]+$/;
    if (!usernameRegex.test(username)) {
      res.status(400).json({ error: 'Username can only contain letters, numbers, and underscores' });
      return;
    }

    // 验证密码强度
    if (password.length < 8) {
      res.status(400).json({ error: 'Password must be at least 8 characters' });
      return;
    }

    // 检查邮箱是否已存在
    const existingUser = await db.users.findOne({ where: { email: email.toLowerCase() } });
    if (existingUser) {
      res.status(409).json({ error: 'Email already registered' });
      return;
    }

    // 检查用户名是否已存在
    const existingUsername = await db.users.findOne({ where: { username } });
    if (existingUsername) {
      res.status(409).json({ error: 'Username already taken' });
      return;
    }

    // 创建新用户
    const user: User = {
      id: crypto.randomUUID(),
      email: email.toLowerCase(),
      username,
      passwordHash: await bcrypt.hash(password, 10),
      createdAt: new Date(),
    };

    await db.users.insert(user);

    // 生成 JWT token（自动登录）
    const token = jwt.sign(
      {
        userId: user.id,
        email: user.email,
      },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRY }
    );

    // 返回用户信息（不包含密码）
    res.status(201).json({
      user: {
        id: user.id,
        email: user.email,
        username: user.username,
      },
      token,
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
}
