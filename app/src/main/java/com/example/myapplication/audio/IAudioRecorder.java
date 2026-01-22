package com.example.myapplication.audio;

import java.io.File;

/**
 * 音频录音接口
 * 负责处理录音相关逻辑，提供开始/停止录音接口
 */
public interface IAudioRecorder {
    
    /**
     * 录音回调接口
     */
    interface OnRecordListener {
        /**
         * 录音中
         */
        void onRecording();
        
        /**
         * 录音完成（WAV文件生成）
         * @param wavFile WAV文件
         */
        void onRecordComplete(File wavFile);
        
        /**
         * 录音失败
         * @param errorMsg 错误信息
         */
        void onRecordFailed(String errorMsg);
    }
    
    /**
     * 设置录音回调
     * @param listener 回调接口
     */
    void setOnRecordListener(OnRecordListener listener);
    
    /**
     * 开始录音
     * @return 是否成功开始
     */
    boolean startRecording();
    
    /**
     * 停止录音
     */
    void stopRecording();
    
    /**
     * 是否正在录音
     * @return 是否正在录音
     */
    boolean isRecording();
    
    /**
     * 清理录音文件（可选调用）
     */
    void cleanRecordFiles();
}
