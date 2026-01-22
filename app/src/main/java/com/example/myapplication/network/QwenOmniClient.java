package com.example.myapplication.network;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.config.AppConfig;
import com.example.myapplication.network.model.QwenRequest;
import com.example.myapplication.util.Base64Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QwenOmniClient {
    private static final String TAG = "QwenOmniClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AppConfig appConfig;
    // 不再缓存 apiKey 和 apiUrl，每次都从 appConfig 读取，确保配置切换时能立即生效
    private final OkHttpClient okHttpClient;
    private OnApiCallback callback;
    private JSONArray messages;

    private final Context context;
    private boolean isToolEnabled = true;
    private String audioVoice = "Cherry"; // 默认发音人
    
    // HTTP连接管理器
    private final ConnectionManager connectionManager;
    
    // 是否检测到工具调用（用于跳过后续音频处理）
    private volatile boolean hasToolCallDetected = false;

    public void setToolEnabled(boolean toolEnabled) {
        isToolEnabled = toolEnabled;
        Log.d(TAG, "工具调用已" + (toolEnabled ? "启用" : "禁用"));
    }
    
    /**
     * 设置音频发音人
     * @param voice 发音人名称（如 "Jennifer", "Ryan", "Cherry" 等）
     */
    public void setAudioVoice(String voice) {
        if (voice != null && !voice.trim().isEmpty()) {
            this.audioVoice = voice.trim();
            Log.d(TAG, "已设置发音人: " + audioVoice);
        }
    }
    
    /**
     * 获取当前音频发音人
     * @return 发音人名称
     */
    public String getAudioVoice() {
        return audioVoice;
    }
    
    /**
     * 检查是否检测到工具调用
     */
    public boolean hasToolCallDetected() {
        return hasToolCallDetected;
    }
    
    /**
     * 重置工具调用检测标志
     */
    public void resetToolCallDetected() {
        hasToolCallDetected = false;
    }

    private static class ToolCallCache {
        private java.util.Map<Integer, SingleToolCall> toolCalls = new java.util.HashMap<>();
        private java.util.Set<String> triggeredCallIds = new java.util.HashSet<>();
        
        public void reset() {
            toolCalls.clear();
            triggeredCallIds.clear();
        }

        public void appendToolCall(JSONObject toolCallJson) {
            try {
                // 获取工具调用的 index（用于并行工具调用）
                int index = toolCallJson.optInt("index", 0);
                
                // 获取或创建对应 index 的工具调用缓存
                SingleToolCall singleCall = toolCalls.get(index);
                if (singleCall == null) {
                    singleCall = new SingleToolCall();
                    toolCalls.put(index, singleCall);
                }
                
                // 更新工具调用信息
                if (toolCallJson.has("id") && singleCall.toolId == null) {
                    singleCall.toolId = toolCallJson.getString("id");
                }

                JSONObject functionJson = toolCallJson.optJSONObject("function");
                if (functionJson != null) {
                    if (functionJson.has("name") && singleCall.functionName == null) {
                        singleCall.functionName = functionJson.getString("name");
                    }
                    if (functionJson.has("arguments")) {
                        String args = functionJson.getString("arguments").trim();
                        if (!args.isEmpty() && !"null".equals(args)) {
                            singleCall.argumentsBuilder.append(args);
                        }
                    }
                }

                // 检查是否完成
                String argsStr = singleCall.argumentsBuilder.toString();
                if (singleCall.functionName != null && !argsStr.isEmpty() && isJsonValid(argsStr)) {
                    singleCall.isCompleted = true;
                }
            } catch (JSONException e) {
                Log.e(TAG, "拼接工具调用失败", e);
            }
        }

        private boolean isJsonValid(String json) {
            try {
                new JSONObject(json);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }

        /**
         * 获取所有新完成的工具调用（未触发过回调的）
         * @return 新完成的工具调用列表
         */
        public java.util.List<CompleteToolCall> getNewCompleteToolCalls() {
            java.util.List<CompleteToolCall> newCompleteCalls = new java.util.ArrayList<>();
            for (SingleToolCall singleCall : toolCalls.values()) {
                if (singleCall.isCompleted && singleCall.toolId != null 
                    && !triggeredCallIds.contains(singleCall.toolId)) {
                    try {
                        JSONObject argsJson = new JSONObject(singleCall.argumentsBuilder.toString());
                        CompleteToolCall completeCall = new CompleteToolCall(
                            singleCall.toolId, singleCall.functionName, argsJson);
                        newCompleteCalls.add(completeCall);
                        // 标记为已触发
                        triggeredCallIds.add(singleCall.toolId);
                    } catch (JSONException e) {
                        Log.e(TAG, "解析工具调用参数失败", e);
                    }
                }
            }
            return newCompleteCalls;
        }
        
        /**
         * 获取所有已完成的工具调用（不管是否已触发回调）
         * @return 所有已完成的工具调用列表
         */
        public java.util.List<CompleteToolCall> getAllCompleteToolCalls() {
            java.util.List<CompleteToolCall> allCompleteCalls = new java.util.ArrayList<>();
            for (SingleToolCall singleCall : toolCalls.values()) {
                if (singleCall.isCompleted && singleCall.toolId != null) {
                    try {
                        JSONObject argsJson = new JSONObject(singleCall.argumentsBuilder.toString());
                        CompleteToolCall completeCall = new CompleteToolCall(
                            singleCall.toolId, singleCall.functionName, argsJson);
                        allCompleteCalls.add(completeCall);
                    } catch (JSONException e) {
                        Log.e(TAG, "解析工具调用参数失败", e);
                    }
                }
            }
            return allCompleteCalls;
        }
        
        /**
         * 检查是否有已完成的工具调用
         */
        public boolean hasCompleteToolCalls() {
            for (SingleToolCall singleCall : toolCalls.values()) {
                if (singleCall.isCompleted && singleCall.toolId != null) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * 检查所有工具调用是否都已完成（用于并行工具调用）
         * 如果检测到有工具调用，但还没有全部完成，返回 false
         * 如果所有工具调用都完成了，返回 true
         */
        public boolean areAllToolCallsComplete() {
            if (toolCalls.isEmpty()) {
                return false; // 没有工具调用
            }
            
            // 检查所有工具调用是否都已完成
            for (SingleToolCall singleCall : toolCalls.values()) {
                if (!singleCall.isCompleted || singleCall.toolId == null) {
                    return false; // 还有未完成的工具调用
                }
            }
            
            return true; // 所有工具调用都已完成
        }
        
        /**
         * 检查是否包含指定 index 的工具调用
         */
        public boolean hasToolCall(int index) {
            return toolCalls.containsKey(index);
        }
        
        /**
         * 获取当前工具调用的数量
         */
        public int getToolCallCount() {
            return toolCalls.size();
        }
        
        /**
         * 单个工具调用的缓存
         */
        private static class SingleToolCall {
            String toolId;
            String functionName;
            StringBuilder argumentsBuilder = new StringBuilder();
            boolean isCompleted = false;
        }
    }

    public static class CompleteToolCall {
        public String toolId;
        public String functionName;
        public JSONObject arguments;

        public CompleteToolCall(String toolId, String functionName, JSONObject arguments) {
            this.toolId = toolId;
            this.functionName = functionName;
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return "CompleteToolCall{" +
                    "toolId='" + toolId + '\'' +
                    ", functionName='" + functionName + '\'' +
                    ", arguments=" + arguments.toString() +
                    '}';
        }
    }

    public interface OnApiCallback {
        void onSuccess(String response);
        void onToolCallComplete(CompleteToolCall completeToolCall);
        void onSummaryComplete(String summary);
        void onFailure(int errorCode, String errorMsg);
        void onError(Throwable throwable);
    }

    /**
     * 构造函数（推荐使用）
     * @param context 上下文
     * @param appConfig 应用配置
     */
    public QwenOmniClient(Context context, AppConfig appConfig) {
        this.context = context;
        this.appConfig = appConfig;
        // 不再缓存 apiKey 和 apiUrl，每次都从 appConfig 读取，确保配置切换时能立即生效
        int timeout = appConfig.getTimeoutSeconds();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
        this.connectionManager = new ConnectionManager();
    }

    /**
     * 构造函数（兼容旧代码，使用默认配置）
     * @param context 上下文
     * @param apiKey API密钥
     * @deprecated 推荐使用 QwenOmniClient(Context, AppConfig)
     */
    @Deprecated
    public QwenOmniClient(Context context, String apiKey) {
        this.context = context;
        this.appConfig = new AppConfig(context);
        // 注意：此构造函数传入的 apiKey 会被忽略，使用 appConfig 中的值
        // 不再缓存 apiKey 和 apiUrl，每次都从 appConfig 读取
        int timeout = appConfig.getTimeoutSeconds();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
        this.connectionManager = new ConnectionManager();
    }

    public void setOnApiCallback(OnApiCallback callback) {
        this.callback = callback;
    }

    public void setMessages(JSONArray messages) {
        this.messages = messages;
        Log.d(TAG, "已设置上下文messages，长度=" + (messages != null ? messages.length() : 0));
    }

    /**
     * 获取当前保存的消息历史
     * @return 消息历史数组，包含 System Message 和 User Message（第一次调用时保存）
     */
    public JSONArray getMessages() {
        return this.messages;
    }

    /**
     * 取消当前正在进行的请求
     */
    public void cancelCurrentRequest() {
        connectionManager.cancelCurrentRequest();
    }

    // 解析域名获取DNS耗时
    private long getDnsResolveTime(String url) {
        long startTime = System.currentTimeMillis();
        try {
            // 使用 URL 类正确解析主机名，支持 http:// 和 https://
            java.net.URL urlObj = new java.net.URL(url);
            String host = urlObj.getHost();
            int port = urlObj.getPort();
            if (port == -1) {
                port = urlObj.getDefaultPort();
            }
            Log.d(TAG, "准备连接: " + host + ":" + port);
            List<InetAddress> addresses = okHttpClient.dns().lookup(host);
            if (!addresses.isEmpty()) {
                Log.d(TAG, "DNS解析成功: " + host + " -> " + addresses.get(0).getHostAddress());
            } else {
                Log.w(TAG, "DNS解析结果为空: " + host);
            }
        } catch (Exception e) {
            Log.e(TAG, "DNS解析失败: " + url, e);
        }
        return System.currentTimeMillis() - startTime;
    }

    public void callApi(File wavFile) {
        new Thread(() -> {
            long totalStartTime = System.currentTimeMillis();
            try {
                ToolCallCache toolCallCache = new ToolCallCache();
                toolCallCache.reset();
                QwenRequest requestModel = null;

                // 音频Base64编码计时
                long encodeStartTime = System.currentTimeMillis();
                String audioBase64 = null;
                if (wavFile != null) {
                    audioBase64 = Base64Util.fileToBase64(wavFile);
                    audioBase64 = audioBase64 == null ? "" : audioBase64;
                    long encodeEndTime = System.currentTimeMillis();
                    Log.d(TAG, "WAV Base64编码完成，长度: " + audioBase64.length() + "，耗时: " + (encodeEndTime - encodeStartTime) + "ms");
                }

                // 请求模型构建计时
                long requestBuildStartTime = System.currentTimeMillis();
                // 重置工具调用检测标志
                resetToolCallDetected();
                
                if (wavFile != null) {
                    // 第一次调用：构建包含用户输入的请求（使用 Builder 模式设置发音人）
                    requestModel = QwenRequest.builder(context)
                            .audio(audioBase64)
                            .summary(false)
                            .toolEnabled(isToolEnabled)
                            .audioVoice(audioVoice)
                            .build();
                     if (requestModel.getMessages() != null) {
                        try {
                            JSONArray originalMessages = requestModel.getMessages();
                            this.messages = new JSONArray();
                            for (int i = 0; i < originalMessages.length(); i++) {
                                JSONObject msg = originalMessages.getJSONObject(i);
                                // 深拷贝：使用 toString() 然后重新解析，确保嵌套结构也被完整复制
                                // 这样可以正确保留音频数据（在 content 数组中的 input_audio）
                                JSONObject msgCopy = new JSONObject(msg.toString());
                                this.messages.put(msgCopy);
                            }
                            Log.d(TAG, "第一次调用：已保存初始 messages（深拷贝），长度=" + this.messages.length());
                            // 验证音频数据是否被正确保存
                            if (this.messages.length() >= 2) {
                                JSONObject userMsg = this.messages.optJSONObject(1);
                                if (userMsg != null && "user".equals(userMsg.optString("role"))) {
                                    Object content = userMsg.opt("content");
                                    if (content instanceof JSONArray) {
                                        JSONArray contentArr = (JSONArray) content;
                                        boolean hasAudio = false;
                                        for (int j = 0; j < contentArr.length(); j++) {
                                            JSONObject item = contentArr.optJSONObject(j);
                                            if (item != null && "input_audio".equals(item.optString("type"))) {
                                                hasAudio = true;
                                                Log.d(TAG, "✓ 用户音频输入已正确保存到消息历史中");
                                                break;
                                            }
                                        }
                                        if (!hasAudio) {
                                            Log.w(TAG, "⚠ 警告：用户消息中未找到音频输入");
                                        }
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "保存初始 messages 失败", e);
                            this.messages = null;
                        }
                    }
                } else {
                    // 二次调用：使用已保存的完整消息历史
                    if (this.messages == null) {
                        Log.e(TAG, "二次调用失败：上下文messages为空");
                        if (callback != null) {
                            callback.onError(new RuntimeException("二次调用缺少上下文，请先设置messages"));
                        }
                        return;
                    }
                    // 二次调用时，this.messages 应该已经包含：System + User + Assistant + Tool
                    // 直接使用这些 messages，不需要重新构建
                    // 传入 isToolCallbackRequest=true，让 QwenRequest 将 tool_choice 设置为 "none"
                    requestModel = QwenRequest.builder(context)
                            .messages(this.messages)
                            .summary(true)
                            .toolEnabled(isToolEnabled)
                            .toolCallback(true)
                            .audioVoice(audioVoice)
                            .build();
                    Log.d(TAG, "二次调用：基于完整对话历史构建请求，messages长度=" + this.messages.length() + "，tool_choice=none");
                }
                long requestBuildEndTime = System.currentTimeMillis();
                Log.d(TAG, "请求模型构建耗时: " + (requestBuildEndTime - requestBuildStartTime) + "ms");

                if (requestModel == null) {
                    Log.e(TAG, "请求模型构建失败");
                    if (callback != null) {
                        callback.onError(new RuntimeException("请求模型构建失败"));
                    }
                    return;
                }

                // 执行请求
                executeRequest(requestModel, toolCallCache, totalStartTime);

            } catch (java.net.ConnectException e) {
                // 连接异常：提供详细的诊断信息
                String apiUrl = appConfig.getApiUrl();
                String errorMsg = "连接失败: " + apiUrl;
                if (e.getMessage() != null && e.getMessage().contains("ECONNREFUSED")) {
                    errorMsg += "\n原因: 连接被拒绝 (ECONNREFUSED)";
                    errorMsg += "\n可能的原因:";
                    errorMsg += "\n  1. 服务器未运行或已关闭";
                    errorMsg += "\n  2. 端口 8901 未开放或被防火墙阻止";
                    errorMsg += "\n  3. 服务器地址不正确";
                    errorMsg += "\n  4. 网络连接问题";
                    errorMsg += "\n\n建议检查:";
                    errorMsg += "\n  - 确认服务器 " + apiUrl.replace("http://", "").split("/")[0] + " 是否正在运行";
                    errorMsg += "\n  - 检查防火墙是否允许端口 8901";
                    errorMsg += "\n  - 尝试从其他设备或网络访问";
                } else {
                    errorMsg += "\n错误详情: " + e.getMessage();
                }
                Log.e(TAG, errorMsg, e);
                if (callback != null) {
                    callback.onError(new RuntimeException(errorMsg, e));
                }
                long totalEndTime = System.currentTimeMillis();
                Log.d(TAG, "API调用异常总耗时: " + (totalEndTime - totalStartTime) + "ms");
            } catch (Throwable e) {
                String errorMsg = "API调用异常: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                if (callback != null) {
                    callback.onError(e);
                }
                long totalEndTime = System.currentTimeMillis();
                Log.d(TAG, "API调用异常总耗时: " + (totalEndTime - totalStartTime) + "ms");
            }
        }).start();
    }

    /**
     * 调用API（图片请求专用）
     * 适用于单张或多张图片请求
     */
    public void callApiWithImage(QwenRequest requestModel) {
        if (requestModel == null) {
            if (callback != null) {
                callback.onError(new RuntimeException("请求模型不能为空"));
            }
            return;
        }

        new Thread(() -> {
            long totalStartTime = System.currentTimeMillis();
            try {
                ToolCallCache toolCallCache = new ToolCallCache();
                toolCallCache.reset();


                executeRequest(requestModel, toolCallCache, totalStartTime);

            } catch (java.net.ConnectException e) {
                String apiUrl = appConfig.getApiUrl();
                String errorMsg = "连接失败: " + apiUrl;
                if (e.getMessage() != null && e.getMessage().contains("ECONNREFUSED")) {
                    errorMsg += "\n原因: 连接被拒绝 (ECONNREFUSED)";
                    errorMsg += "\n可能的原因:";
                    errorMsg += "\n  1. 服务器未运行或已关闭";
                    errorMsg += "\n  2. 端口 8901 未开放或被防火墙阻止";
                    errorMsg += "\n  3. 服务器地址不正确";
                    errorMsg += "\n  4. 网络连接问题";
                } else {
                    errorMsg += "\n错误详情: " + e.getMessage();
                }
                Log.e(TAG, errorMsg, e);
                if (callback != null) {
                    callback.onError(new RuntimeException(errorMsg, e));
                }
                long totalEndTime = System.currentTimeMillis();
                Log.d(TAG, "API调用异常总耗时: " + (totalEndTime - totalStartTime) + "ms");
            } catch (Throwable e) {
                String errorMsg = "API调用异常: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                if (callback != null) {
                    callback.onError(e);
                }
                long totalEndTime = System.currentTimeMillis();
                Log.d(TAG, "API调用异常总耗时: " + (totalEndTime - totalStartTime) + "ms");
            }
        }).start();
    }

    /**
     * 执行API请求的公共逻辑
     */
    private void executeRequest(QwenRequest requestModel, ToolCallCache toolCallCache, long totalStartTime) throws Exception {
        // JSON序列化计时
        long jsonSerializeStartTime = System.currentTimeMillis();
        String requestJson = requestModel.toJson();
        if (requestJson == null || requestJson.isEmpty()) {
            Log.e(TAG, "请求参数为空");
            if (callback != null) {
                callback.onError(new RuntimeException("请求参数为空"));
            }
            return;
        }
        long jsonSerializeEndTime = System.currentTimeMillis();
        Log.d(TAG, "请求参数JSON序列化耗时: " + (jsonSerializeEndTime - jsonSerializeStartTime) + "ms");

        String apiUrl = appConfig.getApiUrl();
        String apiKey = appConfig.getApiKey();
        
        // DNS解析耗时
        long dnsTime = getDnsResolveTime(apiUrl);
        Log.d(TAG, "DNS解析耗时: " + dnsTime + "ms");

        // API请求发送计时（细化网络耗时）
        long apiRequestStartTime = System.currentTimeMillis();
        Log.d(TAG, "准备发送请求到: " + apiUrl);
        Log.d(TAG, "使用API Key: " + (apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : apiKey));
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson, JSON))
                .build();

        // 发送请求并记录各阶段时间
        long callStartTime = System.currentTimeMillis();
        Log.d(TAG, "开始执行请求...");
        
        // 保存 Call 对象以便后续取消
        Call call = okHttpClient.newCall(request);
        connectionManager.setCurrentCall(call);
        
        Response response = call.execute();
        long callEndTime = System.currentTimeMillis();
        
        // 请求完成后清除引用
        connectionManager.clearCall(call);

        // 网络各阶段耗时分析
        long connectionTime = 0;
        long serverProcessTime = 0;
        long receivedResponseTime = 0;
        if (response != null) {
            // 获取OkHttp的详细时间戳
            long sentRequestTime = response.sentRequestAtMillis();
            receivedResponseTime = response.receivedResponseAtMillis();

            if (sentRequestTime > 0 && receivedResponseTime > 0) {
                connectionTime = sentRequestTime - callStartTime;
                serverProcessTime = receivedResponseTime - sentRequestTime;
                long responseTransferTime = callEndTime - receivedResponseTime;

                Log.d(TAG, "TCP连接+TLS握手耗时: " + connectionTime + "ms");
                Log.d(TAG, "服务端处理耗时: " + serverProcessTime + "ms");
                Log.d(TAG, "响应数据传输耗时: " + responseTransferTime + "ms");
            }
        }

        long apiRequestEndTime = System.currentTimeMillis();
        Log.d(TAG, "API请求总耗时: " + (apiRequestEndTime - apiRequestStartTime) + "ms");
        Log.d(TAG, "纯网络传输耗时: " + (connectionTime + (callEndTime - receivedResponseTime)) + "ms");

        // 响应处理计时
        long responseHandleStartTime = System.currentTimeMillis();
        handleStreamResponse(response, toolCallCache, false);
        long responseHandleEndTime = System.currentTimeMillis();
        Log.d(TAG, "响应处理耗时: " + (responseHandleEndTime - responseHandleStartTime) + "ms");

        // 总耗时统计
        long totalEndTime = System.currentTimeMillis();
        Log.d(TAG, "API调用总耗时: " + (totalEndTime - totalStartTime) + "ms");
    }

    public void callSummaryApi(JSONArray messages) {
        new Thread(() -> {
            long totalStartTime = System.currentTimeMillis();
            try {
                if (messages == null) {
                    Log.e(TAG, "总结请求失败：上下文messages为空");
                    if (callback != null) {
                        callback.onError(new RuntimeException("总结请求缺少上下文"));
                    }
                    return;
                }

                // 请求模型构建计时
                long requestBuildStartTime = System.currentTimeMillis();
                QwenRequest requestModel = QwenRequest.builder(context)
                        .messages(messages)
                        .summary(true)
                        .toolEnabled(false)
                        .audioVoice(audioVoice)
                        .build();
                long requestBuildEndTime = System.currentTimeMillis();
                Log.d(TAG, "总结请求模型构建耗时: " + (requestBuildEndTime - requestBuildStartTime) + "ms");

                // JSON序列化计时
                long jsonSerializeStartTime = System.currentTimeMillis();
                String requestJson = requestModel.toJson();
                if (requestJson == null || requestJson.isEmpty()) {
                    Log.e(TAG, "总结请求参数为空");
                    if (callback != null) {
                        callback.onError(new RuntimeException("总结请求参数为空"));
                    }
                    return;
                }
                long jsonSerializeEndTime = System.currentTimeMillis();
                Log.d(TAG, "总结请求参数JSON序列化耗时: " + (jsonSerializeEndTime - jsonSerializeStartTime) + "ms");
                Log.d(TAG, "总结请求参数: " + requestJson);

                // 每次都从 appConfig 读取最新的 URL 和 API Key
                String apiUrl = appConfig.getApiUrl();
                String apiKey = appConfig.getApiKey();
                
                // DNS解析耗时
                long dnsTime = getDnsResolveTime(apiUrl);
                Log.d(TAG, "DNS解析耗时: " + dnsTime + "ms");

                // API请求发送计时（细化网络耗时）
                long apiRequestStartTime = System.currentTimeMillis();
                Log.d(TAG, "总结请求 - URL: " + apiUrl);
                Request request = new Request.Builder()
                        .url(apiUrl)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestJson, JSON))
                        .build();

                // 发送请求并记录各阶段时间
                long callStartTime = System.currentTimeMillis();
                
                // 保存 Call 对象以便后续取消
                Call call = okHttpClient.newCall(request);
                connectionManager.setCurrentCall(call);
                
                Response response = call.execute();
                long callEndTime = System.currentTimeMillis();
                
                // 请求完成后清除引用
                connectionManager.clearCall(call);

                // 网络各阶段耗时分析
                long connectionTime = 0;
                long serverProcessTime = 0;
                long receivedResponseTime = 0;
                if (response != null) {
                    // 获取OkHttp的详细时间戳
                    long sentRequestTime = response.sentRequestAtMillis();
                    receivedResponseTime = response.receivedResponseAtMillis();

                    if (sentRequestTime > 0 && receivedResponseTime > 0) {
                        connectionTime = sentRequestTime - callStartTime;
                        serverProcessTime = receivedResponseTime - sentRequestTime;
                        long responseTransferTime = callEndTime - receivedResponseTime;

                        Log.d(TAG, "TCP连接+TLS握手耗时: " + connectionTime + "ms");
                        Log.d(TAG, "服务端处理耗时: " + serverProcessTime + "ms");
                        Log.d(TAG, "响应数据传输耗时: " + responseTransferTime + "ms");
                    }
                }

                long apiRequestEndTime = System.currentTimeMillis();
                Log.d(TAG, "总结API请求总耗时: " + (apiRequestEndTime - apiRequestStartTime) + "ms");
                if (receivedResponseTime > 0) {
                    Log.d(TAG, "网络延迟（到首字节）: " + (receivedResponseTime - callStartTime) + "ms");
                }

                Log.d(TAG, "总结请求响应码: " + response.code());

                // 响应处理计时
                long responseHandleStartTime = System.currentTimeMillis();
                handleStreamResponse(response, new ToolCallCache(), true);
                long responseHandleEndTime = System.currentTimeMillis();
                Log.d(TAG, "总结响应处理耗时: " + (responseHandleEndTime - responseHandleStartTime) + "ms");

                // 总耗时统计
                long totalEndTime = System.currentTimeMillis();
                Log.d(TAG, "总结API调用总耗时: " + (totalEndTime - totalStartTime) + "ms");

            } catch (Throwable e) {
                Log.e(TAG, "总结请求异常", e);
                if (callback != null) {
                    callback.onError(e);
                }
                long totalEndTime = System.currentTimeMillis();
                Log.d(TAG, "总结API调用异常总耗时: " + (totalEndTime - totalStartTime) + "ms");
            }
        }).start();
    }

    // 新增：测试纯网络延迟的方法
    public void testNetworkLatency() {
        new Thread(() -> {
            try {
                Log.d(TAG, "开始测试纯网络延迟...");
                
                // 每次都从 appConfig 读取最新的 URL
                String apiUrl = appConfig.getApiUrl();

                // 1. 测试DNS解析延迟
                long dnsTime = getDnsResolveTime(apiUrl);
                Log.d(TAG, "DNS解析延迟: " + dnsTime + "ms");

                // 2. 测试TCP连接延迟
                long connectStartTime = System.currentTimeMillis();
                Request testRequest = new Request.Builder()
                        .url(apiUrl)
                        .head() // 使用HEAD请求，只建立连接不传输数据
                        .build();

                Response response = okHttpClient.newCall(testRequest).execute();
                long connectEndTime = System.currentTimeMillis();

                long totalConnectTime = connectEndTime - connectStartTime;
                Log.d(TAG, "纯连接耗时（DNS+TCP+TLS）: " + totalConnectTime + "ms");
                Log.d(TAG, "连接测试响应码: " + response.code());

                // 3. 测试ping延迟（模拟）
                long pingStartTime = System.currentTimeMillis();
                Request pingRequest = new Request.Builder()
                        .url("https://dashscope.aliyuncs.com/health") // 健康检查接口
                        .get()
                        .build();
                Response pingResponse = okHttpClient.newCall(pingRequest).execute();
                long pingEndTime = System.currentTimeMillis();

                Log.d(TAG, "API网关Ping延迟: " + (pingEndTime - pingStartTime) + "ms");

            } catch (Exception e) {
                Log.e(TAG, "网络延迟测试失败", e);
            }
        }).start();
    }

    private void handleStreamResponse(Response response, ToolCallCache toolCallCache, boolean isSummaryRequest) {
        StringBuilder summaryBuilder = new StringBuilder();
        BufferedReader reader = null;
        try {
            if (response == null) {
                Log.e(TAG, "响应为空");
                if (callback != null) {
                    callback.onError(new RuntimeException("响应为空"));
                }
                return;
            }

            if (response.isSuccessful() && response.body() != null) {
                Log.d(TAG, "======= 服务端响应开始 =======");
                Log.d(TAG, "服务器响应" + response);
                
                reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8)
                );
                // 注册 reader 到 ConnectionManager，以便取消时能立即关闭
                connectionManager.setCurrentReader(reader);
                String line;
                long streamParseStartTime = System.currentTimeMillis();
                int lineCount = 0;
                // 记录首行响应时间
                long firstLineTime = 0;
                boolean shouldDisconnect = false; // 标记是否需要提前断开连接
                // 记录最后一次检测到工具调用的时间，用于判断是否所有工具调用都已开始
                long lastToolCallTime = 0;
                int consecutiveIncompleteChecks = 0;
                 final long MAX_WAIT_TIME_FOR_MORE_TOOL_CALLS = 100; // 500ms

                while (true) {
                    try {
                        line = reader.readLine();
                        if (line == null) {
                            break; // 流结束
                        }
                    } catch (IOException e) {
                        // EOFException 通常表示流正常结束，这是正常的，不应该作为错误
                        if (e instanceof java.io.EOFException) {
                            Log.d(TAG, "流式响应正常结束（EOF），已读取 " + lineCount + " 行");
                            break; // 正常结束，退出循环
                        }
                        // 如果流被关闭（取消请求），正常退出
                        if (e.getMessage() != null && e.getMessage().contains("Stream closed")) {
                            Log.d(TAG, "流已被关闭（请求已取消），退出读取循环，已读取 " + lineCount + " 行");
                            break;
                        }
                        // 其他IO异常，记录并退出
                        Log.w(TAG, "读取响应流时发生IO异常，已读取 " + lineCount + " 行", e);
                        break;
                    }
                    
                    lineCount++;
                    if (firstLineTime == 0) {
                        firstLineTime = System.currentTimeMillis();
                        Log.d(TAG, "首行响应接收时间: " + (firstLineTime - streamParseStartTime) + "ms");
                    }
                    
                    // 打印服务端响应的原始内容
                    if (line != null && !line.trim().isEmpty()) {
                        // 如果内容太长，截取前500个字符打印
                        String logLine = line.length() > 500 ? line.substring(0, 500) + "..." : line;
                        Log.d(TAG, "[服务端响应第" + lineCount + "行] " + logLine);
                    } else {
                        Log.d(TAG, "[服务端响应第" + lineCount + "行] (空行)");
                    }

                    if (callback != null) {
                        callback.onSuccess(line);
                    }

                    if (isSummaryRequest) {
                        try {
                            line = line.trim();
                            if (line.startsWith("data:")) {
                                String jsonStr = line.replaceFirst("^data:\\s*", "");
                                if (jsonStr.startsWith("[") && jsonStr.contains("DONE")) {
                                    Log.d(TAG, "总结响应已结束，共解析" + lineCount + "行");
                                    if (callback != null) {
                                        callback.onSummaryComplete(summaryBuilder.toString().trim());
                                    }
                                    continue;
                                }
                                if (jsonStr.startsWith("{")) {
                                    JSONObject responseJson = new JSONObject(jsonStr);
                                    JSONArray choices = responseJson.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject firstChoice = choices.getJSONObject(0);
                                        JSONObject delta = firstChoice.optJSONObject("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.optString("content", "").trim();
                                            if (!content.isEmpty()) {
                                                summaryBuilder.append(content);
                                                Log.d(TAG, "拼接总结文本: " + summaryBuilder.toString());
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            Log.w(TAG, "总结响应解析失败", e);
                        }
                        continue;
                    }

                    // 只处理工具调用相关的逻辑，文本和音频交给 ResponseHandler 统一处理
                    // 这样可以避免重复解析 JSON，提高性能
                    try {
                        line = line.trim();
                        if (line.startsWith("data:")) {
                            String jsonStr = line.replaceFirst("^data:\\s*", "");
                            if (jsonStr.startsWith("[") && jsonStr.contains("DONE")) {
                                Log.d(TAG, "工具调用响应已结束，共解析" + lineCount + "行");
                                break; // 流结束，退出循环
                            }
                            if (jsonStr.startsWith("{")) {
                                // 只解析工具调用相关的数据，避免重复解析文本和音频
                                // 先快速检查是否包含 tool_calls（字符串匹配，避免完整解析）
                                if (jsonStr.contains("\"tool_calls\"")) {
                                    // 只有包含 tool_calls 时才解析 JSON
                                    JSONObject responseJson = new JSONObject(jsonStr);
                                    JSONArray choices = responseJson.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject firstChoice = choices.getJSONObject(0);
                                        JSONObject delta = firstChoice.optJSONObject("delta");
                                        if (delta == null) continue;

                                        // 检查工具调用（只处理工具调用，文本和音频交给上层处理）
                                        JSONArray toolCalls = delta.optJSONArray("tool_calls");
                                        if (toolCalls != null && toolCalls.length() > 0) {
                                            // 标记已检测到工具调用，后续音频将被跳过
                                            hasToolCallDetected = true;
                                            
                                            // 记录检测到工具调用的时间
                                            long currentTime = System.currentTimeMillis();
                                            boolean hasNewToolCall = false;

                                            for (int i = 0; i < toolCalls.length(); i++) {
                                                JSONObject toolCallJson = toolCalls.getJSONObject(i);
                                                // 检查是否是新的工具调用（通过检查是否已有该index）
                                                int index = toolCallJson.optInt("index", 0);
                                                if (!toolCallCache.hasToolCall(index)) {
                                                    hasNewToolCall = true;
                                                }
                                                toolCallCache.appendToolCall(toolCallJson);
                                            }
                                            
                                            if (hasNewToolCall) {
                                                lastToolCallTime = currentTime;
                                                consecutiveIncompleteChecks = 0; // 重置计数器
                                                int toolCallCount = toolCallCache.getToolCallCount();
                                                Log.d(TAG, "检测到新的工具调用，当前工具调用数量: " + toolCallCount + "，后续音频将被跳过");
                                            }
                                            
                                            // 检查所有工具调用是否都已完成
                                            if (toolCallCache.areAllToolCallsComplete()) {
                                                // 如果所有已检测到的工具调用都完成了，还需要判断是否还有更多工具调用可能出现
                                                // 如果距离最后一次检测到新工具调用已经超过等待时间，认为所有工具调用都已开始
                                                long timeSinceLastToolCall = currentTime - lastToolCallTime;
                                                if (lastToolCallTime == 0 || timeSinceLastToolCall >= MAX_WAIT_TIME_FOR_MORE_TOOL_CALLS) {
                                                    Log.d(TAG, "所有工具调用信息已完整获取（共" + toolCallCache.getToolCallCount() + "个），准备断开连接并立即处理");
                                                    shouldDisconnect = true;
                                                    break; // 退出循环，断开连接
                                                } else {
                                                    Log.d(TAG, "所有已检测到的工具调用已完成，但等待更多工具调用出现（已等待" + timeSinceLastToolCall + "ms）");
                                                }
                                            } else {
                                                // 如果还有未完成的工具调用，增加连续未完成检查计数
                                                consecutiveIncompleteChecks++;
                                            }
                                        }
                                    }
                                } else {
                                    // 不包含 tool_calls，不需要解析，交给上层处理
                                    // 这样可以避免重复解析 JSON
                                }
                                
                                // 如果没有工具调用数据，检查是否应该等待
                                // 如果已经有工具调用但未完成，且距离最后一次工具调用超过等待时间，可以断开
                                if (lastToolCallTime > 0 && toolCallCache.getToolCallCount() > 0) {
                                    long timeSinceLastToolCall = System.currentTimeMillis() - lastToolCallTime;
                                    if (timeSinceLastToolCall >= MAX_WAIT_TIME_FOR_MORE_TOOL_CALLS) {
                                        // 如果所有已检测到的工具调用都完成了，可以断开
                                        if (toolCallCache.areAllToolCallsComplete()) {
                                            Log.d(TAG, "等待超时，所有工具调用信息已完整获取（共" + toolCallCache.getToolCallCount() + "个），准备断开连接");
                                            shouldDisconnect = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "工具调用解析失败", e);
                    }
                    
                    // 如果需要提前断开连接，退出循环
                    if (shouldDisconnect) {
                        break;
                    }
                }
                // 清除 reader 引用
                connectionManager.clearReader();
                
                connectionManager.closeAll(reader, null, shouldDisconnect);
                
                long streamParseEndTime = System.currentTimeMillis();
                Log.d(TAG, "======= 服务端响应结束 =======");
                if (shouldDisconnect) {
                    Log.d(TAG, "流式响应提前断开，总行数: " + lineCount + "，耗时: " + (streamParseEndTime - streamParseStartTime) + "ms");
                } else {
                    Log.d(TAG, "流式响应解析完成，总行数: " + lineCount + "，耗时: " + (streamParseEndTime - streamParseStartTime) + "ms");
                }

                // 计算平均每行传输时间
                if (lineCount > 0) {
                    long avgLineTime = (streamParseEndTime - streamParseStartTime) / lineCount;
                    Log.d(TAG, "平均每行响应传输时间: " + avgLineTime + "ms");
                }
                
                // 如果提前断开连接，立即处理工具调用
                if (shouldDisconnect && toolCallCache.hasCompleteToolCalls() && callback != null) {
                    java.util.List<CompleteToolCall> allCompleteCalls = toolCallCache.getAllCompleteToolCalls();
                    Log.d(TAG, "提前断开连接后，发现 " + allCompleteCalls.size() + " 个工具调用，立即处理");
                    // 触发所有工具调用回调（按顺序）
                    for (CompleteToolCall completeToolCall : allCompleteCalls) {
                        Log.d(TAG, "触发工具调用回调: " + completeToolCall.functionName);
                        callback.onToolCallComplete(completeToolCall);
                    }
                    return; // 提前返回，不继续处理
                }
                
                // 流式响应正常结束后，检查是否有已完成的工具调用
                if (toolCallCache.hasCompleteToolCalls() && callback != null) {
                    java.util.List<CompleteToolCall> allCompleteCalls = toolCallCache.getAllCompleteToolCalls();
                    Log.d(TAG, "流式响应结束，发现 " + allCompleteCalls.size() + " 个工具调用，准备一次性处理");
                    // 触发所有工具调用回调（按顺序）
                    for (CompleteToolCall completeToolCall : allCompleteCalls) {
                        Log.d(TAG, "触发工具调用回调: " + completeToolCall.functionName);
                        callback.onToolCallComplete(completeToolCall);
                    }
                }

            } else {
                Log.e(TAG, "======= 服务端错误响应 =======");
                int errorCode = response.code();
                String errorMsg = response.message();
                Log.e(TAG, "HTTP状态码: " + errorCode);
                Log.e(TAG, "HTTP状态消息: " + errorMsg);
                Log.e(TAG, "响应头信息:");
                for (String headerName : response.headers().names()) {
                    Log.e(TAG, "  " + headerName + ": " + response.header(headerName));
                }
                if (response.body() != null) {
                    String errorBody = response.body().string();
                    Log.e(TAG, "响应体内容: " + errorBody);
                    errorMsg = errorMsg + "，详细信息：" + errorBody;
                } else {
                    Log.e(TAG, "响应体为空");
                }
                Log.e(TAG, "API请求失败: 错误码=" + errorCode + ", 错误信息=" + errorMsg);
                if (callback != null) {
                    callback.onFailure(errorCode, errorMsg);
                }
            }
        } catch (IOException e) {
            // EOFException 通常表示流正常结束，这是正常的，不应该作为错误
            if (e instanceof java.io.EOFException) {
                Log.d(TAG, "流式响应正常结束（EOF），可能是空响应或流已结束");
                // 如果是空响应，检查是否有工具调用需要处理
                if (toolCallCache != null && toolCallCache.hasCompleteToolCalls() && callback != null) {
                    java.util.List<CompleteToolCall> allCompleteCalls = toolCallCache.getAllCompleteToolCalls();
                    Log.d(TAG, "流结束前发现 " + allCompleteCalls.size() + " 个工具调用，立即处理");
                    for (CompleteToolCall completeToolCall : allCompleteCalls) {
                        callback.onToolCallComplete(completeToolCall);
                    }
                }
                return; // 正常结束，不报错
            }
            // 如果是因为流被关闭导致的异常（请求被取消），不记录为错误
            if (e.getMessage() != null && e.getMessage().contains("Stream closed")) {
                Log.d(TAG, "响应流已关闭（请求可能已被取消）");
            } else {
                Log.e(TAG, "响应处理IO异常", e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "响应处理失败", e);
            if (callback != null) {
                callback.onError(e);
            }
        } finally {
            // 清除 reader 引用
            connectionManager.clearReader();
            
            // 确保Response被关闭
            ConnectionManager.closeResponse(response);
        }
    }
}