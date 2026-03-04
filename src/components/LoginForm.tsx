import React, { useState } from 'react';
import { validateEmail } from '../utils/validation';
import './LoginForm.css';

interface LoginFormProps {
  onSuccess?: (user: User) => void;
  onForgotPassword?: () => void;
  onRegister?: () => void;
}

interface User {
  id: string;
  email: string;
  username: string;
}

interface LoginResponse {
  user: User;
  token: string;
}

export function LoginForm({ onSuccess, onForgotPassword, onRegister }: LoginFormProps) {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    rememberMe: false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setErrors({});

    // 验证邮箱
    const emailError = validateEmail(formData.email);
    if (emailError) {
      setErrors({ email: emailError });
      setLoading(false);
      return;
    }

    // 验证密码不为空
    if (!formData.password) {
      setErrors({ password: 'Password is required' });
      setLoading(false);
      return;
    }

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: formData.email,
          password: formData.password,
        }),
      });

      const data: LoginResponse = await response.json();

      if (!response.ok) {
        setErrors({ submit: data as any });
        return;
      }

      // 保存 token
      if (formData.rememberMe) {
        localStorage.setItem('token', data.token);
        localStorage.setItem('rememberMe', 'true');
      } else {
        sessionStorage.setItem('token', data.token);
      }

      // 登录成功
      onSuccess?.(data.user);
    } catch (error) {
      setErrors({ submit: 'Network error. Please try again.' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <form onSubmit={handleSubmit} className="login-form">
        <div className="login-header">
          <h1>Welcome Back</h1>
          <p>Sign in to continue to your account</p>
        </div>

        <div className="form-group">
          <label htmlFor="email">Email Address</label>
          <input
            id="email"
            type="email"
            placeholder="you@example.com"
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            disabled={loading}
            autoComplete="email"
          />
          {errors.email && <span className="error-message">{errors.email}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="password">Password</label>
          <div className="password-input-wrapper">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="Enter your password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              disabled={loading}
              autoComplete="current-password"
            />
            <button
              type="button"
              className="toggle-password"
              onClick={() => setShowPassword(!showPassword)}
              tabIndex={-1}
            >
              {showPassword ? '🙈' : '👁️'}
            </button>
          </div>
          {errors.password && <span className="error-message">{errors.password}</span>}
        </div>

        <div className="form-options">
          <label className="remember-me">
            <input
              type="checkbox"
              checked={formData.rememberMe}
              onChange={(e) => setFormData({ ...formData, rememberMe: e.target.checked })}
              disabled={loading}
            />
            <span>Remember me for 30 days</span>
          </label>
          {onForgotPassword && (
            <button
              type="button"
              className="forgot-password-link"
              onClick={onForgotPassword}
              tabIndex={-1}
            >
              Forgot password?
            </button>
          )}
        </div>

        {errors.submit && (
          <div className="error-banner">
            <span>⚠️</span>
            {typeof errors.submit === 'string' ? errors.submit : 'Login failed. Please check your credentials.'}
          </div>
        )}

        <button type="submit" className="submit-button" disabled={loading}>
          {loading ? (
            <>
              <span className="spinner"></span>
              Signing in...
            </>
          ) : (
            'Sign In'
          )}
        </button>

        {onRegister && (
          <p className="register-link">
            Don't have an account?{' '}
            <button type="button" onClick={onRegister} tabIndex={-1}>
              Sign up
            </button>
          </p>
        )}
      </form>

      <div className="login-footer">
        <p>🔒 Secure login with 256-bit encryption</p>
      </div>
    </div>
  );
}
