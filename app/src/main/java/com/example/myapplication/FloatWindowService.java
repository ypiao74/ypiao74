package com.example.myapplication;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.myapplication.audio.AudioPlayer;
import com.example.myapplication.audio.AudioRecorder;
import com.example.myapplication.config.AppConfig;
import com.example.myapplication.network.QwenOmniClient;
import com.example.myapplication.network.model.QwenRequest;
import com.example.myapplication.util.Base64Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class FloatWindowService extends Service {
    private static final String TAG = "FloatWindowService";

    // 业务变量
    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;
    private AudioRecorder audioRecorder;
    private QwenOmniClient qwenOmniClient;
    private AudioPlayer audioPlayer;
    private AppConfig appConfig;
    private QwenOmniClient.CompleteToolCall savedCompleteToolCall = null;
    private JSONArray messageHistory;
    private StringBuilder audioBase64Builder;
    private StringBuilder textContentBuilder;
    private boolean isToolEnabled = true; // 工具启用状态

    // UI 控件
    private Button startBtn, stopBtn;
    private TextView resultTv;
    private Switch toolEnableSwitch;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initFloatWindow();
        initBusinessLogic();
        bindAllEvents();
    }

    /**
     * 初始化悬浮窗
     */
    private void initFloatWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.float_window, null);

        params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;
        params.format = PixelFormat.RGBA_8888;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        windowManager.addView(floatView, params);

        // 绑定控件
        startBtn = floatView.findViewById(R.id.btn_start_float);
        stopBtn = floatView.findViewById(R.id.btn_stop_float);
        resultTv = floatView.findViewById(R.id.tv_result_float);

        stopBtn.setEnabled(false);
        resultTv.setText("点击「开始录音」按钮，说出你的需求...");

    }

    /**
     * 初始化所有业务逻辑
     */
    private void initBusinessLogic() {
        messageHistory = new JSONArray();
        audioBase64Builder = new StringBuilder();
        textContentBuilder = new StringBuilder();

        initAudioPlayer();

        audioRecorder = new AudioRecorder(this);
        audioRecorder.setOnRecordListener(new AudioRecorder.OnRecordListener() {
            @Override
            public void onRecording() {
                updateUI(() -> {
                    resultTv.setText("录音中...（点击「停止录音」结束）");
                    audioBase64Builder.setLength(0);
                    textContentBuilder.setLength(0);
                    messageHistory = new JSONArray(); // 每次录音开始时重置历史
                    startBtn.setEnabled(false);
                    stopBtn.setEnabled(true);
                });
            }

            @Override
            public void onRecordComplete(File wavFile) {
                updateUI(() -> {
                    resultTv.setText("WAV文件生成完成，正在请求Qwen-Omni API...");
                    try {
                        String audioBase64 = Base64Util.fileToBase64(wavFile);
                        audioBase64 = audioBase64 == null ? "" : audioBase64;
                        // 修正：传入Context (this)
                        QwenRequest requestModel = new QwenRequest(FloatWindowService.this, audioBase64, false, isToolEnabled);
                        qwenOmniClient.callApi(wavFile);
                    } catch (JSONException e) {
                        Log.e(TAG, "创建请求失败", e);
                        resultTv.setText("请求创建失败：" + e.getMessage());
                        resetBtnState();
                    }
                });
            }

            @Override
            public void onRecordFailed(String errorMsg) {
                updateUI(() -> {
                    resultTv.setText("录音失败: " + errorMsg);
                    resetBtnState();
                });
            }
        });

        appConfig = new AppConfig(this);
        qwenOmniClient = new QwenOmniClient(this, appConfig);
        qwenOmniClient.setOnApiCallback(new QwenOmniClient.OnApiCallback() {
            @Override
            public void onSuccess(String responseLine) {
                updateUI(() -> {
                    try {
                        String line = responseLine.trim();
                        Log.i(TAG, "原始响应行: " + line); // 保留日志打印，方便调试
                        if (line.isEmpty()) return;

                        if (line.startsWith("data:")) {
                            String jsonStr = line.replaceFirst("^data:\\s*", "");
                            if (jsonStr.startsWith("[") && jsonStr.contains("DONE")) {
                                Log.d(TAG, "流式响应已结束（DONE标识）");
                                updateFinalResponse();
                                return;
                            }
                            if (jsonStr.startsWith("{")) {
                                JSONObject responseJson = new JSONObject(jsonStr);
                                JSONArray choices = responseJson.optJSONArray("choices");
                                if (choices != null && choices.length() > 0) {
                                    JSONObject firstChoice = choices.getJSONObject(0);
                                    JSONObject delta = firstChoice.optJSONObject("delta");
                                    if (delta != null) {
                                        // ==================== 新增的逻辑 ====================
                                        if (delta.has("content")) {
                                            String content = delta.optString("content", "").trim();
                                            if (!content.isEmpty()) {
                                                textContentBuilder.append(content);

                                                // 如果 messageHistory 为空，则创建一个 user 消息并添加进去
                                                // 这通常发生在第一次收到文本响应时
                                                if (messageHistory.length() == 0) {
                                                    JSONObject userMsg = new JSONObject();
                                                    userMsg.put("role", "user");
                                                    userMsg.put("content", content); // 注意：这里的 content 是模型返回的文本
                                                    messageHistory.put(userMsg);
                                                    Log.d(TAG, "初始化 messageHistory 并添加 user 消息: " + userMsg);
                                                }

                                                resultTv.setText("Qwen-Omni响应:\n" + textContentBuilder.toString() + "\n\n正在接收音频...");
                                            }
                                        }
                                        // =====================================================

                                        if (delta.has("audio")) {
                                            JSONObject audioObj = delta.getJSONObject("audio");
                                            String audioBase64 = audioObj.optString("data", "").trim();
                                            if (!audioBase64.isEmpty()) {
                                                audioBase64 = audioBase64.replaceAll("\\s+", "");
                                                if (audioBase64.contains(",")) {
                                                    audioBase64 = audioBase64.split(",")[1];
                                                }
                                                if (isValidBase64(audioBase64)) {
                                                    audioBase64Builder.append(audioBase64);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        resultTv.setText("JSON解析失败:\n" + e.getMessage());
                        Log.e(TAG, "JSON解析错误", e);
                    }
                });
            }

            @Override
            public void onToolCallComplete(QwenOmniClient.CompleteToolCall completeToolCall) {
                if (completeToolCall != null) {
                    Log.d(TAG, "工具调用拼接完成: " + completeToolCall);
                    updateUI(() -> resultTv.append("\n工具调用拼接完成: " + completeToolCall.functionName));
                    savedCompleteToolCall = completeToolCall;
                    audioBase64Builder.setLength(0);

                    String toolOutput = executeTool(completeToolCall);
                    try {
                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", "");
                        JSONArray toolCalls = new JSONArray();
                        JSONObject toolCallJson = new JSONObject();
                        toolCallJson.put("id", completeToolCall.toolId);
                        toolCallJson.put("type", "function");
                        JSONObject functionJson = new JSONObject();
                        functionJson.put("name", completeToolCall.functionName);
                        functionJson.put("arguments", completeToolCall.arguments.toString());
                        toolCallJson.put("function", functionJson);
                        toolCallJson.put("index", 0);
                        toolCalls.put(toolCallJson);
                        assistantMsg.put("tool_calls", toolCalls);
                        messageHistory.put(assistantMsg);

                        JSONObject toolMsg = new JSONObject();
                        toolMsg.put("role", "tool");
                        toolMsg.put("content", toolOutput);
                        toolMsg.put("tool_call_id", completeToolCall.toolId);
                        messageHistory.put(toolMsg);

                        updateUI(() -> resultTv.setText("工具执行完成，发起第二次模型调用..."));
                        qwenOmniClient.setMessages(messageHistory);
                        // 工具回调请求：传入 isToolCallbackRequest=true，让模型根据情况决定是否再次调用工具
                        QwenRequest requestModel = new QwenRequest(FloatWindowService.this, messageHistory, true, isToolEnabled, true);
                       // qwenOmniClient.callApi(null);
                    } catch (JSONException e) {
                        Log.e(TAG, "构建二次调用上下文失败", e);
                        updateUI(() -> resultTv.append("\n构建上下文失败: " + e.getMessage()));
                    }
                }
            }

            @Override
            public void onSummaryComplete(String summary) {

            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                updateUI(() -> {
                    resultTv.setText("API请求失败: 错误码=" + errorCode + ", " + errorMsg);
                    resetBtnState();
                });
            }


            @Override
            public void onError(Throwable throwable) {
                updateUI(() -> {
                    resultTv.setText("API调用异常: " + (throwable != null ? throwable.getMessage() : "未知异常"));
                    resetBtnState();
                });
            }
        });
    }

    /**
     * 初始化音频播放器
     */
    private void initAudioPlayer() {
        audioPlayer = new AudioPlayer();
        audioPlayer.setOnAudioPlayListener(new AudioPlayer.OnAudioPlayListener() {
            @Override
            public void onAudioPlayStart() {
                updateUI(() -> resultTv.append("\n🎵 语音响应开始播放..."));
            }

            @Override
            public void onAudioPlayComplete() {
                updateUI(() -> {
                    resultTv.append("\n🎵 语音响应播放完成");
                    resetBtnState();
                });
            }

            @Override
            public void onAudioPlayError(String error) {
                updateUI(() -> resultTv.append("\n❌ 音频播放错误: " + error));
                resetBtnState();
            }

            @Override
            public void onAudioSaved(File audioFile) {
                Log.d(TAG, "临时音频文件已保存: " + audioFile.getAbsolutePath());
            }
        });
    }

    /**
     * 绑定所有控件事件
     */
    private void bindAllEvents() {
        startBtn.setOnClickListener(v -> checkRecordPermissionAndStart());

        stopBtn.setOnClickListener(v -> {
            if (audioRecorder != null) {
                audioRecorder.stopRecording();
            }
            updateUI(() -> {
                resultTv.setText("⏹️ 录音已停止，正在处理...");
                // resetBtnState(); // 停止录音后，按钮状态由 onRecordComplete 或 onRecordFailed 重置
            });
        });

        // 新增：工具启用开关事件


        // 悬浮窗拖动逻辑
        floatView.setOnTouchListener(new View.OnTouchListener() {
            private int lastX, lastY;
            private float downX, downY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = params.x;
                        lastY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        // 只有当移动距离足够大时才认为是拖动
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            params.x = lastX + dx;
                            params.y = lastY + dy;
                            windowManager.updateViewLayout(floatView, params);
                            isDragging = true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        // 如果不是拖动，则触发点击事件
                        if (!isDragging) {
                            v.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /**
     * 录音权限检查
     */
    private void checkRecordPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请授予录音权限以继续使用", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            startRecording();
        }
    }

    /**
     * 开始录音
     */
    private void startRecording() {
        if (audioRecorder != null && !audioRecorder.isRecording()) {
            boolean isStarted = audioRecorder.startRecording();
            if (!isStarted) {
                updateUI(() -> resultTv.setText("❌ 录音启动失败，请重试"));
            }
        }
    }

    /**
     * 工具执行逻辑
     * 修正：从 content 对象中获取 operation
     */
    private String executeTool(QwenOmniClient.CompleteToolCall toolCall) {

            return "已经完成对应的操作，你需要回复已经完成对应操作" ;

    }

    /**
     * 更新最终响应
     * 保留之前的显示内容，只更新文本部分
     */
    private void updateFinalResponse() {
        updateUI(() -> {
            String finalText = textContentBuilder.length() > 0
                    ? textContentBuilder.toString()
                    : "已成功执行工具操作。";
            
            // 构建完整的显示内容，保留之前的提示信息
            StringBuilder displayText = new StringBuilder();
            displayText.append("Qwen-Omni响应:\n");
            displayText.append(finalText);

            if (audioBase64Builder.length() > 0) {
                try {
                    String pureBase64 = processBase64(audioBase64Builder.toString());
                    byte[] audioBytes = Base64.decode(pureBase64, Base64.DEFAULT);
                    if (audioBytes.length < 1024) {
                        displayText.append("\n❌ 音频数据无效（长度过短）");
                        resultTv.setText(displayText.toString());
                        resetBtnState();
                        return;
                    }
                    // 显示完整内容并开始播放音频
                    displayText.append("\n\n正在播放语音响应...");
                    resultTv.setText(displayText.toString());
                    audioPlayer.playAudioFromBase64(pureBase64);
                } catch (IllegalArgumentException e) {
                    displayText.append("\n❌ 音频解码失败: " + e.getMessage());
                    resultTv.setText(displayText.toString());
                    Log.e(TAG, "Base64解码错误", e);
                    resetBtnState();
                }
            } else {
                displayText.append("\n无语音反馈。");
                resultTv.setText(displayText.toString());
                resetBtnState();
            }
        });
    }

    /**
     * Base64处理
     */
    private String processBase64(String base64) {
        String pureBase64 = base64.replaceAll("\\s+", "")
                .replaceAll("[^A-Za-z0-9+/=]", "");
        int remainder = pureBase64.length() % 4;
        if (remainder != 0) {
            StringBuilder padBuilder = new StringBuilder(pureBase64);
            for (int i = 0; i < 4 - remainder; i++) {
                padBuilder.append("=");
            }
            pureBase64 = padBuilder.toString();
        }
        return pureBase64;
    }

    /**
     * Base64验证
     */
    private boolean isValidBase64(String base64) {
        String regex = "^[A-Za-z0-9+/=]*$";
        return base64.matches(regex);
    }

    /**
     * 重置按钮状态
     */
    private void resetBtnState() {
        updateUI(() -> {
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        });
    }

    /**
     * UI更新工具
     */
    private void updateUI(Runnable action) {
        if (mainHandler != null && Looper.myLooper() != mainHandler.getLooper()) {
            mainHandler.post(action);
        } else if (action != null) {
            action.run();
        }
    }

    /**
     * 释放资源
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
            audioRecorder = null;
        }
        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer = null;
        }
        if (floatView != null && windowManager != null) {
            try {
                windowManager.removeView(floatView);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "移除悬浮窗失败", e);
            }
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }
    }
}