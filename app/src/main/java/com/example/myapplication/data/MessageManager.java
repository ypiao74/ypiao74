package com.example.myapplication.data;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 消息历史管理器
 * 负责管理对话历史和状态
 */
public class MessageManager {
    private static final String TAG = "MessageManager";
    
    private JSONArray messageHistory;
    private StringBuilder textContentBuilder;
    private StringBuilder audioBase64Builder;
    private String audioTranscript; // 输出音频的转录文本
    
    public MessageManager() {
        reset();
    }
    
    /**
     * 重置所有状态
     */
    public void reset() {
        messageHistory = new JSONArray();
        textContentBuilder = new StringBuilder();
        audioBase64Builder = new StringBuilder();
        audioTranscript = null;
    }
    
    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) throws JSONException {
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", content);
        messageHistory.put(userMsg);
        Log.d(TAG, "已添加用户消息，当前历史长度: " + messageHistory.length());
    }
    
    /**
     * 添加助手消息（工具调用）
     * 支持并行工具调用：如果有多个工具调用，会将它们合并到一个 Assistant Message 的 tool_calls 数组中
     */
    public void addAssistantToolCallMessage(String toolId, String functionName, JSONObject arguments) throws JSONException {
        // 检查是否已经有包含 tool_calls 的 Assistant Message（最后一个）
        JSONObject lastAssistantMsg = null;
        int lastAssistantIndex = -1;
        for (int i = messageHistory.length() - 1; i >= 0; i--) {
            JSONObject msg = messageHistory.getJSONObject(i);
            if ("assistant".equals(msg.optString("role")) && msg.has("tool_calls")) {
                lastAssistantMsg = msg;
                lastAssistantIndex = i;
                break;
            }
        }
        
        JSONObject toolCallJson = new JSONObject();
        toolCallJson.put("id", toolId);
        toolCallJson.put("type", "function");
        
        JSONObject functionJson = new JSONObject();
        functionJson.put("name", functionName);
        functionJson.put("arguments", arguments.toString());
        toolCallJson.put("function", functionJson);
        
        if (lastAssistantMsg != null) {
            // 如果已经有 Assistant Message，将新的工具调用添加到现有的 tool_calls 数组中
            JSONArray existingToolCalls = lastAssistantMsg.getJSONArray("tool_calls");
            // 设置正确的 index（当前数组长度）
            toolCallJson.put("index", existingToolCalls.length());
            existingToolCalls.put(toolCallJson);
            Log.d(TAG, "已添加工具调用到现有 Assistant Message，index=" + (existingToolCalls.length() - 1) + "，当前 tool_calls 数量=" + existingToolCalls.length());
        } else {
            // 如果没有 Assistant Message，创建新的
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", "");
            
            JSONArray toolCalls = new JSONArray();
            toolCallJson.put("index", 0);
            toolCalls.put(toolCallJson);
            assistantMsg.put("tool_calls", toolCalls);
            messageHistory.put(assistantMsg);
            Log.d(TAG, "已创建新的 Assistant Message，包含工具调用，index=0");
        }
    }
    
    /**
     * 添加工具执行结果消息
     */

    public void addToolResultMessage(String toolId, String result) throws JSONException {
        JSONObject toolMsg = new JSONObject();
        toolMsg.put("role", "tool");
        toolMsg.put("content", result);
        toolMsg.put("tool_call_id", toolId);
        messageHistory.put(toolMsg);
        Log.d(TAG, "已添加工具执行结果消息");
    }
    
    /**
     * 追加文本内容
     */
    public void appendTextContent(String content) {
        if (content != null && !content.isEmpty()) {
            textContentBuilder.append(content);
        }
    }
    
    /**
     * 追加音频Base64数据
     */
    public void appendAudioBase64(String base64) {
        if (base64 != null && !base64.isEmpty()) {
            audioBase64Builder.append(base64);
        }
    }
    
    /**
     * 清空音频数据
     */
    public void clearAudioData() {
        audioBase64Builder.setLength(0);
    }
    
    /**
     * 获取消息历史
     */
    public JSONArray getMessageHistory() {
        return messageHistory;
    }
    
    /**
     * 获取完整文本内容
     */
    public String getTextContent() {
        return textContentBuilder.toString();
    }
    
    /**
     * 获取完整音频Base64数据
     */
    public String getAudioBase64() {
        return audioBase64Builder.toString();
    }
    
    /**
     * 检查是否有文本内容
     */
    public boolean hasTextContent() {
        return textContentBuilder.length() > 0;
    }
    
    /**
     * 检查是否有音频数据
     */
    public boolean hasAudioData() {
        return audioBase64Builder.length() > 0;
    }
    
    /**
     * 设置音频转录文本
     */
    public void setAudioTranscript(String transcript) {
        this.audioTranscript = transcript;
    }
    
    /**
     * 获取音频转录文本
     */
    public String getAudioTranscript() {
        return audioTranscript;
    }
    
    /**
     * 检查是否有音频转录文本
     */
    public boolean hasAudioTranscript() {
        return audioTranscript != null && !audioTranscript.isEmpty();
    }
}
