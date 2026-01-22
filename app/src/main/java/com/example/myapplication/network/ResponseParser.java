package com.example.myapplication.network;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * API响应解析器
 * 负责解析流式响应数据
 */
public class ResponseParser {
    private static final String TAG = "ResponseParser";

    /**
     * 解析响应行，提取文本内容
     */
    public static String parseTextContent(String responseLine) {
        try {
            String line = responseLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                return null;
            }

            String jsonStr = line.replaceFirst("^data:\\s*", "");
            if (jsonStr.startsWith("[") && jsonStr.contains("DONE")) {
                return null; // 流结束标记
            }

            if (jsonStr.startsWith("{")) {
                JSONObject responseJson = new JSONObject(jsonStr);
                JSONArray choices = responseJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject delta = firstChoice.optJSONObject("delta");
                    if (delta != null && delta.has("content")) {
                        return delta.optString("content", "");
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "解析文本内容失败", e);
        }
        return null;
    }

    /**
     * 解析响应行，提取音频Base64数据
     */
    public static String parseAudioBase64(String responseLine) {
        try {
            String line = responseLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                return null;
            }

            String jsonStr = line.replaceFirst("^data:\\s*", "");
            if (jsonStr.startsWith("{")) {
                JSONObject responseJson = new JSONObject(jsonStr);
                JSONArray choices = responseJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject delta = firstChoice.optJSONObject("delta");
                    if (delta != null && delta.has("audio")) {
                        JSONObject audioObj = delta.getJSONObject("audio");
                        String audioBase64 = audioObj.optString("data", "").trim();
                        if (!audioBase64.isEmpty()) {
                            audioBase64 = audioBase64.replaceAll("\\s+", "");
                            if (audioBase64.contains(",")) {
                                audioBase64 = audioBase64.split(",")[1];
                            }
                            return audioBase64;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "解析音频数据失败", e);
        }
        return null;
    }

    /**
     * 解析输入音频的ASR结果
     * 检查响应中是否有输入音频的识别结果字段
     */
    public static String parseInputAudioASR(String responseLine) {
        try {
            String line = responseLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                return null;
            }

            String jsonStr = line.replaceFirst("^data:\\s*", "");
            if (jsonStr.startsWith("{")) {
                JSONObject responseJson = new JSONObject(jsonStr);
                
                // 检查根级别的 input_audio_transcript 字段
                if (responseJson.has("input_audio_transcript")) {
                    String asr = responseJson.optString("input_audio_transcript", "").trim();
                    if (!asr.isEmpty()) {
                        return asr;
                    }
                }
                
                // 检查 choices[0].delta 中的 input_audio_transcript
                JSONArray choices = responseJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject delta = firstChoice.optJSONObject("delta");
                    if (delta != null) {
                        // 检查 input_audio_transcript
                        if (delta.has("input_audio_transcript")) {
                            String asr = delta.optString("input_audio_transcript", "").trim();
                            if (!asr.isEmpty()) {
                                return asr;
                            }
                        }
                        // 检查 input_transcript（可能的变体）
                        if (delta.has("input_transcript")) {
                            String asr = delta.optString("input_transcript", "").trim();
                            if (!asr.isEmpty()) {
                                return asr;
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "解析输入音频ASR失败", e);
        }
        return null;
    }

    /**
     * 解析响应行，提取音频转录文本（transcript）
     * 根据API文档，audio对象可能包含transcript字段（输出音频的转录）
     */
    public static String parseAudioTranscript(String responseLine) {
        try {
            String line = responseLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                return null;
            }

            String jsonStr = line.replaceFirst("^data:\\s*", "");
            if (jsonStr.startsWith("{")) {
                JSONObject responseJson = new JSONObject(jsonStr);
                JSONArray choices = responseJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject delta = firstChoice.optJSONObject("delta");
                    if (delta != null && delta.has("audio")) {
                        JSONObject audioObj = delta.getJSONObject("audio");
                        String transcript = audioObj.optString("transcript", "").trim();
                        if (!transcript.isEmpty()) {
                            return transcript;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "解析音频转录文本失败", e);
        }
        return null;
    }

    /**
     * 解析响应行，提取使用统计信息（usage）
     * 当stream_options.include_usage为true时，会在最后一个chunk中返回usage信息
     */
    public static JSONObject parseUsage(String responseLine) {
        try {
            String line = responseLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                return null;
            }

            String jsonStr = line.replaceFirst("^data:\\s*", "");
            if (jsonStr.startsWith("{")) {
                JSONObject responseJson = new JSONObject(jsonStr);
                if (responseJson.has("usage")) {
                    return responseJson.getJSONObject("usage");
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "解析使用统计信息失败", e);
        }
        return null;
    }

    /**
     * 检查是否是流结束标记
     */
    public static boolean isStreamEnd(String responseLine) {
        String line = responseLine.trim();
        if (line.startsWith("data:")) {
            String jsonStr = line.replaceFirst("^data:\\s*", "");
            return jsonStr.startsWith("[") && jsonStr.contains("DONE");
        }
        return false;
    }

    /**
     * 解析工具调用数据
     */
    public static JSONArray parseToolCalls(String responseLine) {
        try {
            String line = responseLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                return null;
            }

            String jsonStr = line.replaceFirst("^data:\\s*", "");
            if (jsonStr.startsWith("{")) {
                JSONObject responseJson = new JSONObject(jsonStr);
                JSONArray choices = responseJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject delta = firstChoice.optJSONObject("delta");
                    if (delta != null) {
                        return delta.optJSONArray("tool_calls");
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "解析工具调用失败", e);
        }
        return null;
    }
}


