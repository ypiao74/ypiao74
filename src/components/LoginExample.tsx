/**
 * 登录页面使用示例
 * 
 * 在 App.tsx 或其他页面中使用：
 */

import React from 'react';
import { LoginForm } from './LoginForm';

// 示例：完整的登录页面组件
export function LoginPage() {
  const handleLoginSuccess = (user: { id: string; email: string; username: string }) => {
    console.log('Login successful!', user);
    // 跳转到仪表盘或其他页面
    window.location.href = '/dashboard';
  };

  const handleForgotPassword = () => {
    console.log('Navigate to forgot password page');
    window.location.href = '/forgot-password';
  };

  const handleRegister = () => {
    console.log('Navigate to register page');
    window.location.href = '/register';
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <LoginForm
        onSuccess={handleLoginSuccess}
        onForgotPassword={handleForgotPassword}
        onRegister={handleRegister}
      />
    </div>
  );
}

// 示例：嵌入到其他表单中
export function AppWithLogin() {
  const [isLoggedIn, setIsLoggedIn] = React.useState(false);
  const [currentUser, setCurrentUser] = React.useState<{ id: string; email: string; username: string } | null>(null);

  return (
    <div>
      {!isLoggedIn ? (
        <LoginForm
          onSuccess={(user) => {
            setIsLoggedIn(true);
            setCurrentUser(user);
          }}
          onRegister={() => console.log('Go to register')}
        />
      ) : (
        <div>
          <h1>Welcome, {currentUser?.username}!</h1>
          <button onClick={() => setIsLoggedIn(false)}>Logout</button>
        </div>
      )}
    </div>
  );
}

// 导出类型
export type { User } from './LoginForm';
