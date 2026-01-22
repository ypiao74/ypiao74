package com.example.myapplication.presenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

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
import com.example.myapplication.presenter.contract.QwenContract;
import com.example.myapplication.util.Base64Util;
import com.example.myapplication.util.Base64Validator;
import com.example.myapplication.util.ImageUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Qwen功能Presenter
 * 负责处理业务逻辑，与View解耦
 */
public abstract class QwenPresenter implements QwenContract.Presenter {
    private static final String TAG = "QwenPresenter";

    private final Context context;
    private final QwenContract.View view;
    private final Handler mainHandler;

    // 业务组件
    private AudioRecorder audioRecorder;
    private QwenOmniClient qwenOmniClient;
    private AudioPlayer audioPlayer;
    private StreamingAudioPlayer streamingAudioPlayer;

    // 数据管理
    private final MessageManager messageManager;
    private final ResponseHandler responseHandler;
    private final ToolExecutor toolExecutor;

    // 配置
    private final AppConfig appConfig;
    private boolean isToolEnabled = true;
    private String audioVoice = "Cherry"; // 默认发音人
    
    // 工具调用处理相关
    private int pendingToolCallCount = 0; // 待处理的工具调用数量
    private boolean isSecondCallPending = false; // 是否正在等待发起二次调用
    private StringBuilder toolCallResultsDisplay = new StringBuilder(); // 保存工具调用结果的展示文本
    
    // 请求取消标志
    private volatile boolean isRequestCancelled = false; // 标记当前请求是否已被取消

    // ======================== 时间统计变量 - 核心新增 ========================
    private long startRequestTime;
    private long firstResponseTime; // 第一次响应到达时间
    private long firstTextTime;
    private long firstAudioTime;
    private long firstToolCallTime;
    private boolean isFirstTextReceived = false;
    private boolean isFirstAudioReceived = false;
    private boolean isFirstToolCallReceived = false;
    private boolean isRequestTimeShowed = false;
    private boolean isTextAudioDiffCalculated = false; // 文音时间差计算标记
    
    // 二次调用时间统计
    private long secondCallStartTime; // 二次调用开始时间
    private long secondCallFirstTextTime; // 二次调用首个文本到达时间
    private long secondCallFirstAudioTime; // 二次调用首个音频到达时间
    private boolean isSecondCallStarted = false; // 是否已开始二次调用
    private boolean isSecondCallFirstTextReceived = false;
    private boolean isSecondCallFirstAudioReceived = false;
    
    // 音频播放时间
    private long audioPlayStartTime; // 音频播放开始时间
    private boolean isAudioPlayStarted = false; // 音频是否已开始播放
    
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    // 统计文本缓存，防止被覆盖
    private final StringBuilder timeStatSb = new StringBuilder();

    public QwenPresenter(Context context, QwenContract.View view) {
        this.context = context;
        this.view = view;
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
                // 记录音频播放开始时间（只记录一次，用于二次调用的音频）
                // 注意：实际播放开始时间在updateFinalResponse中已记录，这里只做UI更新
                updateUI(() -> view.appendResult(AppConstants.MSG_AUDIO_PLAY_START));
            }

            @Override
            public void onAudioPlayComplete() {
                updateUI(() -> {
                    view.appendResult(AppConstants.MSG_AUDIO_PLAY_COMPLETE);
                    view.resetButtonState();
                });
            }

            @Override
            public void onAudioPlayError(String error) {
                updateUI(() -> {
                    view.appendResult(String.format(AppConstants.MSG_AUDIO_PLAY_ERROR, error));
                    view.resetButtonState();
                });
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
                updateUI(() -> {
                    view.appendResult("\n🎵 流式音频播放完成");
                });
            }

            @Override
            public void onPlayError(String error) {
                updateUI(() -> {
                    view.appendResult("\n❌ 流式音频播放错误: " + error);
                });
            }

            @Override
            public void onChunkReceived(int chunkSize) {
            }
        });
    }

    private void initAudioRecorder() {
        audioRecorder = new AudioRecorder(context);
        audioRecorder.setOnRecordListener(new AudioRecorder.OnRecordListener() {
            @Override
            public void onRecording() {
                updateUI(() -> {
                    view.setResult(AppConstants.MSG_RECORDING);
                    messageManager.reset();
                    if (streamingAudioPlayer != null && streamingAudioPlayer.isPlaying()) {
                        streamingAudioPlayer.stop();
                    }
                    view.enableStartButton(false);
                    view.enableStopButton(true);
                    resetTimeMark(); // 重置计时
                    // 重置工具调用相关状态
                    pendingToolCallCount = 0;
                    isSecondCallPending = false;
                    toolCallResultsDisplay.setLength(0); // 清空工具调用结果展示
                    isRequestCancelled = false; // 重置取消标志
                });
            }

            @Override
            public void onRecordComplete(File wavFile) {
                updateUI(() -> {
                    view.setResult(AppConstants.MSG_PROCESSING);
                    handleRecordComplete(wavFile);
                });
            }

            @Override
            public void onRecordFailed(String errorMsg) {
                updateUI(() -> {
                    view.setResult(String.format(AppConstants.MSG_RECORD_FAILED, errorMsg));
                    view.resetButtonState();
                });
            }
        });
    }

    // 录音完成-发起请求 核心修改：带时间统计+防覆盖
    private void handleRecordComplete(File wavFile) {
        // 取消旧请求并停止音频播放
        cancelCurrentRequestAndStopAudio();
        
        // 重置取消标志，准备新的请求
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
            // 开始计时
            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送请求... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            // 只显示一次请求时间，缓存起来
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                updateUI(() -> view.setResult(timeStatSb.toString()));
                isRequestTimeShowed = true;
            }

            qwenOmniClient.callApi(wavFile);
        } catch (JSONException e) {
            Log.e(TAG, "创建请求失败", e);
            updateUI(() -> view.setResult("请求创建失败：" + e.getMessage()));
        }
    }

    private void initQwenClient() {
        qwenOmniClient = new QwenOmniClient(context, appConfig);
        qwenOmniClient.setToolEnabled(isToolEnabled);
        qwenOmniClient.setAudioVoice(audioVoice); // 设置发音人
        qwenOmniClient.setOnApiCallback(new QwenOmniClient.OnApiCallback() {
            @Override
            public void onSuccess(String responseLine) {
                // 记录第一次响应到达时间（只记录一次）
                if (firstResponseTime == 0) {
                    firstResponseTime = System.currentTimeMillis();
                    Log.d(TAG, "第一次响应到达，耗时: " + (firstResponseTime - startRequestTime) + "ms");
                }
                
                // 如果检测到工具调用，跳过音频处理
                if (qwenOmniClient.hasToolCallDetected()) {
                    // 检查是否是音频数据，如果是则跳过
                    String audioBase64 = ResponseParser.parseAudioBase64(responseLine);
                    if (audioBase64 != null && !audioBase64.isEmpty()) {
                        Log.d(TAG, "检测到工具调用，跳过音频数据处理");
                        return; // 跳过音频处理
                    }
                }
                handleApiSuccess(responseLine);
            }

            @Override
            public void onToolCallComplete(QwenOmniClient.CompleteToolCall completeToolCall) {
                // 首包工具调用耗时-只统计一次（从第一次响应开始计算）
                if (!isFirstToolCallReceived && completeToolCall != null) {
                    firstToolCallTime = System.currentTimeMillis();
                    if (firstResponseTime > 0) {

                    } else {
                        // 如果firstResponseTime未设置，使用startRequestTime作为后备
                        long toolCost = firstToolCallTime - startRequestTime;
                        String toolText = "[⏱️] 首个工具调用到达耗时: " + toolCost / 1000.0 + " 秒";
                        Log.d(TAG, toolText);
                        timeStatSb.append(toolText).append("\n");
                    }
                    isFirstToolCallReceived = true;
                    refreshTimeStatUI(); // 刷新UI确保显示
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
                updateUI(() -> {
                    view.setResult(errorText);
                    view.resetButtonState();
                });
            }

            @Override
            public void onError(Throwable throwable) {
                long errorCost = System.currentTimeMillis() - startRequestTime;
                String errorMsg = throwable != null ? throwable.getMessage() : "未知异常";
                String errorText = String.format(AppConstants.MSG_API_ERROR, errorMsg) + "\n[⏱️] 请求异常耗时: " + errorCost / 1000.0 + " 秒";
                Log.e(TAG, errorText);
                updateUI(() -> {
                    view.setResult(errorText);
                    view.resetButtonState();
                });
            }
        });
    }

    // 处理响应核心修改：时间统计置顶 + 文音时间差计算
    private void handleApiSuccess(String responseLine) {
        String line = responseLine.trim();
        Log.i(TAG, "原始响应行(trim后): " + line);
        ResponseHandler.ResponseResult result = responseHandler.handleResponseLine(responseLine);
        if (result.getType() == ResponseHandler.ResponseResult.Type.STREAM_END) {
            long totalCost = System.currentTimeMillis() - startRequestTime;
            String totalText = "[✅] 流式响应完成，总耗时: " + totalCost / 1000.0 + " 秒";
            Log.d(TAG, totalText);
            timeStatSb.append(totalText).append("\n");
            // 确保文音时间差已计算
            calculateTextAudioDiff();
            // 打印性能面板（Log+上屏）
            printTimeStatistics(totalCost);
            updateFinalResponse();
            return;
        }

        if (result.getType() == ResponseHandler.ResponseResult.Type.TEXT) {
            // 判断是第一次调用还是二次调用
            if (isSecondCallStarted && !isSecondCallFirstTextReceived) {
                // 二次调用首个文本到达
                firstTextTime = System.currentTimeMillis();
                secondCallFirstTextTime = System.currentTimeMillis();
                long secondTextCost = secondCallFirstTextTime - secondCallStartTime;
                String secondTextTime = "[⏱️] 二次调用文本到达耗时: " + secondTextCost / 1000.0 + " 秒";
                Log.d(TAG, secondTextTime);
                timeStatSb.append(secondTextTime).append("\n");
                isSecondCallFirstTextReceived = true;
                refreshTimeStatUI();
            } else if (!isFirstTextReceived) {
                // 第一次调用首包文本耗时-只统计一次
                firstTextTime = System.currentTimeMillis();
                long textCost = firstTextTime - startRequestTime;
                String textTime = "[⏱️] 首个文本到达耗时: " + textCost / 1000.0 + " 秒";
                Log.d(TAG, textTime);
                timeStatSb.append(textTime).append("\n");
                isFirstTextReceived = true;
                calculateTextAudioDiff(); // 检查音频是否已到达
            }
            // 核心：时间统计置顶 + 工具调用结果 + 文本内容 + 音频提示，永不覆盖
            updateUI(() -> {
                String text = messageManager.getTextContent();
                // 保留工具调用结果的展示
                String toolCallInfo = toolCallResultsDisplay.length() > 0 ? toolCallResultsDisplay.toString() + "\n" : "";
                String showText = timeStatSb.toString() + toolCallInfo + "Qwen-Omni响应:\n" + text + "\n\n正在接收音频...";
                view.setResult(showText);
            });
        }

        // 处理流式音频-只追加不覆盖统计文本
        if (result.getType() == ResponseHandler.ResponseResult.Type.AUDIO) {
            // 判断是第一次调用还是二次调用
            if (isSecondCallStarted && !isSecondCallFirstAudioReceived) {
                // 二次调用首个音频到达
                secondCallFirstAudioTime = System.currentTimeMillis();
                long secondAudioCost = secondCallFirstAudioTime - secondCallStartTime;
                String secondAudioTime = "[⏱️] 二次调用音频到达耗时: " + secondAudioCost / 1000.0 + " 秒";
                Log.d(TAG, secondAudioTime);
                timeStatSb.append(secondAudioTime).append("\n");
                isSecondCallFirstAudioReceived = true;
                refreshTimeStatUI();
            } else if (!isFirstAudioReceived) {
                // 第一次调用首包音频耗时-只统计一次
                firstAudioTime = System.currentTimeMillis();
                long audioCost = firstAudioTime - startRequestTime;
                String audioTime = "[⏱️] 首个音频到达耗时: " + audioCost / 1000.0 + " 秒";
                Log.d(TAG, audioTime);
                timeStatSb.append(audioTime).append("\n");
                isFirstAudioReceived = true;
                calculateTextAudioDiff(); // 检查文本是否已到达
            }
            String audioChunk = result.getContent();
            if (audioChunk != null && !audioChunk.isEmpty()) {
                // 如果请求已被取消，忽略后续的音频数据
                if (isRequestCancelled) {
                    Log.d(TAG, "请求已取消，忽略音频数据");
                    return;
                }
                
                if (!streamingAudioPlayer.isPlaying()) {
                    streamingAudioPlayer.start();
                }
                streamingAudioPlayer.appendAudioChunk(audioChunk);
            }
        }
    }

    // ======================== 新增：计算首包文音时间差 ========================
    private void calculateTextAudioDiff() {
        if (isFirstTextReceived && isFirstAudioReceived && !isTextAudioDiffCalculated) {
            double diffSeconds = (firstAudioTime - firstTextTime) / 1000.0;
            String diffDesc = diffSeconds > 0 ? "(音频晚)" : "(音频早)";
            String diffText = String.format("[⏱️] 文音时间差: %+.2f 秒 %s", diffSeconds, diffDesc);
            Log.d(TAG, diffText);
            timeStatSb.append(diffText).append("\n");
            isTextAudioDiffCalculated = true;
            refreshTimeStatUI(); // 实时刷新UI显示
        }
    }

    // ======================== 新增：刷新统计信息UI ========================
    private void refreshTimeStatUI() {
        updateUI(() -> {
            // 提取当前显示的非统计部分，重新拼接
            String currentResult = view.getResult() != null ? view.getResult() : "";
            int contentStartIndex = currentResult.indexOf("Qwen-Omni响应:");
            if (contentStartIndex != -1) {
                String contentPart = currentResult.substring(contentStartIndex);
                view.setResult(timeStatSb.toString() + contentPart);
            } else {
                view.setResult(timeStatSb.toString());
            }
        });
    }

    private void handleToolCallComplete(QwenOmniClient.CompleteToolCall completeToolCall) {
        if (completeToolCall == null) {
            return;
        }

        Log.d(TAG, "工具调用拼接完成: " + completeToolCall);

        messageManager.clearAudioData();
        String toolOutput = toolExecutor.execute(completeToolCall);

        // 格式化并保存工具调用结果（只显示核心字段）
        String toolResultDisplay = formatToolCallResult(completeToolCall, toolOutput);
        toolCallResultsDisplay.append(toolResultDisplay).append("\n");
        
        // 上屏展示工具调用结果（追加显示，不覆盖）
        updateUI(() -> view.appendResult(toolResultDisplay));

        try {
            // 添加 Assistant Message（包含 tool_calls）
            messageManager.addAssistantToolCallMessage(
                    completeToolCall.toolId,
                    completeToolCall.functionName,
                    completeToolCall.arguments
            );
            // 添加 Tool Message（工具执行结果）
            messageManager.addToolResultMessage(completeToolCall.toolId, toolOutput);
            
            // 增加待处理的工具调用计数
            pendingToolCallCount++;
            Log.d(TAG, "已处理工具调用: " + completeToolCall.functionName + "，当前待处理数量=" + pendingToolCallCount);
            // 从第一次响应到达开始计算
            long toolCost = firstToolCallTime - firstResponseTime;
            String toolText = "[⏱️] 工具调用响应处理耗时: " + toolCost / 1000.0 + " 秒";
            Log.d(TAG, toolText);
            timeStatSb.append(toolText).append("\n");
            mainHandler.postDelayed(() -> {
                checkAndTriggerSecondCall();
            }, 50);
        } catch (JSONException e) {
            Log.e(TAG, "构建工具调用消息失败", e);
            updateUI(() -> view.appendResult("\n构建工具调用消息失败: " + e.getMessage()));
        }
    }
    
    /**
     * 格式化工具调用结果用于展示（只显示核心字段）
     * @param completeToolCall 工具调用信息
     * @param toolOutput 工具执行结果
     * @return 格式化后的展示文本
     */
    private String formatToolCallResult(QwenOmniClient.CompleteToolCall completeToolCall, String toolOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[工具调用] ").append(completeToolCall.functionName);
        
        // 只显示参数（如果有）
        if (completeToolCall.arguments != null && completeToolCall.arguments.length() > 0) {
            sb.append(" 参数: ").append(completeToolCall.arguments.toString());
        }
        
        return sb.toString();
    }
    
    /**
     * 检查并触发二次调用
     * 只有当所有工具调用都处理完成后，才发起一次二次调用
     */
    private void checkAndTriggerSecondCall() {
        // 检查是否已经有待处理的工具调用
        if (pendingToolCallCount == 0) {
            return;
        }
        
        // 如果已经在等待发起二次调用，不重复发起
        if (isSecondCallPending) {
            Log.d(TAG, "二次调用已在等待中，跳过");
            return;
        }
        
        // 标记为正在等待发起二次调用
        isSecondCallPending = true;
        int currentPendingCount = pendingToolCallCount;
        pendingToolCallCount = 0; // 重置计数
        
        // 记录二次调用开始时间

        isSecondCallStarted = true;
        isSecondCallFirstTextReceived = false;
        isSecondCallFirstAudioReceived = false;
        Log.d(TAG, "二次调用开始，时间: " + secondCallStartTime);
        
        try {
            JSONArray completeMessages = new JSONArray();
            
            // 从 QwenOmniClient 获取第一次调用的 messages（包含 System + User）
            JSONArray initialMessages = qwenOmniClient.getMessages();
            if (initialMessages != null) {
                // 复制初始消息（System + User）
                for (int i = 0; i < initialMessages.length(); i++) {
                    completeMessages.put(initialMessages.get(i));
                }
                Log.d(TAG, "已添加初始消息（System + User），数量=" + initialMessages.length());
            }
            
            // 从 messageManager 获取 Assistant + Tool 消息
            JSONArray toolMessages = messageManager.getMessageHistory();
            if (toolMessages != null) {
                // 只添加 Assistant 和 Tool 消息（跳过可能存在的重复 User 消息）
                for (int i = 0; i < toolMessages.length(); i++) {
                    JSONObject msg = toolMessages.getJSONObject(i);
                    String role = msg.optString("role", "");
                    // 只添加 assistant 和 tool 角色的消息
                    if ("assistant".equals(role) || "tool".equals(role)) {
                        completeMessages.put(msg);
                    }
                }
                Log.d(TAG, "已添加工具相关消息（Assistant + Tool），数量=" + toolMessages.length() + "，包含 " + currentPendingCount + " 个工具调用结果");
            }
            
            Log.d(TAG, "二次调用完整消息历史长度=" + completeMessages.length() + "，包含 " + currentPendingCount + " 个工具调用结果");
            qwenOmniClient.setMessages(completeMessages);

            // 显示工具调用结果和时间统计，不覆盖已有内容
            String toolCallInfo = toolCallResultsDisplay.length() > 0 ? toolCallResultsDisplay.toString() + "\n" : "";
            updateUI(() -> view.setResult(timeStatSb.toString() + toolCallInfo + AppConstants.MSG_TOOL_EXECUTE_COMPLETE));
            secondCallStartTime = System.currentTimeMillis();
            // 二次调用：传入 null 表示使用已保存的完整消息历史
            qwenOmniClient.callApi(null);
            
            // 重置标志，允许下次工具调用时再次发起二次调用
            isSecondCallPending = false;
        } catch (JSONException e) {
            Log.e(TAG, "构建二次调用上下文失败", e);
            updateUI(() -> view.appendResult("\n构建上下文失败: " + e.getMessage()));
            isSecondCallPending = false; // 重置标志
        }
    }

    private void handleSummaryComplete(String summary) {
        updateUI(() -> {
            // 保留工具调用结果的展示
            String toolCallInfo = toolCallResultsDisplay.length() > 0 ? toolCallResultsDisplay.toString() + "\n" : "";
            String showText = timeStatSb.toString() + toolCallInfo + "Qwen-Omni 自然语言总结:\n" + summary;
            if (messageManager.hasAudioData()) {
                showText += "\n\n正在播放语音响应...";
                view.setResult(showText);
                updateFinalResponse();
            } else {
                view.setResult(showText);
                view.resetButtonState();
            }
        });
    }

    private void updateFinalResponse() {
        updateUI(() -> {
            String finalText = messageManager.hasTextContent()
                    ? messageManager.getTextContent()
                    : "已成功执行工具操作。";

            StringBuilder displayText = new StringBuilder();
            // 永远把时间统计放在最顶部，核心解决覆盖问题
            displayText.append(timeStatSb.toString());
            // 添加工具调用结果展示（如果有）
            if (toolCallResultsDisplay.length() > 0) {
                displayText.append(toolCallResultsDisplay.toString()).append("\n");
            }
            displayText.append("Qwen-Omni响应:\n");
            displayText.append(finalText);

            if (streamingAudioPlayer != null && streamingAudioPlayer.isPlaying()) {
                displayText.append("\n\n流式音频播放中...");
                view.setResult(displayText.toString());
            } else if (messageManager.hasAudioData()) {
                try {
                    String pureBase64 = Base64Validator.normalize(messageManager.getAudioBase64());
                    byte[] audioBytes = Base64.decode(pureBase64, Base64.DEFAULT);

                    if (audioBytes.length < AppConstants.MIN_AUDIO_BYTES) {
                        displayText.append("\n").append(AppConstants.MSG_AUDIO_INVALID);
                        view.setResult(displayText.toString());
                        view.resetButtonState();
                        return;
                    }

                    displayText.append("\n\n正在播放语音响应...");
                    view.setResult(displayText.toString());
                    
                    // 如果是二次调用，记录音频播放开始时间
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
                    displayText.append("\n").append(String.format(
                            AppConstants.MSG_AUDIO_DECODE_FAILED,
                            e.getMessage()
                    ));
                    view.setResult(displayText.toString());
                    Log.e(TAG, "Base64解码错误", e);
                    view.resetButtonState();
                }
            } else {
                displayText.append("\n").append(AppConstants.MSG_NO_AUDIO_RESPONSE);
                view.setResult(displayText.toString());
                view.resetButtonState();
            }
        });
    }

    // 时间统计辅助方法
    private void resetTimeMark() {
        startRequestTime = 0;
        firstResponseTime = 0;
        firstTextTime = 0;
        firstAudioTime = 0;
        isRequestCancelled = false; // 重置取消标志
        firstToolCallTime = 0;
        isFirstTextReceived = false;
        isFirstAudioReceived = false;
        isFirstToolCallReceived = false;
        isRequestTimeShowed = false;
        isTextAudioDiffCalculated = false;
        
        // 重置二次调用相关时间
        secondCallStartTime = 0;
        secondCallFirstTextTime = 0;
        secondCallFirstAudioTime = 0;
        isSecondCallStarted = false;
        isSecondCallFirstTextReceived = false;
        isSecondCallFirstAudioReceived = false;
        
        // 重置音频播放时间
        audioPlayStartTime = 0;
        isAudioPlayStarted = false;
        
        timeStatSb.setLength(0);
    }

    // ======================== 核心修改：性能统计面板上屏 ========================
    private void printTimeStatistics(long totalCost) {
        // 1. 原有Logcat日志打印（保留不变）
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

        // 2. 新增：性能统计面板上屏（和Logcat格式一致）
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

        // 3. 上屏显示（追加方式，不覆盖原有内容）
        updateUI(() -> view.appendResult(statSb.toString()));
    }

    @Override
    public void startRecording() {
        if (audioRecorder != null && !audioRecorder.isRecording()) {
            audioRecorder.startRecording();
        }
    }

    @Override
    public void stopRecording() {
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
        }
    }

    @Override
    public void setToolEnabled(boolean enabled) {
        this.isToolEnabled = enabled;
        if (qwenOmniClient != null) {
            qwenOmniClient.setToolEnabled(enabled);
        }
    }


    @Override
    public void sendImageRequest(File imageFile, String textContent) {
        // 取消旧请求并停止音频播放
        cancelCurrentRequestAndStopAudio();
        
        if (imageFile == null || !imageFile.exists()) {
            updateUI(() -> view.setResult("❌ 图片文件不存在"));
            return;
        }

        // 验证图片文件
        ImageUtil.ImageValidationResult validation = ImageUtil.validateImageForApi(imageFile);
        if (!validation.isValid()) {
            updateUI(() -> view.setResult("❌ " + validation.getMessage()));
            return;
        }

        try {
            resetTimeMark();
            messageManager.reset();

            updateUI(() -> view.setResult(AppConstants.MSG_IMAGE_PROCESSING));

            // 将图片文件转换为 Base64
            String imageBase64 = Base64Util.fileToBase64(imageFile);
            if (imageBase64 == null || imageBase64.isEmpty()) {
                updateUI(() -> view.setResult(String.format(AppConstants.MSG_IMAGE_ENCODE_FAILED, "Base64编码失败")));
                return;
            }

            // 检测图片格式
            String imageFormat = ImageUtil.detectImageFormat(imageFile);
            Log.d(TAG, "图片格式检测: " + imageFormat + ", Base64长度: " + imageBase64.length());

            // 构建请求
            QwenRequest requestModel = new QwenRequest(
                context,
                imageBase64,
                imageFormat,
                textContent,  // 可选的文本内容
                isToolEnabled
            );

            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送图片请求... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                updateUI(() -> view.setResult(timeStatSb.toString()));
                isRequestTimeShowed = true;
            }

            // 使用图片请求专用方法
            qwenOmniClient.callApiWithImage(requestModel);
        } catch (JSONException e) {
            Log.e(TAG, "创建图片请求失败", e);
            updateUI(() -> view.setResult("请求创建失败：" + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "处理图片失败", e);
            updateUI(() -> view.setResult(String.format(AppConstants.MSG_IMAGE_PICK_FAILED, e.getMessage())));
        }
    }

    @Override
    public void sendMultipleImageRequest(java.util.List<File> imageFiles, String textContent) {
        // 取消旧请求并停止音频播放
        cancelCurrentRequestAndStopAudio();
        
        if (imageFiles == null || imageFiles.isEmpty()) {
            updateUI(() -> view.setResult("❌ 请至少选择一张图片"));
            return;
        }

        // 验证所有图片
        java.util.List<String> imageBase64List = new java.util.ArrayList<>();
        java.util.List<String> imageFormatList = new java.util.ArrayList<>();
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

            // 转换为 Base64
            String imageBase64 = Base64Util.fileToBase64(imageFile);
            if (imageBase64 == null || imageBase64.isEmpty()) {
                errorMessages.append(imageFile.getName()).append(": Base64编码失败\n");
                continue;
            }

            String imageFormat = ImageUtil.detectImageFormat(imageFile);
            imageBase64List.add(imageBase64);
            imageFormatList.add(imageFormat);
        }

        // 如果有验证错误，显示错误信息
        if (errorMessages.length() > 0) {
            updateUI(() -> view.setResult("❌ 图片验证失败:\n" + errorMessages.toString()));
            return;
        }

        // 如果没有有效的图片，返回
        if (imageBase64List.isEmpty()) {
            updateUI(() -> view.setResult("❌ 没有有效的图片"));
            return;
        }

        try {
            resetTimeMark();
            messageManager.reset();

            updateUI(() -> view.setResult(AppConstants.MSG_IMAGE_PROCESSING + " (共 " + imageBase64List.size() + " 张图片)"));

            // 构建多图片请求
            QwenRequest requestModel = new QwenRequest(
                context,
                imageBase64List,
                imageFormatList,
                textContent,  // 可选的文本内容
                isToolEnabled
            );

            startRequestTime = System.currentTimeMillis();
            String currentTimeStr = sdf.format(new Date(startRequestTime)) + "." + String.format("%03d", startRequestTime % 1000);
            String reqTimeText = "正在发送多图片请求 (" + imageBase64List.size() + " 张)... 当前时间: " + currentTimeStr;
            Log.d(TAG, reqTimeText);
            if (!isRequestTimeShowed) {
                timeStatSb.append(reqTimeText).append("\n");
                updateUI(() -> view.setResult(timeStatSb.toString()));
                isRequestTimeShowed = true;
            }

            // 使用图片请求专用方法
            qwenOmniClient.callApiWithImage(requestModel);
        } catch (JSONException e) {
            Log.e(TAG, "创建多图片请求失败", e);
            updateUI(() -> view.setResult("请求创建失败：" + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "处理多图片失败", e);
            updateUI(() -> view.setResult(String.format(AppConstants.MSG_IMAGE_PICK_FAILED, e.getMessage())));
        }
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
     * 获取 QwenOmniClient 实例（用于设置发音人等配置）
     * @return QwenOmniClient 实例
     */
    public QwenOmniClient getQwenOmniClient() {
        return qwenOmniClient;
    }
    
    /**
     * 取消当前请求并停止所有音频播放
     * 在发起新请求前调用，确保旧连接和音频播放被清理
     */
    @Override
    public void cancelCurrentRequest() {
        cancelCurrentRequestAndStopAudio();
    }
    
    private void cancelCurrentRequestAndStopAudio() {
        Log.d(TAG, "取消当前请求并停止音频播放");
        
        // 设置取消标志，防止后续音频数据继续处理
        isRequestCancelled = true;
        
        // 取消网络请求
        if (qwenOmniClient != null) {
            qwenOmniClient.cancelCurrentRequest();
        }
        
        // 停止流式音频播放
        if (streamingAudioPlayer != null && streamingAudioPlayer.isPlaying()) {
            streamingAudioPlayer.stop();
            Log.d(TAG, "已停止流式音频播放");
        }
        
        // 停止普通音频播放
        if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.stop();
            Log.d(TAG, "已停止音频播放");
        }
    }

    @Override
    public void release() {
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
    }

    private void updateUI(Runnable action) {
        if (mainHandler != null && Looper.myLooper() != mainHandler.getLooper()) {
            mainHandler.post(action);
        } else if (action != null) {
            action.run();
        }
    }

    // ======================== 需在 QwenContract.View 中添加的方法 ========================
    // 打开 QwenContract.java，在 View 接口中添加：
    // String getResult();
}