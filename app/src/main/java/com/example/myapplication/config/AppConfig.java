package com.example.myapplication.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 应用配置管理类
 * 统一管理所有配置信息（API密钥、URL、模型名称等）
 */
public class AppConfig {
    private static final String PREF_NAME = "app_config";
    
    // SharedPreferences 键名
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_TIMEOUT_SECONDS = "timeout_seconds";
    private static final String KEY_USE_OMNI_CONFIG = "use_omni_config";
    
    // 默认配置值
    private static final String DEFAULT_API_KEY = "H4I5J6K7L8M9N0O1P2Q3R4S5T6U7V8W9X0Y1Z2a3b4c5d6e7f8g9h0i1j2k4m5==";
    private static final String DEFAULT_API_URL = "http://8.130.209.214:8901/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL_NAME = "qwen3-omni-mobile";
    private static final String OMNI_API_KEY = "sk-920faaf7d06546429b226c3b6f71530b";
    private static final String OMNI_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String Omni_MODEL_NAME = "qwen3-omni-flash";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final SharedPreferences preferences;

    public AppConfig(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ==================== API Key ====================
    
    /**
     * 获取API密钥（根据当前配置模式）
     */
    public String getApiKey() {
        String savedKey = preferences.getString(KEY_API_KEY, null);
        if (savedKey != null) {
            return savedKey; // 如果用户手动设置过，使用手动设置的值
        }
        // 否则根据配置模式返回
        return isUseOmniConfig() ? OMNI_API_KEY : DEFAULT_API_KEY;
    }

    /**
     * 设置API密钥
     */
    public void setApiKey(String apiKey) {
        preferences.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    // ==================== API URL ====================
    
    /**
     * 获取API URL（根据当前配置模式）
     */
    public String getApiUrl() {
        String savedUrl = preferences.getString(KEY_API_URL, null);
        if (savedUrl != null) {
            return savedUrl;
        }
        // 步骤1：提取开关状态到变量
        boolean useOmni = isUseOmniConfig();
        // 步骤2：提取分支结果到变量（关键！）
        String branchUrl = useOmni ? OMNI_API_URL : DEFAULT_API_URL;

        // 步骤3：打印所有关键信息（一目了然）
        Log.i("AppConfig", "===== 关键调试 =====");
        Log.i("AppConfig", "useOmni = " + useOmni);
        Log.i("AppConfig", "OMNI_API_URL = " + OMNI_API_URL);
        Log.i("AppConfig", "DEFAULT_API_URL = " + DEFAULT_API_URL);
        Log.i("AppConfig", "branchUrl（分支结果） = " + branchUrl);
        Log.i("AppConfig", "====================");

        // 步骤4：返回分支结果
        return branchUrl;
    }

    /**
     * 设置API URL
     */
    public void setApiUrl(String apiUrl) {
        preferences.edit().putString(KEY_API_URL, apiUrl).apply();
    }

    // ==================== Model Name ====================
    
    /**
     * 获取模型名称（根据当前配置模式）
     */
    public String getModelName() {
        String savedModel = preferences.getString(KEY_MODEL_NAME, null);
        if (savedModel != null) {
            return savedModel; // 如果用户手动设置过，使用手动设置的值
        }
        // 否则根据配置模式返回
        return isUseOmniConfig() ? Omni_MODEL_NAME : DEFAULT_MODEL_NAME;
    }

    /**
     * 设置模型名称
     */
    public void setModelName(String modelName) {
        preferences.edit().putString(KEY_MODEL_NAME, modelName).apply();
    }

    // ==================== Timeout ====================
    
    /**
     * 获取网络超时时间（秒）
     */
    public int getTimeoutSeconds() {
        return preferences.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 设置网络超时时间（秒）
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        preferences.edit().putInt(KEY_TIMEOUT_SECONDS, timeoutSeconds).apply();
    }

    // ==================== 配置模式切换 ====================
    
    /**
     * 是否使用 Omni 配置
     */
    public boolean isUseOmniConfig() {
        return preferences.getBoolean(KEY_USE_OMNI_CONFIG, false);
    }

    /**
     * 设置是否使用 Omni 配置
     * @param useOmniConfig true=使用Omni配置，false=使用默认配置
     */
    public void setUseOmniConfig(boolean useOmniConfig) {
        // 使用 commit() 确保配置立即写入，避免异步延迟导致读取旧值
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_USE_OMNI_CONFIG, useOmniConfig);
        // 清除手动设置的配置，让系统使用模式对应的默认值
        editor.remove(KEY_API_KEY);
        editor.remove(KEY_API_URL);
        editor.remove(KEY_MODEL_NAME);
        editor.commit(); // 同步提交，确保立即生效
    }

    // ==================== 其他方法 ====================
    
    /**
     * 清除所有配置
     */
    public void clear() {
        preferences.edit().clear().apply();
    }

    /**
     * 重置为默认配置
     */
    public void resetToDefaults() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_API_KEY);
        editor.remove(KEY_API_URL);
        editor.remove(KEY_MODEL_NAME);
        editor.remove(KEY_TIMEOUT_SECONDS);
        editor.remove(KEY_USE_OMNI_CONFIG);
        editor.apply();
    }
}


