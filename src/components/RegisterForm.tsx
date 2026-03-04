import React, { useState } from 'react';
import { validateEmail, validateUsername, getPasswordStrength } from '../utils/validation';
import './RegisterForm.css';

interface RegisterFormProps {
  onSuccess?: (user: User) => void;
  onLoginClick?: () => void;
}

interface User {
  id: string;
  email: string;
  username: string;
}

interface PasswordRequirements {
  length: boolean;
  uppercase: boolean;
  lowercase: boolean;
  number: boolean;
}

export function RegisterForm({ onSuccess, onLoginClick }: RegisterFormProps) {
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    agreeTerms: false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [passwordStrength, setPasswordStrength] = useState(0);

  const getPasswordReqs = (password: string): PasswordRequirements => ({
    length: password.length >= 8,
    uppercase: /[A-Z]/.test(password),
    lowercase: /[a-z]/.test(password),
    number: /[0-9]/.test(password),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    // 验证
    const usernameErr = validateUsername(formData.username);
    if (usernameErr) { setErrors(prev => ({ ...prev, username: usernameErr })); return; }

    const emailErr = validateEmail(formData.email);
    if (emailErr) { setErrors(prev => ({ ...prev, email: emailErr })); return; }

    const strength = getPasswordStrength(formData.password);
    if (strength < 4) { setErrors(prev => ({ ...prev, password: 'Please create a stronger password' })); return; }

    if (formData.password !== formData.confirmPassword) {
      setErrors(prev => ({ ...prev, confirmPassword: 'Passwords do not match' })); return;
    }

    if (!formData.agreeTerms) {
      setErrors(prev => ({ ...prev, terms: 'You must agree to the terms' })); return;
    }

    setLoading(true);

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: formData.username,
          email: formData.email,
          password: formData.password,
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        setErrors(prev => ({ ...prev, submit: data.error }));
        return;
      }

      onSuccess?.(data.user);
    } catch (error) {
      setErrors(prev => ({ ...prev, submit: 'Registration failed. Please try again.' }));
    } finally {
      setLoading(false);
    }
  };

  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const password = e.target.value;
    setFormData(prev => ({ ...prev, password }));
    setPasswordStrength(getPasswordStrength(password));
  };

  const reqs = getPasswordReqs(formData.password);

  return (
    <div className="register-container">
      <form onSubmit={handleSubmit} className="register-form">
        <div className="register-header">
          <h1>Create Account</h1>
          <p>Join us today and get started</p>
        </div>

        <div className="form-group">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            type="text"
            placeholder="Choose a username"
            value={formData.username}
            onChange={(e) => setFormData(prev => ({ ...prev, username: e.target.value }))}
            disabled={loading}
            autoComplete="username"
          />
          {errors.username && <span className="error-message">{errors.username}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="email">Email Address</label>
          <input
            id="email"
            type="email"
            placeholder="you@example.com"
            value={formData.email}
            onChange={(e) => setFormData(prev => ({ ...prev, email: e.target.value }))}
            disabled={loading}
            autoComplete="email"
          />
          {errors.email && <span className="error-message">{errors.email}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            placeholder="Create a strong password"
            value={formData.password}
            onChange={handlePasswordChange}
            disabled={loading}
            autoComplete="new-password"
          />
          <div className="password-strength">
            <div className={`password-strength-bar strength-${passwordStrength}`}></div>
          </div>
          <div className="password-requirements">
            <ul>
              <li className={reqs.length ? 'met' : 'unmet'}>At least 8 characters</li>
              <li className={reqs.uppercase ? 'met' : 'unmet'}>One uppercase letter</li>
              <li className={reqs.lowercase ? 'met' : 'unmet'}>One lowercase letter</li>
              <li className={reqs.number ? 'met' : 'unmet'}>One number</li>
            </ul>
          </div>
          {errors.password && <span className="error-message">{errors.password}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="confirmPassword">Confirm Password</label>
          <input
            id="confirmPassword"
            type="password"
            placeholder="Confirm your password"
            value={formData.confirmPassword}
            onChange={(e) => setFormData(prev => ({ ...prev, confirmPassword: e.target.value }))}
            disabled={loading}
            autoComplete="new-password"
          />
          {errors.confirmPassword && <span className="error-message">{errors.confirmPassword}</span>}
        </div>

        <div className="form-group">
          <label className="terms-checkbox">
            <input
              type="checkbox"
              checked={formData.agreeTerms}
              onChange={(e) => setFormData(prev => ({ ...prev, agreeTerms: e.target.checked }))}
              disabled={loading}
            />
            <span>I agree to the <a href="#">Terms of Service</a> and <a href="#">Privacy Policy</a></span>
          </label>
          {errors.terms && <span className="error-message">{errors.terms}</span>}
        </div>

        {errors.submit && <div className="error-banner">{errors.submit}</div>}

        <button type="submit" className="submit-button" disabled={loading}>
          {loading ? (
            <>
              <span className="spinner"></span>
              Creating Account...
            </>
          ) : (
            'Create Account'
          )}
        </button>

        <p className="login-link">
          Already have an account?{' '}
          <button type="button" onClick={onLoginClick}>
            Sign in
          </button>
        </p>
      </form>
    </div>
  );
}
