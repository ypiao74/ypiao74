package com.example.myapplication.network.model;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.config.AppConfig;
import com.example.myapplication.config.AppConstants;
import com.example.myapplication.util.ToolLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Qwen3-Omni-Mobile API请求模型
 * 根据Qwen3-Omni-Mobile云端服务接口文档实现
 */
public class QwenRequest {
    private static final String TAG = "QwenRequest";
    private static final String BASE_SYSTEM_MESSAGE = "生成流畅、准确且符合上下文的自然语言文本，同时利用高质量语音合成技术将文本转换为发音标准、语速适中、音质清晰的音频文件；整个过程严格遵循输入语种，确保内容简洁易懂，杜绝语法错误与发音偏差。\n" +
            "语速尽量快一点。";
    
    // Mobile模型专用的工具调用system prompt模板
    private static final String MOBILE_TOOL_SYSTEM_PROMPT_TEMPLATE = 
        "拥有 20 年实战经验的专业越野教练。职责是利用多模态数据（视觉图像与车辆传感器数据）为驾驶员提供实时、准确、权威且易懂的越野指导。性格沉稳、果断。\n" +
                "核心能力：\n" +
                "多模态融合分析：同时参考【路况图像】和【车辆数据】进行综合判断。\n" +
                "风险分级预警：根据风险程度（高/中/低）选择对应模板和语气。\n" +
                "专业指导：掌握沙漠、泥地、涉水、攀爬等场景的黄金法则（快走沙，慢走水；慢上快下）。\n" +
                "情绪支持：在紧张时刻（如陡坡、陷车）提供冷静引导与鼓励。\n" +
                "输出规范（模板）：\n" +
                "低风险（一般指导）：教练分析：[场景]。建议：[具体操作]。\n\n" +
        "You may call one or more functions to assist with the user query.\n\n" +
        "You are provided with function signatures within <tools></tools> XML tags:\n" +
        "<tools>\n" +
        "%s\n" +
        "</tools>\n\n" +
        "For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:\n" +
        "<tool_call>\n" +
        "{{\"name\": <function-name>, \"arguments\": <args-json-object>}}\n" +
        "</tool_call>";

    private static final String DEFAULT_AUDIO_VOICE = "Cherry";
    private static final String DEFAULT_AUDIO_FORMAT = "wav";
    
    // 音频配置（可通过 Builder 自定义）
    private final String audioVoice;
    private final String audioFormat;

    private final Context context;
    private JSONArray messages;
    private final boolean isSummaryRequest;
    private final boolean isToolEnabled;
    private final boolean isToolCallbackRequest;
    private final String modelName; // 模型名称，从 AppConfig 获取

    // Getter 方法
    public JSONArray getMessages() {
        return messages;
    }

    public boolean isSummaryRequest() {
        return isSummaryRequest;
    }

    public boolean isToolEnabled() {
        return isToolEnabled;
    }

    public boolean isToolCallbackRequest() {
        return isToolCallbackRequest;
    }

    /**
     * 私有构造函数，由 Builder 调用
     */
    private QwenRequest(Builder builder) throws JSONException {
        // 参数验证
        if (builder.context == null) {
            throw new IllegalArgumentException("Context 不能为空");
        }
        
        this.context = builder.context;
        this.isSummaryRequest = builder.isSummaryRequest;
        this.isToolEnabled = builder.isToolEnabled;
        this.isToolCallbackRequest = builder.isToolCallbackRequest;
        this.modelName = builder.modelName != null ? builder.modelName : new AppConfig(builder.context).getModelName();
        this.messages = builder.messages != null ? builder.messages : new JSONArray();
        this.audioVoice = builder.audioVoice != null ? builder.audioVoice : DEFAULT_AUDIO_VOICE;
        this.audioFormat = builder.audioFormat != null ? builder.audioFormat : DEFAULT_AUDIO_FORMAT;

        if (builder.messages != null && builder.messages.length() > 0) {
            this.messages = builder.messages;
        } else if (builder.videoBase64 != null) {
            buildVideoFileMessages(builder.videoBase64, builder.videoFormat, builder.textContent);
        } else if (builder.videoImageBase64List != null && !builder.videoImageBase64List.isEmpty()) {
            buildVideoImageListMessages(builder.videoImageBase64List, builder.videoImageFormatList, builder.textContent);
        } else if (builder.audioBase64 != null) {
            buildAudioMessages(builder.audioBase64);
        } else if (builder.imageBase64List != null && !builder.imageBase64List.isEmpty()) {
            buildMultipleImageMessages(builder.imageBase64List, builder.imageFormatList, builder.textContent);
        } else if (builder.imageBase64 != null) {
            buildImageMessages(builder.imageBase64, builder.imageFormat, builder.textContent);
        } else if (builder.textContent != null) {
            buildTextMessages(builder.textContent);
        } else {
            throw new IllegalArgumentException("必须提供至少一种输入类型：audio、text、image、video 或 messages");
        }
    }

    /**
     * Builder 类：用于构建 QwenRequest
     */
    public static class Builder {
        private final Context context;
        private String modelName;
        private String audioBase64;
        private String textContent;
        private String imageBase64;
        private String imageFormat;
        private java.util.List<String> imageBase64List;
        private java.util.List<String> imageFormatList;
        // 视频相关字段
        private String videoBase64; // 视频文件Base64编码
        private String videoFormat; // 视频格式（如 "mp4"）
        private java.util.List<String> videoImageBase64List; // 视频图片列表Base64编码
        private java.util.List<String> videoImageFormatList; // 视频图片列表格式
        private JSONArray messages;
        private boolean isSummaryRequest = false;
        private boolean isToolEnabled = true;
        private boolean isToolCallbackRequest = false;
        private String audioVoice;
        private String audioFormat;

        public Builder(Context context) {
            this.context = context;
        }

        /**
         * 设置模型名称（可选，默认从 AppConfig 获取）
         */
        public Builder model(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * 设置音频输入
         */
        public Builder audio(String audioBase64) {
            this.audioBase64 = audioBase64;
            return this;
        }

        /**
         * 设置文本内容
         */
        public Builder text(String textContent) {
            this.textContent = textContent;
            return this;
        }

        /**
         * 设置单张图片
         */
        public Builder image(String imageBase64, String imageFormat) {
            this.imageBase64 = imageBase64;
            this.imageFormat = imageFormat;
            return this;
        }

        /**
         * 设置多张图片
         */
        public Builder images(java.util.List<String> imageBase64List, java.util.List<String> imageFormatList) {
            this.imageBase64List = imageBase64List;
            this.imageFormatList = imageFormatList;
            return this;
        }

        /**
         * 设置视频文件（视频文件形式）
         * @param videoBase64 视频文件Base64编码
         * @param videoFormat 视频格式（如 "mp4"）
         */
        public Builder video(String videoBase64, String videoFormat) {
            this.videoBase64 = videoBase64;
            this.videoFormat = videoFormat;
            return this;
        }

        /**
         * 设置视频图片列表（图片列表形式）
         * @param videoImageBase64List 图片Base64编码列表
         * @param videoImageFormatList 图片格式列表
         */
        public Builder videoImages(java.util.List<String> videoImageBase64List, java.util.List<String> videoImageFormatList) {
            this.videoImageBase64List = videoImageBase64List;
            this.videoImageFormatList = videoImageFormatList;
            return this;
        }

        /**
         * 设置消息历史（用于工具回调等场景）
         */
        public Builder messages(JSONArray messages) {
            this.messages = messages;
            return this;
        }

        /**
         * 设置为总结请求
         */
        public Builder summary(boolean isSummaryRequest) {
            this.isSummaryRequest = isSummaryRequest;
            return this;
        }

        /**
         * 启用/禁用工具
         */
        public Builder toolEnabled(boolean isToolEnabled) {
            this.isToolEnabled = isToolEnabled;
            return this;
        }

        /**
         * 设置为工具回调请求
         */
        public Builder toolCallback(boolean isToolCallbackRequest) {
            this.isToolCallbackRequest = isToolCallbackRequest;
            return this;
        }

        /**
         * 设置音频语音（可选，默认 "Sunny"）
         */
        public Builder audioVoice(String audioVoice) {
            this.audioVoice = audioVoice;
            return this;
        }

        /**
         * 设置音频格式（可选，默认 "wav"）
         */
        public Builder audioFormat(String audioFormat) {
            this.audioFormat = audioFormat;
            return this;
        }

        /**
         * 构建 QwenRequest 实例
         * @throws JSONException JSON构建异常
         * @throws IllegalArgumentException 参数验证失败
         */
        public QwenRequest build() throws JSONException {
            // 参数验证
            validate();
            return new QwenRequest(this);
        }

        /**
         * 验证 Builder 参数
         * @throws IllegalArgumentException 参数验证失败
         */
        private void validate() {
            if (context == null) {
                throw new IllegalArgumentException("Context 不能为空");
            }
            
            // 检查是否至少提供了一种输入类型
            boolean hasInput = (audioBase64 != null && !audioBase64.isEmpty())
                    || (textContent != null && !textContent.trim().isEmpty())
                    || (imageBase64 != null && !imageBase64.isEmpty())
                    || (imageBase64List != null && !imageBase64List.isEmpty())
                    || (videoBase64 != null && !videoBase64.isEmpty())
                    || (videoImageBase64List != null && !videoImageBase64List.isEmpty())
                    || (messages != null && messages.length() > 0);
            
            if (!hasInput) {
                throw new IllegalArgumentException("必须提供至少一种输入类型：audio、text、image、video 或 messages");
            }
            
            // 验证图片相关参数
            if (imageBase64 != null && (imageFormat == null || imageFormat.trim().isEmpty())) {
                throw new IllegalArgumentException("设置图片时必须提供图片格式（imageFormat）");
            }
            
            if (imageBase64List != null && imageFormatList != null) {
                if (imageBase64List.size() != imageFormatList.size()) {
                    throw new IllegalArgumentException("图片列表和格式列表的长度必须一致");
                }
            }
            
            // 验证视频相关参数
            if (videoBase64 != null && (videoFormat == null || videoFormat.trim().isEmpty())) {
                throw new IllegalArgumentException("设置视频时必须提供视频格式（videoFormat）");
            }
            
            if (videoImageBase64List != null && videoImageFormatList != null) {
                if (videoImageBase64List.size() != videoImageFormatList.size()) {
                    throw new IllegalArgumentException("视频图片列表和格式列表的长度必须一致");
                }
                // 验证图片数量：Flash最少2张，Mobile最少2张（根据文档）
                if (videoImageBase64List.size() < 2) {
                    throw new IllegalArgumentException("视频图片列表至少需要2张图片");
                }
            }
        }
    }

    /**
     * 创建 Builder 实例
     */
    public static Builder builder(Context context) {
        return new Builder(context);
    }

    /**
     * 构建音频请求
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).audio(audioBase64).toolEnabled(isToolEnabled).build()
     */
    @Deprecated
    public QwenRequest(Context context, String audioBase64, boolean isSummaryRequest, boolean isToolEnabled) throws JSONException {
        this(context, audioBase64, isSummaryRequest, isToolEnabled, false);
    }

    /**
     * 构建音频请求（完整参数）
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).audio(audioBase64).summary(isSummaryRequest).toolEnabled(isToolEnabled).toolCallback(isToolCallbackRequest).build()
     */
    @Deprecated
    public QwenRequest(Context context, String audioBase64, boolean isSummaryRequest, boolean isToolEnabled, boolean isToolCallbackRequest) throws JSONException {
        this.context = context;
        this.isSummaryRequest = isSummaryRequest;
        this.isToolEnabled = isToolEnabled;
        this.isToolCallbackRequest = isToolCallbackRequest;
        this.modelName = new AppConfig(context).getModelName();
        this.messages = new JSONArray();
        this.audioVoice = DEFAULT_AUDIO_VOICE;
        this.audioFormat = DEFAULT_AUDIO_FORMAT;
        buildAudioMessages(audioBase64);
    }

    /**
     * 构建消息请求
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).messages(messages).summary(isSummaryRequest).toolEnabled(isToolEnabled).build()
     */
    @Deprecated
    public QwenRequest(Context context, JSONArray messages, boolean isSummaryRequest, boolean isToolEnabled) {
        this(context, messages, isSummaryRequest, isToolEnabled, false);
    }

    /**
     * 构建消息请求（完整参数）
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).messages(messages).summary(isSummaryRequest).toolEnabled(isToolEnabled).toolCallback(isToolCallbackRequest).build()
     */
    @Deprecated
    public QwenRequest(Context context, JSONArray messages, boolean isSummaryRequest, boolean isToolEnabled, boolean isToolCallbackRequest) {
        this.context = context;
        this.messages = messages;
        this.isSummaryRequest = isSummaryRequest;
        this.isToolEnabled = isToolEnabled;
        this.isToolCallbackRequest = isToolCallbackRequest;
        this.modelName = new AppConfig(context).getModelName();
        this.audioVoice = DEFAULT_AUDIO_VOICE;
        this.audioFormat = DEFAULT_AUDIO_FORMAT;
    }

    /**
     * 构建文本请求
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).text(textContent).toolEnabled(isToolEnabled).build()
     */
    @Deprecated
    public QwenRequest(Context context, String textContent, boolean isToolEnabled) throws JSONException {
        this.context = context;
        this.isSummaryRequest = false;
        this.isToolEnabled = isToolEnabled;
        this.isToolCallbackRequest = false;
        this.modelName = new AppConfig(context).getModelName();
        this.messages = new JSONArray();
        this.audioVoice = DEFAULT_AUDIO_VOICE;
        this.audioFormat = DEFAULT_AUDIO_FORMAT;
        buildTextMessages(textContent);
    }

    /**
     * 构建图片请求（仅图片）
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).image(imageBase64, imageFormat).toolEnabled(isToolEnabled).build()
     */
    @Deprecated
    public QwenRequest(Context context, String imageBase64, String imageFormat, boolean isToolEnabled) throws JSONException {
        this(context, imageBase64, imageFormat, null, isToolEnabled);
    }

    /**
     * 构建图片+文本请求
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).image(imageBase64, imageFormat).text(textContent).toolEnabled(isToolEnabled).build()
     */
    @Deprecated
    public QwenRequest(Context context, String imageBase64, String imageFormat, String textContent, boolean isToolEnabled) throws JSONException {
        this.context = context;
        this.isSummaryRequest = false;
        this.isToolEnabled = isToolEnabled;
        this.isToolCallbackRequest = false;
        this.modelName = new AppConfig(context).getModelName();
        this.messages = new JSONArray();
        this.audioVoice = DEFAULT_AUDIO_VOICE;
        this.audioFormat = DEFAULT_AUDIO_FORMAT;
        buildImageMessages(imageBase64, imageFormat, textContent);
    }

    /**
     * 构建多图片+文本请求
     * @deprecated 推荐使用 Builder 模式：QwenRequest.builder(context).images(imageBase64List, imageFormatList).text(textContent).toolEnabled(isToolEnabled).build()
     */
    @Deprecated
    public QwenRequest(Context context, java.util.List<String> imageBase64List, java.util.List<String> imageFormatList, String textContent, boolean isToolEnabled) throws JSONException {
        this.context = context;
        this.isSummaryRequest = false;
        this.isToolEnabled = isToolEnabled;
        this.isToolCallbackRequest = false;
        this.modelName = new AppConfig(context).getModelName();
        this.messages = new JSONArray();
        this.audioVoice = DEFAULT_AUDIO_VOICE;
        this.audioFormat = DEFAULT_AUDIO_FORMAT;
        buildMultipleImageMessages(imageBase64List, imageFormatList, textContent);
    }

    /**
     * 构建音频消息
     * 根据API文档，音频输入格式为：
     * {
     * "type": "input_audio",
     * "input_audio": {
     * "data": "data:;base64,{base64_audio}",
     * "format": "wav"  // 或 "mp3"
     * }
     * }
     */
    private void buildAudioMessages(String audioBase64) throws JSONException {
        addSystemMessage();
        JSONArray content = new JSONArray();
        
        JSONObject audioContent = new JSONObject();
        audioContent.put("type", "input_audio");
        JSONObject inputAudio = new JSONObject();
        inputAudio.put("data", "data:;base64," + (audioBase64 == null ? "" : audioBase64));
        inputAudio.put("format", "wav");
        audioContent.put("input_audio", inputAudio);
        content.put(audioContent);
        
        addUserMessage(content);
    }

    /**
     * 构建文本消息
     *
     * @param textContent 文本内容
     * @throws JSONException JSON异常
     */
    private void buildTextMessages(String textContent) throws JSONException {
        addSystemMessage();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", textContent == null ? "" : textContent);
        messages.put(userMsg);
    }

    /**
     * 添加系统消息
     * 根据模型类型决定是否将工具描述添加到system prompt中
     * Mobile模型：使用content数组格式，包含text类型
     * Flash模型：使用直接字符串格式（保持兼容）
     */
    private void addSystemMessage() throws JSONException {
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        
        String systemContent = BASE_SYSTEM_MESSAGE;
        
        // Mobile模型：将tools描述放在system prompt中
        if (isMobileModel() && isToolEnabled && !isToolCallbackRequest) {
            try {
                JSONArray tools = ToolLoader.loadTools(context);
                String toolsXml = convertToolsToXml(tools);
                systemContent = String.format(MOBILE_TOOL_SYSTEM_PROMPT_TEMPLATE, toolsXml);
                Log.d(TAG, "Mobile模型：已将工具描述添加到system prompt");
                
                // Mobile模型：使用content数组格式（参考API示例）
                JSONArray contentArray = new JSONArray();
                JSONObject textContent = new JSONObject();
                textContent.put("type", "text");
                textContent.put("text", systemContent);
                contentArray.put(textContent);
                systemMsg.put("content", contentArray);
            } catch (Exception e) {
                Log.e(TAG, "加载工具配置失败，使用默认system message", e);
                // 失败时也使用数组格式
                JSONArray contentArray = new JSONArray();
                JSONObject textContent = new JSONObject();
                textContent.put("type", "text");
                textContent.put("text", systemContent);
                contentArray.put(textContent);
                systemMsg.put("content", contentArray);
            }
        } else {
            // Flash模型：使用直接字符串格式（保持原有兼容性）
            systemMsg.put("content", systemContent);
        }
        
        messages.put(systemMsg);
    }
    
    /**
     * 判断是否为Mobile模型
     */
    private boolean isMobileModel() {
        return modelName != null && modelName.contains("mobile");
    }
    
    /**
     * 将工具配置JSONArray转换为XML格式字符串
     * 格式：每个工具占一行，使用Python字典格式（单引号）
     */
    private String convertToolsToXml(JSONArray tools) throws JSONException {
        StringBuilder xmlBuilder = new StringBuilder();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            String toolStr = tool.toString().replace("\"", "'");
            xmlBuilder.append(toolStr);
            if (i < tools.length() - 1) {
                xmlBuilder.append("\n");
            }
        }
        return xmlBuilder.toString();
    }

    /**
     * 添加用户消息（使用 content 数组格式）
     */
    private void addUserMessage(JSONArray content) throws JSONException {
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", content);
        messages.put(userMsg);
    }

    /**
     * 构建图片消息
     * 根据API文档，图片输入格式为：
     * {
     * "type": "image_url",
     * "image_url": {
     * "url": "data:image/{format};base64,{base64_image}"
     * }
     * }
     * 如果包含文本，格式为：
     * [
     *   {"type": "text", "text": "文本内容"},
     *   {"type": "image_url", "image_url": {"url": "data:image/{format};base64,{base64}"}}
     * ]
     *
     * @param imageBase64 图片Base64编码（不含前缀）
     * @param imageFormat 图片格式（如 "jpeg", "png", "webp"）
     * @param textContent 文本内容（可选）
     * @throws JSONException JSON异常
     */
    private void buildImageMessages(String imageBase64, String imageFormat, String textContent) throws JSONException {
        addSystemMessage();
        JSONArray content = new JSONArray();
        
        // 如果有文本内容，先添加文本
        if (textContent != null && !textContent.trim().isEmpty()) {
            content.put(createTextContent(textContent));
        }
        
        // 添加图片内容
        content.put(createImageContent(imageBase64, imageFormat));
        
        addUserMessage(content);
    }

    /**
     * 构建多图片消息
     * 根据API文档，支持传入多张图片，格式为：
     * [
     *   {"type": "text", "text": "文本内容"},
     *   {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,{base64}"}},
     *   {"type": "image_url", "image_url": {"url": "data:image/png;base64,{base64}"}},
     *   ...
     * ]
     *
     * @param imageBase64List 图片Base64编码列表
     * @param imageFormatList 图片格式列表
     * @param textContent     文本内容（可选）
     * @throws JSONException JSON异常
     */
    private void buildMultipleImageMessages(java.util.List<String> imageBase64List, java.util.List<String> imageFormatList, String textContent) throws JSONException {
        addSystemMessage();
        JSONArray content = new JSONArray();
        
        // 如果有文本内容，先添加文本
        if (textContent != null && !textContent.trim().isEmpty()) {
            content.put(createTextContent(textContent));
        }
        
        // 添加所有图片
        if (imageBase64List != null && imageFormatList != null) {
            int imageCount = Math.min(imageBase64List.size(), imageFormatList.size());
            for (int i = 0; i < imageCount; i++) {
                String imageBase64 = imageBase64List.get(i);
                String imageFormat = imageFormatList.get(i);
                if (imageBase64 != null && imageFormat != null) {
                    content.put(createImageContent(imageBase64, imageFormat));
                }
            }
            Log.d(TAG, "已添加 " + imageCount + " 张图片到请求");
        }
        
        addUserMessage(content);
    }

    /**
     * 检查消息中是否包含图片输入
     */
    private boolean hasImageInput(JSONArray messages) {
        if (messages == null) {
            return false;
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg == null) continue;
            
            Object content = msg.opt("content");
            if (content instanceof JSONArray) {
                JSONArray contentArr = (JSONArray) content;
                for (int j = 0; j < contentArr.length(); j++) {
                    JSONObject itemObj = contentArr.optJSONObject(j);
                    if (itemObj != null && "image_url".equals(itemObj.optString("type"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 创建图片内容对象
     */
    private JSONObject createImageContent(String imageBase64, String imageFormat) throws JSONException {
        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        String mimeType = "image/" + (imageFormat != null ? imageFormat.toLowerCase() : "jpeg");
        imageUrl.put("url", "data:" + mimeType + ";base64," + (imageBase64 == null ? "" : imageBase64));
        imageContent.put("image_url", imageUrl);
        return imageContent;
    }

    /**
     * 创建文本内容对象
     */
    private JSONObject createTextContent(String text) throws JSONException {
        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", text == null ? "" : text);
        return textContent;
    }

    /**
     * 构建视频文件消息（视频文件形式）
     * @param videoBase64 视频文件Base64编码
     * @param videoFormat 视频格式（如 "mp4"）
     * @param textContent 文本内容（可选）
     * @throws JSONException JSON异常
     */
    private void buildVideoFileMessages(String videoBase64, String videoFormat, String textContent) throws JSONException {
        addSystemMessage();
        JSONArray content = new JSONArray();
        
        // 添加视频内容
        JSONObject videoContent = new JSONObject();
        videoContent.put("type", "video_url");
        JSONObject videoUrl = new JSONObject();
        String mimeType = "video/" + (videoFormat != null ? videoFormat.toLowerCase() : "mp4");
        videoUrl.put("url", "data:" + mimeType + ";base64," + (videoBase64 == null ? "" : videoBase64));
        videoContent.put("video_url", videoUrl);
        content.put(videoContent);
        
        // 如果有文本内容，添加文本
        if (textContent != null && !textContent.trim().isEmpty()) {
            content.put(createTextContent(textContent));
        }
        
        addUserMessage(content);
        Log.d(TAG, "已添加视频文件到请求，格式: " + videoFormat);
    }

    /**
     * 构建视频图片列表消息（图片列表形式）
     * @param videoImageBase64List 图片Base64编码列表
     * @param videoImageFormatList 图片格式列表
     * @param textContent 文本内容（可选）
     * @throws JSONException JSON异常
     */
    private void buildVideoImageListMessages(java.util.List<String> videoImageBase64List, java.util.List<String> videoImageFormatList, String textContent) throws JSONException {
        addSystemMessage();
        JSONArray content = new JSONArray();
        
        // 添加视频图片列表
        JSONObject videoContent = new JSONObject();
        videoContent.put("type", "video");
        JSONArray videoArray = new JSONArray();
        
        if (videoImageBase64List != null && videoImageFormatList != null) {
            int imageCount = Math.min(videoImageBase64List.size(), videoImageFormatList.size());
            for (int i = 0; i < imageCount; i++) {
                String imageBase64 = videoImageBase64List.get(i);
                String imageFormat = videoImageFormatList.get(i);
                if (imageBase64 != null && imageFormat != null) {
                    String mimeType = "image/" + imageFormat.toLowerCase();
                    String imageUrl = "data:" + mimeType + ";base64," + imageBase64;
                    videoArray.put(imageUrl);
                }
            }
            Log.d(TAG, "已添加 " + imageCount + " 张图片到视频列表");
        }
        
        videoContent.put("video", videoArray);
        content.put(videoContent);
        
        // 如果有文本内容，添加文本
        if (textContent != null && !textContent.trim().isEmpty()) {
            content.put(createTextContent(textContent));
        }
        
        addUserMessage(content);
    }

    /**
     * 转换为JSON字符串
     * 根据Qwen3-Omni-Mobile API文档规范构建请求
     */
    public String toJson() throws JSONException, IOException {
        JSONObject requestJson = new JSONObject();

        requestJson.put("model", modelName);
        Log.d(TAG, "使用模型: " + modelName);

        requestJson.put("stream", true);

        JSONObject streamOptions = new JSONObject();
        streamOptions.put("include_usage", true);
        requestJson.put("stream_options", streamOptions);

        // 模态配置：根据消息内容和模型类型动态添加支持的模态
        JSONArray modalities = new JSONArray();
        modalities.put("text");
        modalities.put("audio");

         requestJson.put("modalities", modalities);

        // Flash模型：使用标准的tools数组和tool_choice
        // Mobile模型：工具描述已在system prompt中，不需要在request body中添加tools
        if (isToolEnabled && !isMobileModel()) {
            JSONArray tools = ToolLoader.loadTools(context);
            requestJson.put("tools", tools);
            // 二次调用时，将 tool_choice 设置为 "none"，避免再次触发工具调用
            String toolChoice = isToolCallbackRequest ? "none" : AppConstants.TOOL_CHOICE_AUTO;
            requestJson.put("tool_choice", toolChoice);
            if (!isToolCallbackRequest) {
                requestJson.put("parallel_tool_calls", true);
            }
            Log.d(TAG, "Flash模型：请求已添加 " + tools.length() + " 个工具配置，tool_choice=" + toolChoice + ", parallel_tool_calls=" + (!isToolCallbackRequest));
        } else if (isToolEnabled && isMobileModel()) {
            Log.d(TAG, "Mobile模型：工具描述已在system prompt中，无需在request body中添加tools字段");
        }

        // 音频配置：使用 "audio" 字段（不是 "audio_config"）
        JSONObject audioConfig = new JSONObject();
        audioConfig.put("voice", audioVoice);
        audioConfig.put("format", audioFormat);
        requestJson.put("audio", audioConfig);

        requestJson.put("messages", messages);

        // 打印请求体日志（隐藏 Base64 数据以避免日志过长）
        logRequestForDebug(requestJson);

        return requestJson.toString();
    }

    /**
     * 打印请求体日志（隐藏 Base64 数据）
     */
    private void logRequestForDebug(JSONObject requestJson) {
        try {
            JSONObject logJson = new JSONObject(requestJson.toString());
            JSONArray msgArray = logJson.optJSONArray("messages");
            if (msgArray != null) {
                for (int i = 0; i < msgArray.length(); i++) {
                    JSONObject msgObj = msgArray.optJSONObject(i);
                    if (msgObj == null) continue;
                    
                    Object content = msgObj.opt("content");
                    if (content instanceof JSONArray) {
                        JSONArray contentArr = (JSONArray) content;
                        for (int j = 0; j < contentArr.length(); j++) {
                            JSONObject contentObj = contentArr.optJSONObject(j);
                            if (contentObj == null) continue;
                            
                            // 隐藏音频 Base64 数据
                            if (contentObj.has("input_audio")) {
                                JSONObject inputAudio = contentObj.optJSONObject("input_audio");
                                if (inputAudio != null) {
                                    inputAudio.put("data", "[BASE64_DATA]");
                                }
                            }
                            // 隐藏图片 Base64 数据
                            if (contentObj.has("image_url")) {
                                JSONObject imageUrl = contentObj.optJSONObject("image_url");
                                if (imageUrl != null) {
                                    imageUrl.put("url", "[BASE64_DATA]");
                                }
                            }
                            // 隐藏视频 Base64 数据
                            if (contentObj.has("video_url")) {
                                JSONObject videoUrl = contentObj.optJSONObject("video_url");
                                if (videoUrl != null) {
                                    videoUrl.put("url", "[BASE64_DATA]");
                                }
                            }
                            // 隐藏视频图片列表 Base64 数据
                            if (contentObj.has("video")) {
                                JSONArray videoArray = contentObj.optJSONArray("video");
                                if (videoArray != null) {
                                    JSONArray newVideoArray = new JSONArray();
                                    for (int k = 0; k < videoArray.length(); k++) {
                                        newVideoArray.put("[BASE64_DATA]");
                                    }
                                    contentObj.put("video", newVideoArray);
                                }
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "请求体: " + logJson.toString());
        } catch (JSONException e) {
            Log.w(TAG, "打印请求体日志失败", e);
        }
    }
}