package com.example.myapplication.presenter.contract;

/**
 * Qwen功能契约接口
 * 定义View和Presenter之间的交互协议
 */
public interface QwenContract {
    
    /**
     * View接口
     * 定义UI更新方法
     */
    interface View {
        /**
         * 设置结果文本
         */
        void setResult(String text);
        
        /**
         * 追加结果文本
         */
        void appendResult(String text);
        
        /**
         * 重置按钮状态
         */
        void resetButtonState();
        
        /**
         * 启用开始按钮
         */
        void enableStartButton(boolean enabled);
        
        /**
         * 启用停止按钮
         */
        void enableStopButton(boolean enabled);

        String getResult();
    }
    
    /**
     * Presenter接口
     * 定义业务逻辑方法
     */
    interface Presenter {
        /**
         * 开始录音
         */
        void startRecording();
        
        /**
         * 停止录音
         */
        void stopRecording();
        
        /**
         * 设置工具启用状态
         */
        void setToolEnabled(boolean enabled);
        
        /**
         * 发送文本请求
         * @param text 文本内容
         */
        void sendTextRequest(String text);
        
        /**
         * 发送图片请求（单张图片）
         * @param imageFile 图片文件
         * @param textContent 可选的文本内容
         */
        void sendImageRequest(java.io.File imageFile, String textContent);
        
        /**
         * 发送多图片请求
         * @param imageFiles 图片文件列表
         * @param textContent 可选的文本内容
         */
        void sendMultipleImageRequest(java.util.List<java.io.File> imageFiles, String textContent);
        
        /**
         * 取消当前请求并停止音频播放
         * 用于打断正在进行的请求和音频播放
         */
        void cancelCurrentRequest();
        
        /**
         * 释放资源
         */
        void release();
    }
}
