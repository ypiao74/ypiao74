package com.example.myapplication.domain;

import android.util.Log;

import com.example.myapplication.data.MessageManager;
import com.example.myapplication.network.ResponseParser;
import com.example.myapplication.util.Base64Validator;

import org.json.JSONException;

/**
 * 响应处理器
 * 负责处理API响应并更新状态
 */
public class ResponseHandler {
    private static final String TAG = "ResponseHandler";
    
    private final MessageManager messageManager;
    
    public ResponseHandler(MessageManager messageManager) {
        this.messageManager = messageManager;
    }
    
    /**
     * 处理流式响应行
     * 
     * @param responseLine 响应行
     * @return 处理结果
     */
    public ResponseResult handleResponseLine(String responseLine) {
        if (responseLine == null || responseLine.trim().isEmpty()) {
            return ResponseResult.EMPTY;
        }
        
        String line = responseLine.trim();
        
        // 检查是否是流结束标记
        if (ResponseParser.isStreamEnd(line)) {
            Log.d(TAG, "流式响应已结束");
            return ResponseResult.STREAM_END;
        }
        
        // 解析文本内容
        String textContent = ResponseParser.parseTextContent(line);
        if (textContent != null && !textContent.isEmpty()) {
            if(textContent!=null) {
                messageManager.appendTextContent(textContent);
            }
            // 如果是第一条消息，添加到历史记录
            if (messageManager.getMessageHistory().length() == 0) {
                try {
                    messageManager.addUserMessage(textContent);
                } catch (JSONException e) {
                    Log.e(TAG, "添加用户消息失败", e);
                }
            }
            
            return ResponseResult.text(textContent);
        }
        
        // 解析音频数据
        String audioBase64 = ResponseParser.parseAudioBase64(line);
        if (audioBase64 != null && !audioBase64.isEmpty() && Base64Validator.isValid(audioBase64)) {
            messageManager.appendAudioBase64(audioBase64);
            return ResponseResult.audio(audioBase64);
        }
        
        // 解析音频转录（输出音频的转录文本）
        String transcript = ResponseParser.parseAudioTranscript(line);
        if (transcript != null && !transcript.isEmpty()) {
            messageManager.setAudioTranscript(transcript);
            return ResponseResult.transcript(transcript);
        }
        
        return ResponseResult.EMPTY;
    }
    
    /**
     * 响应处理结果
     */
    public static class ResponseResult {
        public static final ResponseResult EMPTY = new ResponseResult(Type.EMPTY, null);
        public static final ResponseResult STREAM_END = new ResponseResult(Type.STREAM_END, null);
        public static final ResponseResult AUDIO = new ResponseResult(Type.AUDIO, null);
        
        public enum Type {
            EMPTY,
            TEXT,
            AUDIO,
            TRANSCRIPT,
            STREAM_END
        }
        
        private final Type type;
        private final String content;
        
        private ResponseResult(Type type, String content) {
            this.type = type;
            this.content = content;
        }
        
        public static ResponseResult text(String content) {
            return new ResponseResult(Type.TEXT, content);
        }
        
        public static ResponseResult audio() {
            return AUDIO;
        }
        
        public static ResponseResult audio(String audioChunk) {
            return new ResponseResult(Type.AUDIO, audioChunk);
        }
        
        public static ResponseResult transcript(String transcript) {
            return new ResponseResult(Type.TRANSCRIPT, transcript);
        }
        
        public Type getType() {
            return type;
        }
        
        public String getContent() {
            return content;
        }
        
        public boolean hasContent() {
            return type != Type.EMPTY && type != Type.STREAM_END;
        }
    }
}
