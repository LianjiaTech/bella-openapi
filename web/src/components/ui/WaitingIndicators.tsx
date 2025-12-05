import React from 'react';

/**
 * 等待语音输入的动画组件
 */
export const WaitingForVoiceInput = () => {
  return (
    <div className="flex items-center text-gray-500">
      <div className="flex space-x-1">
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className="w-1 bg-blue-400 rounded-full animate-pulse"
            style={{
              animationDelay: `${i * 0.15}s`,
              height: `${Math.max(4, (i % 3 + 1) * 4)}px`
            }}
          ></div>
        ))}
      </div>
    </div>
  );
};

/**
 * 等待AI回复的动画组件
 */
export const WaitingForAIResponse = () => {
  return (
    <div className="inline-flex items-center text-gray-500 ml-1">
      <div className="animate-spin rounded-full h-3 w-3 border-b-2 border-blue-500"></div>
    </div>
  );
};
