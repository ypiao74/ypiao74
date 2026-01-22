package com.example.myapplication.viewmodel;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.myapplication.audio.AudioPlayer;
import com.example.myapplication.audio.AudioRecorder;
import com.example.myapplication.audio.StreamingAudioPlayer;
import com.example.myapplication.config.AppConfig;
import com.example.myapplication.config.AppConstants;
import com.example.myapplication.data.MessageManager;
import com.example.myapplication.domain.ResponseHandler;
import com.example.myapplication.domain.ToolExecutor;
import com.example.myapplication.network.QwenOmniClient;
import com.example.myapplication.network.ResponseParser;
import com.example.myapplication.network.model.QwenRequest;
import com.example.myapplication.util.Base64Util;
import com.example.myapplication.util.Base64Validator;
import com.example.myapplication.util.ImageUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Qwen功能ViewModel (MVVM核心层)
 * 【完全迁移原QwenPresenter的所有业务逻辑，一行未改】
 * 替代原MVP的Presenter，负责处理所有业务逻辑，与View层通过LiveData解耦
 * 生命周期跟随Activity/Fragment，自动管理资源，无内存泄漏风险
 */
public class QwenViewModel extends AndroidViewModel {
    private static final String TAG = "QwenViewModel";

    private final Context context;
    private final Handler mainHandler;

    // 业务组件 【原封不动迁移】
    private AudioRecorder audioRecorder;
    private QwenOmniClient qwenOmniClient;
    private AudioPlayer audioPlayer;
    private StreamingAudioPlayer streamingAudioPlayer;

    // 数据管理 【原封不动迁移】
    private final MessageManager messageManager;
    private final ResponseHandler responseHandler;
    private final ToolExecutor toolExecutor;

    // 配置 【原封不动迁移】
    private final AppConfig appConfig;
    private boolean isToolEnabled = true;
    private String audioVoice = "Cherry"; // 默认发音人

    // 工具调用处理相关 【原封不动迁移】
    private int pendingToolCallCount = 0;
    private boolean isSecondCallPending = false;
    private StringBuilder toolCallResultsDisplay = new StringBuilder();

    // 请求取消标志 【原封不动迁移】
    private volatile boolean isRequestCancelled = false;

    // ======================== 你的原时间统计变量【一行未改，全部迁移】 ========================
    private long startRequestTime;
    private long firstResponseTime;
    private long firstTextTime;
    private long firstAudioTime;
    private long firstToolCallTime;
    private boolean isFirstTextReceived = false;
    private boolean isFirstAudioReceived = false;
    private boolean isFirstToolCallReceived = false;
    private boolean isRequestTimeShowed = false;
    private boolean isTextAudioDiffCalculated = false;
    private long secondCallStartTime;
    private long secondCallFirstTextTime;
    private long secondCallFirstAudioTime;
    private boolean isSecondCallStarted = false;
    private boolean isSecondCallFirstTextReceived = false;
    private boolean isSecondCallFirstAudioReceived = false;
    private long audioPlayStartTime;
    private boolean isAudioPlayStarted = false;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private final StringBuilder timeStatSb = new StringBuilder();

    // ======================== MVVM核心：LiveData 替代 MVP的View接口回调【唯一新增的核心】 ========================
    // View层订阅这些LiveData，自动接收数据更新，无需接口回调
    public MutableLiveData<String> resultLiveData = new MutableLiveData<>(); // 结果文本更新
    public MutableLiveData<Boolean> startButtonEnable = new MutableLiveData<>(true); // 开始按钮状态
    public MutableLiveData<Boolean> stopButtonEnable = new MutableLiveData<>(false); // 停止按钮状态

    public QwenViewModel(@NonNull Application application) {
        super(application);
        this.context = application.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.appConfig = new AppConfig(context);

        this.messageManager = new MessageManager();
        this.responseHandler = new ResponseHandler(messageManager);
        this.toolExecutor = new ToolExecutor();

        initializeComponents();
    }

    private void initializeComponents() {
        initAudioPlayer();
        initStreamingAudioPlayer();
        initAudioRecorder();
        initQwenClient();
    }

    private void initAudioPlayer() {
        audioPlayer = new AudioPlayer();
        audioPlayer.setOnAudioPlayListener(new AudioPlayer.OnAudioPlayListener() {
            @Override
            public void onAudioPlayStart() {
                postResult(AppConstants.MSG_AUDIO_PLAY_START);
            }

            @Override
            public void onAudioPlayComplete() {
                postResult(AppConstants.MSG_AUDIO_PLAY_COMPLETE);
                resetButtonState();
                resetSecondCallMark();
            }

            @Override
            public void onAudioPlayError(String error) {
                postResult(String.format(AppConstants.MSG_AUDIO_PLAY_ERROR, error));
                resetButtonState();
                resetSecondCallMark();
            }

            @Override
            public void onAudioSaved(File audioFile) {
                Log.d(TAG, "临时音频文件已保存: " + audioFile.getAbsolutePath());
            }
        });
    }

    private void initStreamingAudioPlayer() {
        streamingAudioPlayer = new StreamingAudioPlayer();
        streamingAudioPlayer.setOnStreamingAudioListener(new StreamingAudioPlayer.OnStreamingAudioListener() {
            @Override
            public void onPlayStart() {

            }

            @Override
            public void onPlayComplete() {
                resetButtonState();
                resetSecondCallMark();
            }

            @Override
            public void onPlayError(String error) {
                postResult("\n❌ 流式音频播放错误: " + error);
                resetButtonState();
                resetSecondCallMark();
            }

            @Override
            public void onChunkReceived(int chunkSize) {}
        });
    }

    private void initAudioRecorder() {
        audioRecorder = new AudioRecorder(context);
        audioRecorder.setOnRecordListener(new AudioRecorder.OnRecordListener() {
            @Override
            public void onRecording() {
                postResult(AppConstants.MSG_RECORDING);
                messageManager.reset();
                if (streamingAudioPlayer != null && streamingAudioPlayer.isPlaying()) {
                    streamingAudioPlayer.stop();
                }
                enableStartButton(false);
                enableStopButton(true);
                resetTimeMark();
                pendingToolCallCount = 0;
                isSecondCallPending = false;
                toolCallResultsDisplay.setLength(0);
                isRequestCancelled = false;
            }

            @Override
            public void onRecordComplete(File wavFile) {
                postResult(AppConstants.MSG_PROCESSING);
                handleRecordComplete(wavFile);
            }

            @Override
            public void onRecordFailed(String errorMsg) {
                postResult(String.format(AppConstants.MSG_RECORD_FAILED, errorMsg));
                resetButtonState();
            }
        });
    }

    private void initQwenClient() {
        qwenOmniClient = new QwenOmniClient(context, appConfig);
        qwenOmniClient.setToolEnabled(isToolEnabled);
        qwenOmniClient.setAudioVoice(audioVoice);
        qwenOmniClient.setOnApiCallback(new QwenOmniClient.OnApiCallback() {
            @Override
            public void onSuccess(String responseLine) {
                if (firstResponseTime == 0) {
                    firstResponseTime = System.currentTimeMillis();
                    Log.d(TAG, "第一次响应到达，耗时: " + (firstResponseTime - startRequestTime) + "ms");
                }
                if (qwenOmniClient.hasToolCallDetected()) {
                    String audioBase64 = ResponseParser.parseAudioBase64(responseLine);
                    if (audioBase64 != null && !audioBase64.isEmpty()) {
                        Log.d(TAG, "检测到工具调用，跳过音频数据处理");
                        return;
                    }
                }
                handleApiSuccess(responseLine);
            }

            @Override
            public void onToolCallComplete(QwenOmniClient.CompleteToolCall completeToolCall) {
                if (!isFirstToolCallReceived && completeToolCall != null) {
                    firstToolCallTime = System.currentTimeMillis();
                    if (firstResponseTime > 0) {
                        long toolCost = firstToolCallTime - firstResponseTime;
                        String toolText = "[⏱️] 工具调用响应处理耗时: " + toolCost / 1000.0 + " 秒";
                        Log.d(TAG, toolText);
                        timeStatSb.append(toolText).append("\n");
                    } else {
                        long toolCost = firstToolCallTime - startRequestTime;
                        String toolText = "[⏱️] 首个工具调用到达耗时: " + toolCost / 1000.0 + " 秒";
                        Log.d(TAG, toolText);
                        timeStatSb.append(toolText).append("\n");
                    }
                    isFirstToolCallReceived = true;
                    refreshTimeStatUI();
                }
                handleToolCallComplete(completeToolCall);
            }

            @Override
            public void onSummaryComplete(String summary) {
                handleSummaryComplete(summary);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                long failCost = System.currentTimeMillis() - startRequestTime;
                String errorText = String.format(AppConstants.MSG_API_FAILED, errorCode, errorMsg) + "\n[⏱️] 请求失败耗时: " + failCost / 1000.0 + " 秒";
                Log.e(TAG, errorText);
                postResult(errorText);
                resetButtonState();
            }

            @Override
            public void onError(Throwable throwable) {
                long errorCost = System.currentTimeMillis() - startRequestTime;
                String errorMsg = throwable != null ? throwable.getMessage() : "未知异常";
                String errorText = String.format(AppConstants.MSG_API_ERROR, errorMsg) + "\n[⏱️] 请求异常耗时: " + errorCost / 1000.0 + " 秒";
                Log.e(TAG, errorText);
                postResult(errorText);
                resetButtonState();
            }
        });
    }

    // ======================== 以下所有方法：完全复制你的原代码，无任何修改 ========================
    private void handleRecordComplete(File wavFile) {
        cancelCurrentRequestAndStopAudio();
        isRequestCancelled = false;
        try {
            String audioBase64 = Base64Util.fileToBase64(wavFile);
            audioBase64 = audioBase64 == null ? "" : audioBase64;

            QwenRequest requestModel = new QwenRequest(
                    context,
                    audioBase64,
                    false,
                    isToolEnabled
            );
            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送请求... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                postResult(timeStatSb.toString());
                isRequestTimeShowed = true;
            }
            qwenOmniClient.callApi(wavFile);
        } catch (JSONException e) {
            Log.e(TAG, "创建请求失败", e);
            postResult("请求创建失败：" + e.getMessage());
        }
    }

    private void handleApiSuccess(String responseLine) {
        String line = responseLine.trim();
        Log.i(TAG, "原始响应行(trim后): " + line);
        ResponseHandler.ResponseResult result = responseHandler.handleResponseLine(responseLine);
        if (result.getType() == ResponseHandler.ResponseResult.Type.STREAM_END) {
            long totalCost = System.currentTimeMillis() - startRequestTime;
            String totalText = "[✅] 流式响应完成，总耗时: " + totalCost / 1000.0 + " 秒";
            Log.d(TAG, totalText);
            timeStatSb.append(totalText).append("\n");
            calculateTextAudioDiff();
            printTimeStatistics(totalCost);
            updateFinalResponse();
            return;
        }

        if (result.getType() == ResponseHandler.ResponseResult.Type.TEXT) {
            if (isSecondCallStarted && !isSecondCallFirstTextReceived) {
                firstTextTime = System.currentTimeMillis();
                secondCallFirstTextTime = System.currentTimeMillis();
                long secondTextCost = secondCallFirstTextTime - secondCallStartTime;
                String secondTextTime = "[⏱️] 二次调用文本到达耗时: " + secondTextCost / 1000.0 + " 秒";
                Log.d(TAG, secondTextTime);
                timeStatSb.append(secondTextTime).append("\n");
                isSecondCallFirstTextReceived = true;
                refreshTimeStatUI();
            } else if (!isFirstTextReceived) {
                firstTextTime = System.currentTimeMillis();
                long textCost = firstTextTime - startRequestTime;
                String textTime = "[⏱️] 首个文本到达耗时: " + textCost / 1000.0 + " 秒";
                Log.d(TAG, textTime);
                timeStatSb.append(textTime).append("\n");
                isFirstTextReceived = true;
                calculateTextAudioDiff();
            }
            String text = messageManager.getTextContent();
            String toolCallInfo = toolCallResultsDisplay.length() > 0 ? toolCallResultsDisplay.toString() + "\n" : "";
            String showText = timeStatSb.toString() + toolCallInfo + "Qwen-Omni响应:\n" + text + "\n\n正在接收音频...";
            postResult(showText);
        }

        if (result.getType() == ResponseHandler.ResponseResult.Type.AUDIO) {
            if (isSecondCallStarted && !isSecondCallFirstAudioReceived) {
                secondCallFirstAudioTime = System.currentTimeMillis();
                long secondAudioCost = secondCallFirstAudioTime - secondCallStartTime;
                String secondAudioTime = "[⏱️] 二次调用音频到达耗时: " + secondAudioCost / 1000.0 + " 秒";
                Log.d(TAG, secondAudioTime);
                timeStatSb.append(secondAudioTime).append("\n");
                isSecondCallFirstAudioReceived = true;
                refreshTimeStatUI();
            } else if (!isFirstAudioReceived) {
                firstAudioTime = System.currentTimeMillis();
                long audioCost = firstAudioTime - startRequestTime;
                String audioTime = "[⏱️] 首个音频到达耗时: " + audioCost / 1000.0 + " 秒";
                Log.d(TAG, audioTime);
                timeStatSb.append(audioTime).append("\n");
                isFirstAudioReceived = true;
                calculateTextAudioDiff();
            }
            String audioChunk = result.getContent();
            if (audioChunk != null && !audioChunk.isEmpty()) {
                if (isRequestCancelled) {
                    Log.d(TAG, "请求已被取消，忽略音频数据");
                    return;
                }
                if (!streamingAudioPlayer.isPlaying()) {
                    streamingAudioPlayer.start();
                }
                streamingAudioPlayer.appendAudioChunk(audioChunk);
            }
        }
    }

    private void calculateTextAudioDiff() {
        if (isFirstTextReceived && isFirstAudioReceived && !isTextAudioDiffCalculated) {
            double diffSeconds = (firstAudioTime - firstTextTime) / 1000.0;
            String diffDesc = diffSeconds > 0 ? "(音频晚)" : "(音频早)";
            String diffText = String.format("[⏱️] 文音时间差: %+.2f 秒 %s", diffSeconds, diffDesc);
            Log.d(TAG, diffText);
            timeStatSb.append(diffText).append("\n");
            isTextAudioDiffCalculated = true;
            refreshTimeStatUI();
        }
    }

    private void refreshTimeStatUI() {
        String currentResult = resultLiveData.getValue() != null ? resultLiveData.getValue() : "";
        int contentStartIndex = currentResult.indexOf("Qwen-Omni响应:");
        if (contentStartIndex != -1) {
            String contentPart = currentResult.substring(contentStartIndex);
            postResult(timeStatSb.toString() + contentPart);
        } else {
            postResult(timeStatSb.toString());
        }
    }

    private void handleToolCallComplete(QwenOmniClient.CompleteToolCall completeToolCall) {
        if (completeToolCall == null) return;
        Log.d(TAG, "工具调用拼接完成: " + completeToolCall);
        messageManager.clearAudioData();
        String toolOutput = toolExecutor.execute(completeToolCall);
        String toolResultDisplay = formatToolCallResult(completeToolCall, toolOutput);
        toolCallResultsDisplay.append(toolResultDisplay).append("\n");
        postResult(toolResultDisplay);

        try {
            messageManager.addAssistantToolCallMessage(
                    completeToolCall.toolId,
                    completeToolCall.functionName,
                    completeToolCall.arguments
            );
            messageManager.addToolResultMessage(completeToolCall.toolId, toolOutput);
            pendingToolCallCount++;
            Log.d(TAG, "已处理工具调用: " + completeToolCall.functionName + "，当前待处理数量=" + pendingToolCallCount);
            mainHandler.postDelayed(() -> checkAndTriggerSecondCall(), 50);
        } catch (JSONException e) {
            Log.e(TAG, "构建工具调用消息失败", e);
            postResult("\n构建工具调用消息失败: " + e.getMessage());
        }
    }

    private String formatToolCallResult(QwenOmniClient.CompleteToolCall completeToolCall, String toolOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[工具调用] ").append(completeToolCall.functionName);
        if (completeToolCall.arguments != null && completeToolCall.arguments.length() > 0) {
            sb.append(" 参数: ").append(completeToolCall.arguments.toString());
        }
        return sb.toString();
    }

    private void checkAndTriggerSecondCall() {
        if (pendingToolCallCount == 0) return;
        if (isSecondCallPending) {
            Log.d(TAG, "二次调用已在等待中，跳过");
            return;
        }
        isSecondCallPending = true;
        int currentPendingCount = pendingToolCallCount;
        pendingToolCallCount = 0;

        isSecondCallStarted = true;
        isSecondCallFirstTextReceived = false;
        isSecondCallFirstAudioReceived = false;
        Log.d(TAG, "二次调用开始，时间: " + secondCallStartTime);

        try {
            JSONArray completeMessages = new JSONArray();
            JSONArray initialMessages = qwenOmniClient.getMessages();
            if (initialMessages != null) {
                for (int i = 0; i < initialMessages.length(); i++) {
                    completeMessages.put(initialMessages.get(i));
                }
                Log.d(TAG, "已添加初始消息（System + User），数量=" + initialMessages.length());
            }
            JSONArray toolMessages = messageManager.getMessageHistory();
            if (toolMessages != null) {
                for (int i = 0; i < toolMessages.length(); i++) {
                    JSONObject msg = toolMessages.getJSONObject(i);
                    String role = msg.optString("role", "");
                    if ("assistant".equals(role) || "tool".equals(role)) {
                        completeMessages.put(msg);
                    }
                }
                Log.d(TAG, "已添加工具相关消息（Assistant + Tool），数量=" + toolMessages.length());
            }
            Log.d(TAG, "二次调用完整消息历史长度=" + completeMessages.length());
            qwenOmniClient.setMessages(completeMessages);
            String toolCallInfo = toolCallResultsDisplay.length() > 0 ? toolCallResultsDisplay.toString() + "\n" : "";
            postResult(timeStatSb.toString() + toolCallInfo + AppConstants.MSG_TOOL_EXECUTE_COMPLETE);
            secondCallStartTime = System.currentTimeMillis();
            qwenOmniClient.callApi(null);
            isSecondCallPending = false;
        } catch (JSONException e) {
            Log.e(TAG, "构建二次调用上下文失败", e);
            postResult("\n构建上下文失败: " + e.getMessage());
            isSecondCallPending = false;
        }
    }

    private void handleSummaryComplete(String summary) {
        String toolCallInfo = toolCallResultsDisplay.length() > 0 ? toolCallResultsDisplay.toString() + "\n" : "";
        String showText = timeStatSb.toString() + toolCallInfo + "Qwen-Omni 自然语言总结:\n" + summary;
        if (messageManager.hasAudioData()) {
            showText += "\n\n正在播放语音响应...";
            postResult(showText);
            updateFinalResponse();
        } else {
            postResult(showText);
            resetButtonState();
        }
    }

    private void updateFinalResponse() {
        String finalText = messageManager.hasTextContent()
                ? messageManager.getTextContent()
                : "已成功执行工具操作。";

        StringBuilder displayText = new StringBuilder();
        displayText.append(timeStatSb.toString());
        if (toolCallResultsDisplay.length() > 0) {
            displayText.append(toolCallResultsDisplay.toString()).append("\n");
        }
        displayText.append("Qwen-Omni响应:\n");
        displayText.append(finalText);

        if (streamingAudioPlayer != null && streamingAudioPlayer.isPlaying()) {
            displayText.append("\n\n流式音频播放中...");
            postResult(displayText.toString());
        } else if (messageManager.hasAudioData()) {
            try {
                String pureBase64 = Base64Validator.normalize(messageManager.getAudioBase64());
                byte[] audioBytes = Base64.decode(pureBase64, Base64.DEFAULT);

                if (audioBytes.length < AppConstants.MIN_AUDIO_BYTES) {
                    displayText.append("\n").append(AppConstants.MSG_AUDIO_INVALID);
                    postResult(displayText.toString());
                    resetButtonState();
                    return;
                }

                displayText.append("\n\n正在播放语音响应...");
                postResult(displayText.toString());

                if (isSecondCallStarted && !isAudioPlayStarted) {
                    audioPlayStartTime = System.currentTimeMillis();
                    isAudioPlayStarted = true;
                    long totalTime = audioPlayStartTime - startRequestTime;
                    String totalTimeText = "[⏱️] 从首次请求到二次调用音频播放开始: " + totalTime / 1000.0 + " 秒";
                    Log.d(TAG, totalTimeText);
                    timeStatSb.append(totalTimeText).append("\n");
                    refreshTimeStatUI();
                }
                audioPlayer.playAudioFromBase64(pureBase64);
            } catch (IllegalArgumentException e) {
                displayText.append("\n").append(String.format(AppConstants.MSG_AUDIO_DECODE_FAILED, e.getMessage()));
                postResult(displayText.toString());
                Log.e(TAG, "Base64解码错误", e);
                resetButtonState();
            }
        } else {
            displayText.append("\n").append(AppConstants.MSG_NO_AUDIO_RESPONSE);
            postResult(displayText.toString());
            resetButtonState();
        }
    }

    private void resetTimeMark() {
        startRequestTime = 0;
        firstResponseTime = 0;
        firstTextTime = 0;
        firstAudioTime = 0;
        isRequestCancelled = false;
        firstToolCallTime = 0;
        isFirstTextReceived = false;
        isFirstAudioReceived = false;
        isFirstToolCallReceived = false;
        isRequestTimeShowed = false;
        isTextAudioDiffCalculated = false;
        secondCallStartTime = 0;
        secondCallFirstTextTime = 0;
        secondCallFirstAudioTime = 0;
        isSecondCallStarted = false;
        isSecondCallFirstTextReceived = false;
        isSecondCallFirstAudioReceived = false;
        audioPlayStartTime = 0;
        isAudioPlayStarted = false;
        timeStatSb.setLength(0);
    }

    private void printTimeStatistics(long totalCost) {
        Log.d(TAG, "\n========================================");
        Log.d(TAG, "📊 性能统计");
        Log.d(TAG, "========================================");
        if (firstTextTime > 0) {
            Log.d(TAG, String.format("🔤 首包文本 (TTFT-text):   %.2fs", (firstTextTime - startRequestTime) / 1000.0));
        }
        if (firstAudioTime > 0) {
            Log.d(TAG, String.format("🎵 首包音频 (TTFT-audio):  %.2fs", (firstAudioTime - startRequestTime) / 1000.0));
        }
        if (firstToolCallTime > 0) {
            Log.d(TAG, String.format("🛠️  首包工具调用 (TTFT-tool): %.2fs", (firstToolCallTime - startRequestTime) / 1000.0));
        }
        if (isTextAudioDiffCalculated) {
            double diff = (firstAudioTime - firstTextTime) / 1000.0;
            Log.d(TAG, String.format("🔁 文音时间差:           %+.2fs %s", diff, diff > 0 ? "(音频晚)" : "(音频早)"));
        }
        Log.d(TAG, String.format("⏱️  总响应时间:           %.2fs", totalCost / 1000.0));
        Log.d(TAG, "========================================\n");

        StringBuilder statSb = new StringBuilder();
        statSb.append("\n========================================\n");
        statSb.append("📊 性能统计\n");
        statSb.append("========================================\n");
        if (firstTextTime > 0) {
            statSb.append(String.format("🔤 首包文本 (TTFT-text):   %.2fs\n", (firstTextTime - startRequestTime) / 1000.0));
        }
        if (firstAudioTime > 0) {
            statSb.append(String.format("🎵 首包音频 (TTFT-audio):  %.2fs\n", (firstAudioTime - startRequestTime) / 1000.0));
        }
        if (firstToolCallTime > 0) {
            statSb.append(String.format("🛠️  首包工具调用 (TTFT-tool): %.2fs\n", (firstToolCallTime - startRequestTime) / 1000.0));
        }
        if (isTextAudioDiffCalculated) {
            double diff = (firstAudioTime - firstTextTime) / 1000.0;
            statSb.append(String.format("🔁 文音时间差:           %+.2fs %s\n", diff, diff > 0 ? "(音频晚)" : "(音频早)"));
        }
        statSb.append(String.format("⏱️  总响应时间:           %.2fs\n", totalCost / 1000.0));
        statSb.append("========================================\n");
        appendResult(statSb.toString());
    }

    private void resetSecondCallMark() {
        isSecondCallStarted = false;
        isSecondCallFirstTextReceived = false;
        isSecondCallFirstAudioReceived = false;
        isAudioPlayStarted = false;
    }

    // ======================== 对外暴露的业务方法 【原封不动迁移】 ========================
    public void startRecording() {
        if (audioRecorder != null && !audioRecorder.isRecording()) {
            audioRecorder.startRecording();
        }
    }

    public void stopRecording() {
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
            resetButtonState();
        }
    }

    public void setToolEnabled(boolean enabled) {
        this.isToolEnabled = enabled;
        if (qwenOmniClient != null) {
            qwenOmniClient.setToolEnabled(enabled);
        }
    }

    public void sendImageRequest(File imageFile, String textContent) {
        cancelCurrentRequestAndStopAudio();
        if (imageFile == null || !imageFile.exists()) {
            postResult("❌ 图片文件不存在");
            return;
        }
        ImageUtil.ImageValidationResult validation = ImageUtil.validateImageForApi(imageFile);
        if (!validation.isValid()) {
            postResult("❌ " + validation.getMessage());
            return;
        }
        try {
            resetTimeMark();
            messageManager.reset();
            postResult(AppConstants.MSG_IMAGE_PROCESSING);
            String imageBase64 = Base64Util.fileToBase64(imageFile);
            if (imageBase64 == null || imageBase64.isEmpty()) {
                postResult(String.format(AppConstants.MSG_IMAGE_ENCODE_FAILED, "Base64编码失败"));
                return;
            }
            String imageFormat = ImageUtil.detectImageFormat(imageFile);
            Log.d(TAG, "图片格式检测: " + imageFormat + ", Base64长度: " + imageBase64.length());
            QwenRequest requestModel = new QwenRequest(
                    context,
                    imageBase64,
                    imageFormat,
                    textContent,
                    isToolEnabled
            );
            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送图片请求... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                postResult(timeStatSb.toString());
                isRequestTimeShowed = false;
            }
            qwenOmniClient.callApiWithImage(requestModel);
        } catch (JSONException e) {
            Log.e(TAG, "创建图片请求失败", e);
            postResult("请求创建失败：" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "处理图片失败", e);
            postResult(String.format(AppConstants.MSG_IMAGE_PICK_FAILED, e.getMessage()));
        }
    }

    public void sendMultipleImageRequest(List<File> imageFiles, String textContent) {
        cancelCurrentRequestAndStopAudio();
        if (imageFiles == null || imageFiles.isEmpty()) {
            postResult("❌ 请至少选择一张图片");
            return;
        }
        List<String> imageBase64List = new java.util.ArrayList<>();
        List<String> imageFormatList = new java.util.ArrayList<>();
        StringBuilder errorMessages = new StringBuilder();
        for (File imageFile : imageFiles) {
            if (imageFile == null || !imageFile.exists()) {
                errorMessages.append("图片文件不存在\n");
                continue;
            }
            ImageUtil.ImageValidationResult validation = ImageUtil.validateImageForApi(imageFile);
            if (!validation.isValid()) {
                errorMessages.append(imageFile.getName()).append(": ").append(validation.getMessage()).append("\n");
                continue;
            }
            String imageBase64 = Base64Util.fileToBase64(imageFile);
            if (imageBase64 == null || imageBase64.isEmpty()) {
                errorMessages.append(imageFile.getName()).append(": Base64编码失败\n");
                continue;
            }
            String imageFormat = ImageUtil.detectImageFormat(imageFile);
            imageBase64List.add(imageBase64);
            imageFormatList.add(imageFormat);
        }
        if (errorMessages.length() > 0) {
            postResult("❌ 图片验证失败:\n" + errorMessages.toString());
            return;
        }
        if (imageBase64List.isEmpty()) {
            postResult("❌ 没有有效的图片");
            return;
        }
        try {
            resetTimeMark();
            messageManager.reset();
            postResult(AppConstants.MSG_IMAGE_PROCESSING + " (共 " + imageBase64List.size() + " 张图片)");
            QwenRequest requestModel = new QwenRequest(
                    context,
                    imageBase64List,
                    imageFormatList,
                    textContent,
                    isToolEnabled
            );
            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送多图片请求 (" + imageBase64List.size() + " 张)... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                postResult(timeStatSb.toString());
                isRequestTimeShowed = false;
            }
            qwenOmniClient.callApiWithImage(requestModel);
        } catch (JSONException e) {
            Log.e(TAG, "创建多图片请求失败", e);
            postResult("请求创建失败：" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "处理多图片失败", e);
            postResult(String.format(AppConstants.MSG_IMAGE_PICK_FAILED, e.getMessage()));
        }
    }

    /**
     * 发送视频文件请求（视频文件形式）
     * @param videoFile 视频文件
     * @param textContent 文本内容（可选）
     */
    public void sendVideoFileRequest(File videoFile, String textContent) {
        cancelCurrentRequestAndStopAudio();
        if (videoFile == null || !videoFile.exists()) {
            postResult("❌ 视频文件不存在");
            return;
        }
        // 验证视频文件大小（根据模型类型设置限制）
        AppConfig appConfig = new AppConfig(context);
        String modelName = appConfig.getModelName();
        boolean isMobileModel = modelName != null && modelName.contains("mobile");
        long maxSizeMB = 10; // 两种模型都使用10MB限制
        long fileSizeMB = videoFile.length() / (1024 * 1024);
        if (fileSizeMB > maxSizeMB) {
            postResult("❌ 视频文件过大（" + fileSizeMB + "MB），" + 
                      (isMobileModel ? "Mobile" : "Flash") + "模型限制为" + maxSizeMB + "MB");
            return;
        }
        try {
            resetTimeMark();
            messageManager.reset();
            postResult("正在处理视频文件...");
            String videoBase64 = Base64Util.fileToBase64(videoFile);
            if (videoBase64 == null || videoBase64.isEmpty()) {
                postResult("❌ 视频Base64编码失败");
                return;
            }
            String videoFormat = detectVideoFormat(videoFile);
            Log.d(TAG, "视频格式检测: " + videoFormat + ", Base64长度: " + videoBase64.length());
            QwenRequest requestModel = QwenRequest.builder(context)
                    .video(videoBase64, videoFormat)
                    .text(textContent)
                    .toolEnabled(isToolEnabled)
                    .audioVoice(audioVoice)
                    .build();
            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送视频文件请求... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                postResult(timeStatSb.toString());
                isRequestTimeShowed = false;
            }
            qwenOmniClient.callApiWithImage(requestModel);
        } catch (JSONException e) {
            Log.e(TAG, "创建视频文件请求失败", e);
            postResult("请求创建失败：" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "处理视频文件失败", e);
            postResult("处理视频文件失败：" + e.getMessage());
        }
    }

    /**
     * 发送视频图片列表请求（图片列表形式）
     * @param imageFiles 图片文件列表（至少2张）
     * @param textContent 文本内容（可选）
     */
    public void sendVideoImageListRequest(List<File> imageFiles, String textContent) {
        cancelCurrentRequestAndStopAudio();
        if (imageFiles == null || imageFiles.isEmpty()) {
            postResult("❌ 请至少选择2张图片");
            return;
        }
        if (imageFiles.size() < 2) {
            postResult("❌ 视频图片列表至少需要2张图片");
            return;
        }
        List<String> imageBase64List = new java.util.ArrayList<>();
        List<String> imageFormatList = new java.util.ArrayList<>();
        StringBuilder errorMessages = new StringBuilder();
        for (File imageFile : imageFiles) {
            if (imageFile == null || !imageFile.exists()) {
                errorMessages.append("图片文件不存在\n");
                continue;
            }
            ImageUtil.ImageValidationResult validation = ImageUtil.validateImageForApi(imageFile);
            if (!validation.isValid()) {
                errorMessages.append(imageFile.getName()).append(": ").append(validation.getMessage()).append("\n");
                continue;
            }
            String imageBase64 = Base64Util.fileToBase64(imageFile);
            if (imageBase64 == null || imageBase64.isEmpty()) {
                errorMessages.append(imageFile.getName()).append(": Base64编码失败\n");
                continue;
            }
            String imageFormat = ImageUtil.detectImageFormat(imageFile);
            imageBase64List.add(imageBase64);
            imageFormatList.add(imageFormat);
        }
        if (errorMessages.length() > 0) {
            postResult("❌ 图片验证失败:\n" + errorMessages.toString());
            return;
        }
        if (imageBase64List.size() < 2) {
            postResult("❌ 有效的图片数量不足（至少需要2张）");
            return;
        }
        try {
            resetTimeMark();
            messageManager.reset();
            postResult("正在处理视频图片列表... (共 " + imageBase64List.size() + " 张图片)");
            QwenRequest requestModel = QwenRequest.builder(context)
                    .videoImages(imageBase64List, imageFormatList)
                    .text(textContent)
                    .toolEnabled(isToolEnabled)
                    .audioVoice(audioVoice)
                    .build();
            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送视频图片列表请求 (" + imageBase64List.size() + " 张)... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                postResult(timeStatSb.toString());
                isRequestTimeShowed = false;
            }
            qwenOmniClient.callApiWithImage(requestModel);
        } catch (JSONException e) {
            Log.e(TAG, "创建视频图片列表请求失败", e);
            postResult("请求创建失败：" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "处理视频图片列表失败", e);
            postResult("处理视频图片列表失败：" + e.getMessage());
        }
    }

    /**
     * 检测视频文件格式
     */
    private String detectVideoFormat(File videoFile) {
        String fileName = videoFile.getName().toLowerCase();
        if (fileName.endsWith(".mp4")) {
            return "mp4";
        } else if (fileName.endsWith(".mov")) {
            return "mov";
        } else if (fileName.endsWith(".avi")) {
            return "avi";
        } else if (fileName.endsWith(".mkv")) {
            return "mkv";
        } else if (fileName.endsWith(".webm")) {
            return "webm";
        }
        return "mp4"; // 默认格式
    }

    public void setAudioVoice(String voice) {
        if (voice != null && !voice.trim().isEmpty()) {
            this.audioVoice = voice.trim();
            Log.d(TAG, "已设置发音人: " + audioVoice);
        }
    }

    public String getAudioVoice() {
        return audioVoice;
    }

    public QwenOmniClient getQwenOmniClient() {
        return qwenOmniClient;
    }

    public void cancelCurrentRequest() {
        cancelCurrentRequestAndStopAudio();
    }

    private void cancelCurrentRequestAndStopAudio() {
        Log.d(TAG, "取消当前请求并停止音频播放");
        isRequestCancelled = true;
        if (qwenOmniClient != null) {
            qwenOmniClient.cancelCurrentRequest();
        }
        if (streamingAudioPlayer != null && streamingAudioPlayer.isPlaying()) {
            streamingAudioPlayer.stop();
        }
        if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.stop();
        }
    }

    private void postResult(String text) {
        // 使用 postValue 确保可以在后台线程安全调用
        resultLiveData.postValue(text);
    }

    private void appendResult(String text) {
        String current = resultLiveData.getValue() == null ? "" : resultLiveData.getValue();
        postResult(current + text);
    }

    private void enableStartButton(boolean enabled) {
        // 使用 postValue 确保可以在后台线程安全调用
        startButtonEnable.postValue(enabled);
    }

    private void enableStopButton(boolean enabled) {
        // 使用 postValue 确保可以在后台线程安全调用
        stopButtonEnable.postValue(enabled);
    }

    private void resetButtonState() {
        enableStartButton(true);
        enableStopButton(false);
    }

    // ======================== ViewModel生命周期 自动释放资源 ========================
    @Override
    protected void onCleared() {
        super.onCleared();
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
            audioRecorder = null;
        }
        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer = null;
        }
        if (streamingAudioPlayer != null) {
            streamingAudioPlayer.stop();
            streamingAudioPlayer = null;
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        Log.d(TAG, "ViewModel销毁，资源已释放");
    }
}