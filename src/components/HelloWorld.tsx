/**
 * Hello World 组件 - 由 Qwen-Coder 智能体生成
 * 
 * 这是一个测试组件，展示智能体如何工作
 */

import React, { useState } from 'react';
import './HelloWorld.css';

interface HelloWorldProps {
  name?: string;
  onGreet?: (name: string) => void;
}

export function HelloWorld({ name = 'World', onGreet }: HelloWorldProps) {
  const [clickCount, setClickCount] = useState(0);
  const [isAnimating, setIsAnimating] = useState(false);

  const handleClick = () => {
    setIsAnimating(true);
    setClickCount(prev => prev + 1);
    onGreet?.(name);
    
    setTimeout(() => setIsAnimating(false), 300);
  };

  return (
    <div className="hello-world-container">
      <div className={`hello-card ${isAnimating ? 'animating' : ''}`}>
        <h1 className="hello-title">
          👋 Hello, {name}!
        </h1>
        <p className="hello-subtitle">
          Welcome to OpenClaw Agent Cluster
        </p>
        
        <button 
          className="hello-button"
          onClick={handleClick}
          disabled={isAnimating}
        >
          {clickCount === 0 ? 'Say Hello!' : `Hello! (${clickCount})`}
        </button>
        
        <div className="hello-stats">
          <span>🎉 Button clicked {clickCount} times</span>
        </div>
      </div>
    </div>
  );
}
