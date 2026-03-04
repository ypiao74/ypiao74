import { Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { db } from '../../db';

const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-change-in-production';
const JWT_EXPIRY = '24h';

interface LoginRequest {
  email: string;
  password: string;
}

interface User {
  id: string;
  email: string;
  username: string;
  passwordHash: string;
}

interface LoginResponse {
  user: {
    id: string;
    email: string;
    username: string;
  };
  token: string;
}

/**
 * 用户登录 API
 * POST /api/auth/login
 */
export async function loginHandler(req: Request, res: Response): Promise<void> {
  try {
    const { email, password }: LoginRequest = req.body;

    // 验证输入
    if (!email || !password) {
      res.status(400).json({ error: 'Email and password are required' });
      return;
    }

    // 查找用户
    const user = await db.users.findOne({ where: { email: email.toLowerCase() } });

    if (!user) {
      // 为了安全，不区分用户不存在和密码错误
      res.status(401).json({ error: 'Invalid email or password' });
      return;
    }

    // 验证密码
    const isValidPassword = await bcrypt.compare(password, user.passwordHash);

    if (!isValidPassword) {
      res.status(401).json({ error: 'Invalid email or password' });
      return;
    }

    // 生成 JWT token
    const token = jwt.sign(
      {
        userId: user.id,
        email: user.email,
      },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRY }
    );

    // 返回用户信息（不包含密码）
    const response: LoginResponse = {
      user: {
        id: user.id,
        email: user.email,
        username: user.username,
      },
      token,
    };

    // 记录登录日志
    await db.loginLogs.insert({
      userId: user.id,
      timestamp: new Date(),
      ipAddress: req.ip,
      userAgent: req.get('user-agent'),
      success: true,
    });

    res.status(200).json(response);
  } catch (error) {
    console.error('Login error:', error);

    // 记录失败日志
    if (req.body?.email) {
      await db.loginLogs.insert({
        email: req.body.email,
        timestamp: new Date(),
        ipAddress: req.ip,
        userAgent: req.get('user-agent'),
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
      }).catch(() => {}); // 忽略日志错误
    }

    res.status(500).json({ error: 'Internal server error' });
  }
}

/**
 * JWT 中间件 - 验证 token
 */
export function authMiddleware(req: Request, res: Response, next: Function): void {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({ error: 'No token provided' });
    return;
  }

  const token = authHeader.substring(7);

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    (req as any).user = decoded;
    next();
  } catch (error) {
    res.status(401).json({ error: 'Invalid token' });
    return;
  }
}

/**
 * 登出 API
 * POST /api/auth/logout
 */
export async function logoutHandler(req: Request, res: Response): Promise<void> {
  // 如果是基于 token 的认证，客户端只需删除 token
  // 这里可以添加黑名单逻辑或使用 refresh token 方案

  res.status(200).json({ message: 'Logged out successfully' });
}

/**
 * 验证当前用户 API
 * GET /api/auth/me
 */
export async function getCurrentUserHandler(req: Request, res: Response): Promise<void> {
  try {
    const user = (req as any).user;

    if (!user) {
      res.status(401).json({ error: 'Not authenticated' });
      return;
    }

    const dbUser = await db.users.findOne({ where: { id: user.userId } });

    if (!dbUser) {
      res.status(404).json({ error: 'User not found' });
      return;
    }

    res.status(200).json({
      user: {
        id: dbUser.id,
        email: dbUser.email,
        username: dbUser.username,
      },
    });
  } catch (error) {
    console.error('Get current user error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
}
