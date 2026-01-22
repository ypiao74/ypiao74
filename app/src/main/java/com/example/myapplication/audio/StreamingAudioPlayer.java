package com.example.myapplication.audio;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式音频播放器
 * 支持边接收音频数据边播放，实现低延迟的音频响应
 */
public class StreamingAudioPlayer {
    private static final String TAG = "StreamingAudioPlayer";
    private static final int SAMPLE_RATE = 24000;
    private static final int CHANNELS = 1;
    private static final int BIT_DEPTH = 16;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // 缓冲区大小（约100ms的音频数据）
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(
        SAMPLE_RATE, 
        CHANNEL_CONFIG, 
        AUDIO_FORMAT
    ) * 2;
    
    private AudioTrack audioTrack;
    private final Object audioTrackLock = new Object(); // 保护 audioTrack 的同步锁
    private BlockingQueue<byte[]> audioQueue;
    private Thread playbackThread;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    
    private OnStreamingAudioListener listener;
    private Handler mainHandler;
    
    /**
     * 流式音频播放回调接口
     */
    public interface OnStreamingAudioListener {
        void onPlayStart();
        void onPlayComplete();
        void onPlayError(String error);
        void onChunkReceived(int chunkSize);
    }
    
    public StreamingAudioPlayer() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.audioQueue = new LinkedBlockingQueue<>();
    }
    
    /**
     * 设置播放回调
     */
    public void setOnStreamingAudioListener(OnStreamingAudioListener listener) {
        this.listener = listener;
    }
    
    /**
     * 初始化AudioTrack
     */
    private void initAudioTrack() {
        synchronized (audioTrackLock) {
            if (audioTrack != null) {
                releaseAudioTrackLocked();
            }
            
            try {
                audioTrack = new AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM
                );
                
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioTrack初始化失败");
                }
                
                Log.d(TAG, "AudioTrack初始化成功，缓冲区大小: " + BUFFER_SIZE);
            } catch (Exception e) {
                Log.e(TAG, "AudioTrack初始化异常", e);
                notifyError("AudioTrack初始化失败: " + e.getMessage());
                throw e;
            }
        }
    }
    
    /**
     * 开始播放（准备接收音频数据）
     */
    public void start() {
        if (isPlaying.get()) {
            Log.w(TAG, "播放器已在运行");
            return;
        }
        
        isStopped.set(false);
        isPlaying.set(true);
        audioQueue.clear();
        
        try {
            initAudioTrack();
            synchronized (audioTrackLock) {
                if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.play();
                } else {
                    throw new IllegalStateException("AudioTrack未初始化");
                }
            }
            notifyPlayStart();
            
            // 启动播放线程
            playbackThread = new Thread(this::playbackLoop, "StreamingAudioPlayback");
            playbackThread.start();
            
            Log.d(TAG, "流式音频播放已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动播放失败", e);
            isPlaying.set(false);
            notifyError("启动播放失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加音频数据片段（Base64编码）
     */
    public void appendAudioChunk(String base64Chunk) {
        if (!isPlaying.get() || isStopped.get()) {
            Log.w(TAG, "播放器未运行，忽略音频片段");
            return;
        }
        
        try {
            // 解码Base64数据
            byte[] audioBytes = Base64.decode(base64Chunk, Base64.NO_WRAP);
            
            if (audioBytes.length > 0) {
                audioQueue.offer(audioBytes);
                if (listener != null) {
                    mainHandler.post(() -> listener.onChunkReceived(audioBytes.length));
                }
                Log.d(TAG, "收到音频片段: " + audioBytes.length + " bytes");
            }
        } catch (Exception e) {
            Log.e(TAG, "解码音频片段失败", e);
            notifyError("解码音频片段失败: " + e.getMessage());
        }
    }
    
    /**
     * 播放循环（在后台线程运行）
     */
    private void playbackLoop() {
        Log.d(TAG, "播放循环开始");
        
        int emptyQueueCount = 0;
        final int MAX_EMPTY_COUNT = 20; // 队列持续为空20次（约2秒）后认为播放完成
        
        try {
            while (isPlaying.get() && !isStopped.get()) {
                try {
                    // 从队列中获取音频数据（最多等待100ms）
                    byte[] audioData = audioQueue.poll();
                    
                    if (audioData != null) {
                        emptyQueueCount = 0; // 重置空队列计数
                        // 写入AudioTrack播放（需要同步保护）
                        synchronized (audioTrackLock) {
                            if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                                int written = audioTrack.write(audioData, 0, audioData.length);
                                if (written < 0) {
                                    Log.e(TAG, "AudioTrack写入失败: " + written);
                                    break;
                                }
                                Log.d(TAG, "播放音频片段: " + audioData.length + " bytes, 写入: " + written);
                            } else {
                                Log.w(TAG, "AudioTrack不可用，退出播放循环");
                                break;
                            }
                        }
                    } else {
                        emptyQueueCount++;
                        if (emptyQueueCount >= MAX_EMPTY_COUNT) {
                            Log.d(TAG, "队列持续为空，等待播放完成");
                            break;
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "播放循环被中断");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "播放循环异常", e);
                    notifyError("播放异常: " + e.getMessage());
                    break;
                }
            }
        } finally {
            // 播放完成前，处理队列中剩余的数据
            if (isPlaying.get() && !isStopped.get()) {
                Log.d(TAG, "处理队列中剩余数据");
                while (!audioQueue.isEmpty() && !isStopped.get()) {
                    try {
                        byte[] audioData = audioQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (audioData != null) {
                            synchronized (audioTrackLock) {
                                if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                                    audioTrack.write(audioData, 0, audioData.length);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                // 等待AudioTrack缓冲区播放完成
                synchronized (audioTrackLock) {
                    if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            // 等待缓冲区播放完成（约200ms）
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                // 忽略中断
                            }
                            try {
                                audioTrack.stop();
                            } catch (IllegalStateException e) {
                                Log.w(TAG, "停止AudioTrack时状态异常（可能已被释放）", e);
                            }
                        }
                    }
                }
                
                notifyPlayComplete();
            }
            
            releaseAudioTrack();
            isPlaying.set(false);
            Log.d(TAG, "播放循环结束");
        }
    }
    
    /**
     * 停止播放
     */
    public void stop() {
        if (!isPlaying.get()) {
            return;
        }
        
        Log.d(TAG, "停止流式音频播放");
        isStopped.set(true);
        isPlaying.set(false);
        
        // 清空队列
        audioQueue.clear();
        
        // 立即停止AudioTrack并清空缓冲区（在同步块内操作）
        synchronized (audioTrackLock) {
            if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                try {
                    int playState = audioTrack.getPlayState();
                    if (playState == AudioTrack.PLAYSTATE_PLAYING || playState == AudioTrack.PLAYSTATE_PAUSED) {
                        // 先停止播放
                        audioTrack.stop();
                        // 清空缓冲区中未播放的数据
                        audioTrack.flush();
                        Log.d(TAG, "已停止AudioTrack并清空缓冲区");
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "停止AudioTrack时状态异常", e);
                } catch (Exception e) {
                    Log.w(TAG, "清空AudioTrack缓冲区失败", e);
                }
            }
        }
        
        // 中断播放线程
        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "等待播放线程结束超时");
            }
            playbackThread = null;
        }
        
        // 释放AudioTrack（使用同步保护）
        releaseAudioTrack();
    }
    
    /**
     * 释放AudioTrack资源（带同步保护）
     */
    private void releaseAudioTrack() {
        synchronized (audioTrackLock) {
            releaseAudioTrackLocked();
        }
    }
    
    /**
     * 释放AudioTrack资源（内部方法，必须在 audioTrackLock 同步块内调用）
     */
    private void releaseAudioTrackLocked() {
        if (audioTrack != null) {
            try {
                // 检查AudioTrack状态，避免在无效状态下操作
                int state = audioTrack.getState();
                if (state == AudioTrack.STATE_INITIALIZED) {
                    int playState = audioTrack.getPlayState();
                    if (playState == AudioTrack.PLAYSTATE_PLAYING || playState == AudioTrack.PLAYSTATE_PAUSED) {
                        try {
                            // 停止播放
                            audioTrack.stop();
                            // 清空缓冲区中未播放的数据
                            audioTrack.flush();
                            Log.d(TAG, "已清空AudioTrack缓冲区");
                        } catch (IllegalStateException e) {
                            Log.w(TAG, "停止AudioTrack时状态异常（可能已被释放）", e);
                        } catch (Exception e) {
                            Log.w(TAG, "清空AudioTrack缓冲区失败", e);
                        }
                    }
                }
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "释放AudioTrack失败", e);
            } finally {
                audioTrack = null;
            }
        }
    }
    
    /**
     * 检查是否正在播放
     */
    public boolean isPlaying() {
        return isPlaying.get() && !isStopped.get();
    }
    
    /**
     * 通知播放开始
     */
    private void notifyPlayStart() {
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayStart());
        }
    }
    
    /**
     * 通知播放完成
     */
    private void notifyPlayComplete() {
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayComplete());
        }
    }
    
    /**
     * 通知播放错误
     */
    private void notifyError(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayError(error));
        }
    }
}
