package com.example.myapplication.audio;

/**
 * 流式音频播放器接口
 * 支持边接收音频数据边播放，实现低延迟的音频响应
 */
public interface IStreamingAudioPlayer {
    
    /**
     * 流式音频播放回调接口
     */
    interface OnStreamingAudioListener {
        /**
         * 播放开始
         */
        void onPlayStart();
        
        /**
         * 播放完成
         */
        void onPlayComplete();
        
        /**
         * 播放错误
         * @param error 错误信息
         */
        void onPlayError(String error);
        
        /**
         * 接收到音频块
         * @param chunkSize 音频块大小
         */
        void onChunkReceived(int chunkSize);
    }
    
    /**
     * 设置播放回调
     * @param listener 回调接口
     */
    void setOnStreamingAudioListener(OnStreamingAudioListener listener);
    
    /**
     * 开始播放（准备接收音频数据）
     */
    void start();
    
    /**
     * 添加音频数据块（Base64编码）
     * @param base64Chunk Base64编码的音频数据块
     */
    void addAudioChunk(String base64Chunk);
    
    /**
     * 停止播放
     */
    void stop();
    
    /**
     * 是否正在播放
     * @return 是否正在播放
     */
    boolean isPlaying();
}
