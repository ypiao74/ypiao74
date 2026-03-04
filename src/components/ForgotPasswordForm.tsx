import React, { useState } from 'react';
import { validateEmail } from '../utils/validation';
import './ForgotPasswordForm.css';

interface ForgotPasswordFormProps {
  onBack?: () => void;
  onSent?: (email: string) => void;
}

type Step = 'email' | 'sent' | 'reset';

export function ForgotPasswordForm({ onBack, onSent }: ForgotPasswordFormProps) {
  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  const handleSendResetLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    const err = validateEmail(email);
    if (err) { setErrors({ email: err }); return; }

    setLoading(true);

    try {
      const response = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });

      const data = await response.json();

      if (!response.ok) {
        setErrors({ submit: data.error });
        return;
      }

      setStep('sent');
      onSent?.(email);
    } catch (error) {
      setErrors({ submit: 'Failed to send reset link. Please try again.' });
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    if (newPassword.length < 8) {
      setErrors({ password: 'Password must be at least 8 characters' });
      return;
    }

    if (newPassword !== confirmPassword) {
      setErrors({ confirm: 'Passwords do not match' });
      return;
    }

    setLoading(true);

    try {
      const response = await fetch('/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: resetToken,
          password: newPassword,
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        setErrors({ submit: data.error });
        return;
      }

      // 重置成功，可以跳转到登录或自动登录
      alert('Password reset successful!');
      onBack?.();
    } catch (error) {
      setErrors({ submit: 'Failed to reset password. Please try again.' });
    } finally {
      setLoading(false);
    }
  };

  const handleResendEmail = async () => {
    setLoading(true);
    await new Promise(r => setTimeout(r, 1500)); // 模拟
    setLoading(false);
    alert('Reset email resent!');
  };

  // Step 1: Enter Email
  if (step === 'email') {
    return (
      <div className="forgot-password-container">
        <form onSubmit={handleSendResetLink} className="forgot-password-form">
          <div className="header">
            <div className="icon">🔑</div>
            <h1>Forgot Password?</h1>
            <p>No worries! Enter your email and we'll send you reset instructions.</p>
          </div>

          <div className="form-group">
            <label htmlFor="email">Email Address</label>
            <input
              id="email"
              type="email"
              placeholder="Enter your registered email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              autoComplete="email"
            />
            {errors.email && <span className="error-message">{errors.email}</span>}
          </div>

          {errors.submit && <div className="error-banner">{errors.submit}</div>}

          <button type="submit" className="submit-button" disabled={loading}>
            {loading ? (
              <>
                <span className="spinner"></span>
                Sending...
              </>
            ) : (
              'Send Reset Link'
            )}
          </button>

          {onBack && (
            <div className="back-link">
              <button type="button" onClick={onBack}>← Back to Sign In</button>
            </div>
          )}
        </form>
      </div>
    );
  }

  // Step 2: Email Sent
  if (step === 'sent') {
    return (
      <div className="forgot-password-container">
        <div className="forgot-password-form success-state">
          <div className="icon">📧</div>
          <h1>Check Your Email</h1>
          <p>We've sent password reset instructions to:</p>
          <div className="email-display">{email}</div>

          <div className="info-box">
            <strong>Didn't receive the email?</strong>
            <ul>
              <li>Check your spam folder</li>
              <li>Make sure you entered the correct email</li>
              <li>Wait a few minutes and try again</li>
            </ul>
          </div>

          <button className="submit-button" onClick={onBack}>Done</button>
          <button className="resend-button" onClick={handleResendEmail} disabled={loading}>
            {loading ? 'Sending...' : 'Resend Email'}
          </button>

          <div className="back-link">
            <button type="button" onClick={() => setStep('email')}>← Try a different email</button>
          </div>
        </div>
      </div>
    );
  }

  return null;
}
