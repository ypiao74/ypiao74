package com.example.myapplication.config;

/**
 * 应用常量类
 */
public final class AppConstants {
    private AppConstants() {
        // 防止实例化
    }

    // 权限请求码
    public static final int REQUEST_RECORD_AUDIO = 101;
    public static final int REQUEST_OVERLAY_PERMISSION = 100;
    public static final int REQUEST_PICK_IMAGE = 102;

    // API配置（已迁移到 AppConfig，保留此常量仅用于向后兼容）
    @Deprecated
    public static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    @Deprecated
    public static final String MODEL_NAME = "qwen3-omni-mobile"; // 根据API文档，必须使用此模型名称
    public static final boolean STREAM_ENABLED = true;
    public static final String TOOL_CHOICE_AUTO = "auto";

    // 音频配置
    public static final int AUDIO_SAMPLE_RATE = 16000;
    public static final int AUDIO_PLAYBACK_SAMPLE_RATE = 24000;
    public static final int AUDIO_CHANNELS = 1;
    public static final int AUDIO_BIT_DEPTH = 16;
    public static final int MIN_AUDIO_BYTES = 1024;

    // 网络配置
    public static final int NETWORK_TIMEOUT_SECONDS = 60;

    // 文件配置
    public static final String CACHE_DIR_NAME = "car_audio_cache";
    public static final String PCM_FILE_NAME = "recorded.pcm";
    public static final String WAV_FILE_NAME = "recorded.wav";

    // UI消息
    public static final String MSG_RECORDING = "录音中...（点击「停止录音」结束）";
    public static final String MSG_PROCESSING = "WAV文件生成完成，正在请求Qwen-Omni API...";
    public static final String MSG_RECORD_STOPPED = "⏹️ 录音已停止，正在处理...";
    public static final String MSG_RECORD_FAILED = "录音失败: %s";
    public static final String MSG_API_FAILED = "API请求失败: 错误码=%d, %s";
    public static final String MSG_API_ERROR = "API调用异常: %s";
    public static final String MSG_API_RATE_LIMITED = "⚠️ API请求被限速（429），请稍后再试。%s";
    public static final String MSG_RETRY_AFTER = "建议等待 %d 秒后重试";
    public static final String MSG_AUDIO_PLAY_START = "🎵 语音响应开始播放...";
    public static final String MSG_AUDIO_PLAY_COMPLETE = "🎵 语音响应播放完成";
    public static final String MSG_AUDIO_PLAY_ERROR = "❌ 音频播放错误: %s";
    public static final String MSG_TOOL_CALL_COMPLETE = "工具调用拼接完成: %s";
    public static final String MSG_TOOL_EXECUTE_COMPLETE = "工具执行完成，发起第二次模型调用...";
    public static final String MSG_NO_AUDIO_RESPONSE = "无语音反馈。";
    public static final String MSG_AUDIO_INVALID = "❌ 音频数据无效（长度过短）";
    public static final String MSG_AUDIO_DECODE_FAILED = "❌ 音频解码失败: %s";
    public static final String MSG_IMAGE_PROCESSING = "正在处理图片...";
    public static final String MSG_IMAGE_PICK_FAILED = "❌ 图片选择失败: %s";
    public static final String MSG_IMAGE_ENCODE_FAILED = "❌ 图片编码失败: %s";

    // 悬浮窗配置
    public static final int FLOAT_WINDOW_X = 100;
    public static final int FLOAT_WINDOW_Y = 300;
    public static final int DRAG_THRESHOLD = 10;
}


