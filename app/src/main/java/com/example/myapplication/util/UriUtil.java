package com.example.myapplication.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * URI 工具类
 * 用于处理各种 URI 类型，特别是 Android 10+ 的 content:// URI
 */
public final class UriUtil {
    private static final String TAG = "UriUtil";

    private UriUtil() {
        // 防止实例化
    }

    /**
     * 从 URI 读取文件并转换为临时文件
     * 适用于 content://、file:// 等所有 URI 类型
     *
     * @param context 上下文
     * @param uri     文件 URI
     * @return 临时文件，如果失败返回 null
     */
    public static File uriToFile(Context context, Uri uri) {
        if (uri == null) {
            Log.e(TAG, "URI 为空");
            return null;
        }

        try {
            // 如果是 file:// 协议，直接返回文件
            if ("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) {
                        return file;
                    }
                }
            }

            // 对于 content:// 协议，需要读取内容并创建临时文件
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "无法打开输入流: " + uri);
                return null;
            }

            // 创建临时文件
            File tempFile = File.createTempFile("image_", ".tmp", context.getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(tempFile);

            // 复制数据
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            // 关闭流
            outputStream.close();
            inputStream.close();

            Log.d(TAG, "成功将 URI 转换为临时文件: " + tempFile.getAbsolutePath());
            return tempFile;

        } catch (Exception e) {
            Log.e(TAG, "URI 转文件失败: " + uri, e);
            return null;
        }
    }

    /**
     * 从 URI 直接读取为 Base64 字符串
     * 适用于小文件，避免创建临时文件
     *
     * @param context 上下文
     * @param uri     文件 URI
     * @return Base64 字符串，如果失败返回 null
     */
    public static String uriToBase64(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            // 读取所有数据
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] data = outputStream.toByteArray();
            inputStream.close();
            outputStream.close();

            // 转换为 Base64
            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "URI 转 Base64 失败: " + uri, e);
            return null;
        }
    }
}
