import React, { useState, useEffect } from 'react';
import './Dashboard.css';

interface User {
  id: string;
  email: string;
  username: string;
  name?: string;
}

interface DashboardProps {
  user: User;
  onLogout?: () => void;
}

interface Stat {
  label: string;
  value: string | number;
  change: string;
  isUp: boolean;
}

interface Activity {
  id: string;
  icon: string;
  iconClass: string;
  title: string;
  desc: string;
  time: string;
}

export function Dashboard({ user, onLogout }: DashboardProps) {
  const [showLogoutModal, setShowLogoutModal] = useState(false);

  const stats: Stat[] = [
    { label: 'Total Projects', value: '12', change: '↑ 2 new this week', isUp: true },
    { label: 'Tasks Completed', value: '847', change: '↑ 23 today', isUp: true },
    { label: 'Team Members', value: '8', change: '↑ 1 this month', isUp: true },
    { label: 'Hours Tracked', value: '156', change: '↓ 12 from last week', isUp: false },
  ];

  const activities: Activity[] = [
    { id: '1', icon: '📝', iconClass: 'blue', title: 'Created new project "E-commerce Platform"', desc: 'Initial setup with React and Node.js', time: '2 min ago' },
    { id: '2', icon: '✅', iconClass: 'green', title: 'Completed task "User Authentication"', desc: 'Implemented login, register, and password reset', time: '1 hour ago' },
    { id: '3', icon: '🐛', iconClass: 'yellow', title: 'Fixed bug in payment module', desc: 'Resolved issue with Stripe webhook', time: '3 hours ago' },
    { id: '4', icon: '👤', iconClass: 'purple', title: 'New team member joined', desc: 'Welcome Sarah as Frontend Developer', time: 'Yesterday' },
  ];

  const handleLogout = () => {
    setShowLogoutModal(false);
    onLogout?.();
  };

  const initial = (user.name || user.username).charAt(0).toUpperCase();

  return (
    <div className="dashboard">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="logo">🚀 OpenClaw</div>
        <ul className="nav-menu">
          <li><a href="#" className="active"><span className="icon">📊</span> Dashboard</a></li>
          <li><a href="#"><span className="icon">📁</span> Projects</a></li>
          <li><a href="#"><span className="icon">👥</span> Team</a></li>
          <li><a href="#"><span className="icon">📅</span> Calendar</a></li>
          <li><a href="#"><span className="icon">⚙️</span> Settings</a></li>
        </ul>
        <div className="user-mini">
          <div className="avatar">{initial}</div>
          <div className="user-info">
            <div className="name">{user.name || user.username}</div>
            <div className="email">{user.email}</div>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main">
        <div className="header">
          <h1>Welcome back, {(user.name || user.username).split(' ')[0]}! 👋</h1>
          <div className="header-actions">
            <button className="btn btn-outline" onClick={() => alert('Notifications')}>🔔 Notifications</button>
            <button className="btn btn-primary" onClick={() => alert('Create new project')}>+ New Project</button>
            <button className="btn btn-outline" onClick={() => setShowLogoutModal(true)}>🚪 Logout</button>
          </div>
        </div>

        {/* Stats */}
        <div className="stats-grid">
          {stats.map((stat, i) => (
            <div key={i} className="stat-card">
              <div className="label">{stat.label}</div>
              <div className="value">{stat.value}</div>
              <div className={`change ${stat.isUp ? 'up' : 'down'}`}>{stat.change}</div>
            </div>
          ))}
        </div>

        {/* Recent Activity */}
        <div className="card">
          <div className="card-header">
            <h2>Recent Activity</h2>
            <button className="btn btn-outline" onClick={() => alert('View all')}>View All</button>
          </div>
          <div className="card-body">
            <ul className="activity-list">
              {activities.map((activity) => (
                <li key={activity.id} className="activity-item">
                  <div className={`activity-icon ${activity.iconClass}`}>{activity.icon}</div>
                  <div className="activity-content">
                    <div className="title">{activity.title}</div>
                    <div className="desc">{activity.desc}</div>
                  </div>
                  <div className="activity-time">{activity.time}</div>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Profile Preview */}
        <div className="card">
          <div className="card-header">
            <h2>Your Profile</h2>
            <button className="btn btn-outline" onClick={() => alert('Edit profile')}>Edit</button>
          </div>
          <div className="card-body">
            <div className="profile-grid">
              <div className="profile-avatar">{initial}</div>
              <div className="profile-details">
                <div className="detail-group">
                  <label>Username</label>
                  <div className="value">{user.username}</div>
                </div>
                <div className="detail-group">
                  <label>Email</label>
                  <div className="value">{user.email}</div>
                </div>
                <div className="detail-group">
                  <label>Member Since</label>
                  <div className="value">March 2026</div>
                </div>
                <div className="detail-group">
                  <label>Account Type</label>
                  <div className="value">🌟 Pro Plan</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Logout Modal */}
      {showLogoutModal && (
        <div className="modal-overlay active" onClick={() => setShowLogoutModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>Logout?</h2>
            <p>Are you sure you want to logout from your account?</p>
            <div className="modal-buttons">
              <button className="btn btn-secondary" onClick={() => setShowLogoutModal(false)}>Cancel</button>
              <button className="btn btn-danger" onClick={handleLogout}>Logout</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
