package com.example.myapplication.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 录音管理类（职责：仅处理录音相关逻辑，提供开始/停止录音接口）
 */
public class AudioRecorder implements IAudioRecorder {
    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = 16000; // 固定16000采样率（Qwen-Omni推荐）
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final Context context;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private File pcmFile;
    private File wavFile;
    private IAudioRecorder.OnRecordListener listener;

    public AudioRecorder(Context context) {
        this.context = context;
    }

    /**
     * 设置录音回调
     */
    @Override
    public void setOnRecordListener(IAudioRecorder.OnRecordListener listener) {
        this.listener = listener;
    }

    /**
     * 开始录音
     * @return 是否成功开始
     */
    @Override
    public boolean startRecording() {
        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onRecordFailed("缺少录音权限");
            }
            return false;
        }

        // 初始化文件
        initRecordFiles();

        // 计算缓冲区大小
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize <= 0) {
            if (listener != null) {
                listener.onRecordFailed("缓冲区大小计算失败");
            }
            return false;
        }

        // 初始化AudioRecord
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "AudioRecord初始化失败", e);
            if (listener != null) {
                listener.onRecordFailed("录音初始化失败: " + e.getMessage());
            }
            return false;
        }

        // 开始录音
        audioRecord.startRecording();
        isRecording = true;

        if (listener != null) {
            listener.onRecording();
        }

        // 异步写入PCM数据
        new Thread(this::writePcmData).start();

        return true;
    }

    /**
     * 停止录音
     */
    public void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    /**
     * 初始化录音文件（PCM临时文件 + WAV输出文件）
     */
    private void initRecordFiles() {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            pcmFile = new File(externalFilesDir, "recorded.pcm");
            wavFile = new File(externalFilesDir, "recorded.wav");
            Log.d(TAG, "PCM文件路径: " + pcmFile.getAbsolutePath());
            Log.d(TAG, "WAV文件路径: " + wavFile.getAbsolutePath());
        }
    }

    /**
     * 写入PCM数据到文件
     */
    private void writePcmData() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        byte[] buffer = new byte[bufferSize];

        try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    fos.write(buffer, 0, read);
                }
            }
            // 录音完成后转WAV
            convertPcmToWav();
        } catch (IOException e) {
            Log.e(TAG, "PCM写入失败", e);
            if (listener != null) {
                listener.onRecordFailed("录音写入失败: " + e.getMessage());
            }
        }
    }

    /**
     * PCM转WAV
     */
    private void convertPcmToWav() {
        try {
            WavConverter.pcmToWav(pcmFile, wavFile, SAMPLE_RATE, 1, 16);
            if (listener != null) {
                listener.onRecordComplete(wavFile);
            }
        } catch (IOException e) {
            Log.e(TAG, "PCM转WAV失败", e);
            if (listener != null) {
                listener.onRecordFailed("WAV生成失败: " + e.getMessage());
            }
        }
    }

    /**
     * 是否正在录音
     */
    @Override
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 清理录音文件（可选调用）
     */
    @Override
    public void cleanRecordFiles() {
        if (pcmFile != null && pcmFile.exists()) {
            pcmFile.delete();
        }
        if (wavFile != null && wavFile.exists()) {
            wavFile.delete();
        }
    }
}