package com.example.myapplication.audio;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 音频播放器（支持Base64解码、WAV文件生成、音频播放，适配车载场景）
 */
public class AudioPlayer implements IAudioPlayer {
    private static final String TAG = "CarAudioPlayer";
    private static final int SAMPLE_RATE = 30000;
    private static final int CHANNELS = 1;
    private static final int BIT_DEPTH = 16;
    private static final String CACHE_DIR_NAME = "car_audio_cache";

    private IAudioPlayer.OnAudioPlayListener listener;
    private MediaPlayer mediaPlayer;
    private File tempAudioFile; // 临时WAV文件（播放后自动清理）

    public AudioPlayer() {}

    /**
     * 设置播放回调
     */
    @Override
    public void setOnAudioPlayListener(IAudioPlayer.OnAudioPlayListener listener) {
        this.listener = listener;
    }

    /**
     * 查询是否正在播放
     */
    @Override
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /**
     * 播放音频（Base64编码）
     */
    @Override
    public void playAudioFromBase64(String base64Data) {
        stop();

        new Thread(() -> {
            try {
                byte[] audioBytes = Base64.decode(base64Data, Base64.NO_WRAP);
                Log.d(TAG, "解码音频数据长度: " + audioBytes.length + " bytes");

                if (audioBytes.length < 1024) {
                    notifyError("音频数据过短（无效），长度: " + audioBytes.length + "B");
                    return;
                }

                tempAudioFile = createWavFileFromPcm(audioBytes);
                Log.d(TAG, "WAV文件创建成功: " + tempAudioFile.getAbsolutePath());
                notifyAudioSaved(tempAudioFile);

                initMediaPlayerAndPlay();

            } catch (Exception e) {
                Log.e(TAG, "播放音频失败", e);
                notifyError("播放失败: " + e.getMessage());
                releaseResources();
            }
        }).start();
    }

    /**
     * 生成标准WAV文件（PCM数据+WAV头）
     */
    private File createWavFileFromPcm(byte[] pcmData) throws IOException {
        File cacheDir = new File(System.getProperty("java.io.tmpdir"), CACHE_DIR_NAME);
        if (!cacheDir.exists()) {
            boolean mkdirSuccess = cacheDir.mkdirs();
            Log.d(TAG, "缓存目录创建: " + (mkdirSuccess ? "成功" : "失败"));
        }

        File wavFile = File.createTempFile("qwen_audio_", ".wav", cacheDir);
        wavFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(wavFile)) {
            writeWavHeader(fos, pcmData.length);
            fos.write(pcmData);
            fos.flush();
        }

        return wavFile;
    }

    private void writeWavHeader(FileOutputStream fos, int pcmLength) throws IOException {
        int totalDataLen = pcmLength + 36;
        int byteRate = SAMPLE_RATE * CHANNELS * BIT_DEPTH / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) CHANNELS; header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (CHANNELS * BIT_DEPTH / 8); header[33] = 0;
        header[34] = (byte) BIT_DEPTH; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmLength & 0xff);
        header[41] = (byte) ((pcmLength >> 8) & 0xff);
        header[42] = (byte) ((pcmLength >> 16) & 0xff);
        header[43] = (byte) ((pcmLength >> 24) & 0xff);

        fos.write(header);
    }

    private void initMediaPlayerAndPlay() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "MediaPlayer准备就绪，播放时长: " + mp.getDuration() + "ms");
            notifyPlayStart();
            mp.start();
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            Log.d(TAG, "音频播放完成");
            notifyPlayComplete();
            stop();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            String errorMsg = "播放错误: what=" + what + ", extra=" + extra;
            Log.e(TAG, errorMsg);
            notifyError(errorMsg);
            stop();
            return true;
        });

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(tempAudioFile.getAbsolutePath());
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer初始化失败", e);
            notifyError("播放器初始化失败: " + e.getMessage());
            stop();
        }
    }

    /**
     * ✅ 关键：添加公用 stop 方法
     */
    @Override
    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "停止播放异常", e);
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (tempAudioFile != null && tempAudioFile.exists()) {
            boolean deleted = tempAudioFile.delete();
            Log.d(TAG, "临时文件清理: " + (deleted ? "成功" : "失败"));
            tempAudioFile = null;
        }
    }

    private void releaseResources() {
        stop();
    }

    private void notifyPlayStart() {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onAudioPlayStart());
        }
    }

    private void notifyPlayComplete() {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onAudioPlayComplete());
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onAudioPlayError(error));
        }
    }

    private void notifyAudioSaved(File audioFile) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onAudioSaved(audioFile));
        }
    }
}
