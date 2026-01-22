package com.example.myapplication.util;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Base64编码工具类（文件转Base64字符串）
 */
public final class Base64Util {
    private static final String TAG = "Base64Util";
    private static final int BUFFER_SIZE = 4096;

    private Base64Util() {
        // 防止实例化
    }

    /**
     * 将文件转为Base64字符串（不含前缀）
     */
    public static String fileToBase64(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            Log.e(TAG, "文件不存在或无法读取: " + (file != null ? file.getAbsolutePath() : "null"));
            return null;
        }


        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            // Base64编码（默认模式，不含换行符）
            return Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT).replaceAll("\\s+", "");
        } catch (IOException e) {
            Log.e(TAG, "文件转Base64失败", e);
            return null;
        }
    }
}