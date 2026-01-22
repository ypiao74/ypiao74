package com.example.myapplication.audio;

import java.io.File;

/**
 * 音频播放器接口
 * 支持Base64解码、WAV文件生成、音频播放，适配车载场景
 */
public interface IAudioPlayer {
    
    /**
     * 播放回调接口
     */
    interface OnAudioPlayListener {
        /**
         * 播放开始
         */
        void onAudioPlayStart();
        
        /**
         * 播放完成
         */
        void onAudioPlayComplete();
        
        /**
         * 播放错误
         * @param error 错误信息
         */
        void onAudioPlayError(String error);
        
        /**
         * 音频已保存
         * @param audioFile 音频文件
         */
        void onAudioSaved(File audioFile);
    }
    
    /**
     * 设置播放回调
     * @param listener 回调接口
     */
    void setOnAudioPlayListener(OnAudioPlayListener listener);
    
    /**
     * 查询是否正在播放
     * @return 是否正在播放
     */
    boolean isPlaying();
    
    /**
     * 播放音频（Base64编码）
     * @param base64Data Base64编码的音频数据
     */
    void playAudioFromBase64(String base64Data);
    
    /**
     * 停止播放
     */
    void stop();
}
