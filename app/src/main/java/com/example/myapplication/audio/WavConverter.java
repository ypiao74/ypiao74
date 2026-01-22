package com.example.myapplication.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * WAV格式转换工具类（职责：仅处理PCM转WAV）
 */
public class WavConverter {

    private WavConverter() {
        // 私有构造，防止实例化
    }

    /**
     * PCM文件转WAV文件
     * @param pcmFile 输入PCM文件
     * @param wavFile 输出WAV文件
     * @param sampleRate 采样率（如16000）
     * @param channels 声道数（如1）
     * @param bitsPerSample 位宽（如16）
     */
    public static void pcmToWav(File pcmFile, File wavFile, int sampleRate, int channels, int bitsPerSample) throws IOException {
        byte[] pcmData = readPcmData(pcmFile);
        writeWavHeaderAndData(wavFile, pcmData, sampleRate, channels, bitsPerSample);
    }

    /**
     * 读取PCM文件数据
     */
    private static byte[] readPcmData(File pcmFile) throws IOException {
        byte[] pcmData = new byte[(int) pcmFile.length()];
        try (FileInputStream fis = new FileInputStream(pcmFile)) {
            fis.read(pcmData);
        }
        return pcmData;
    }

    /**
     * 写入WAV文件头和数据
     */
    private static void writeWavHeaderAndData(File wavFile, byte[] pcmData, int sampleRate, int channels, int bitsPerSample) throws IOException {
        int totalDataLen = pcmData.length + 36;
        int byteRate = sampleRate * channels * bitsPerSample / 8;

        try (FileOutputStream fos = new FileOutputStream(wavFile)) {
            // 写入WAV头
            fos.write("RIFF".getBytes());
            fos.write(intToByteArray(totalDataLen), 0, 4);
            fos.write("WAVE".getBytes());
            fos.write("fmt ".getBytes());
            fos.write(intToByteArray(16), 0, 4); // Subchunk1Size
            fos.write(shortToByteArray((short) 1), 0, 2); // PCM格式
            fos.write(shortToByteArray((short) channels), 0, 2);
            fos.write(intToByteArray(sampleRate), 0, 4);
            fos.write(intToByteArray(byteRate), 0, 4);
            fos.write(shortToByteArray((short) (channels * bitsPerSample / 8)), 0, 2);
            fos.write(shortToByteArray((short) bitsPerSample), 0, 2);
            fos.write("data".getBytes());
            fos.write(intToByteArray(pcmData.length), 0, 4);
            // 写入PCM数据
            fos.write(pcmData);
        }
    }

    /**
     * int转字节数组（小端）
     */
    private static byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    /**
     * short转字节数组（小端）
     */
    private static byte[] shortToByteArray(short value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }
}