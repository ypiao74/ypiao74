package com.example.myapplication.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 图片工具类
 * 用于检测图片格式和处理图片
 */
public final class ImageUtil {
    private static final String TAG = "ImageUtil";

    private ImageUtil() {
        // 防止实例化
    }

    /**
     * 根据文件扩展名检测图片格式
     *
     * @param file 图片文件
     * @return 图片格式（jpeg, png, webp），如果无法识别则返回 "jpeg"
     */
    public static String detectImageFormat(File file) {
        if (file == null || !file.exists()) {
            Log.w(TAG, "文件不存在，返回默认格式 jpeg");
            return "jpeg";
        }

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "jpeg";
        } else if (fileName.endsWith(".png")) {
            return "png";
        } else if (fileName.endsWith(".webp")) {
            return "webp";
        }

        // 尝试通过文件头检测
        try {
            return detectImageFormatByHeader(file);
        } catch (IOException e) {
            Log.w(TAG, "通过文件头检测格式失败，返回默认格式 jpeg", e);
            return "jpeg";
        }
    }

    /**
     * 通过文件头检测图片格式
     *
     * @param file 图片文件
     * @return 图片格式
     * @throws IOException IO异常
     */
    private static String detectImageFormatByHeader(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int bytesRead = fis.read(header);
            if (bytesRead < 4) {
                return "jpeg"; // 默认格式
            }

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "jpeg";
            }

            // PNG: 89 50 4E 47
            if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                return "png";
            }

            // WebP: RIFF ... WEBP
            if (bytesRead >= 12 &&
                header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "webp";
            }

            return "jpeg"; // 默认格式
        }
    }

    /**
     * 验证图片文件是否有效
     *
     * @param file 图片文件
     * @return 是否有效
     */
    public static boolean isValidImageFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return false;
        }

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            return options.outWidth > 0 && options.outHeight > 0;
        } catch (Exception e) {
            Log.e(TAG, "验证图片文件失败", e);
            return false;
        }
    }

    /**
     * 验证图片是否符合 API 要求
     * 根据文档要求：
     * - 单个图片文件大小不超过 10 MB
     * - 图片宽度和高度均应大于 10 像素
     * - 宽高比不应超过 200:1 或 1:200
     *
     * @param file 图片文件
     * @return 验证结果，包含是否通过和错误信息
     */
    public static ImageValidationResult validateImageForApi(File file) {
        if (file == null || !file.exists()) {
            return new ImageValidationResult(false, "图片文件不存在");
        }

        // 检查文件大小（10 MB = 10 * 1024 * 1024 字节）
        long fileSize = file.length();
        long maxSize = 10 * 1024 * 1024; // 10 MB
        if (fileSize > maxSize) {
            double sizeMB = fileSize / (1024.0 * 1024.0);
            return new ImageValidationResult(false, 
                String.format("图片文件过大: %.2f MB，最大支持 10 MB", sizeMB));
        }

        // 检查图片尺寸
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            
            int width = options.outWidth;
            int height = options.outHeight;

            if (width <= 10 || height <= 10) {
                return new ImageValidationResult(false, 
                    String.format("图片尺寸过小: %dx%d，最小支持 10x10 像素", width, height));
            }

            // 检查宽高比（不应超过 200:1 或 1:200）
            double aspectRatio;
            if (width > height) {
                aspectRatio = (double) width / height;
            } else {
                aspectRatio = (double) height / width;
            }

            if (aspectRatio > 200) {
                return new ImageValidationResult(false, 
                    String.format("图片宽高比过大: %.2f:1，最大支持 200:1", aspectRatio));
            }

            return new ImageValidationResult(true, "验证通过");
        } catch (Exception e) {
            Log.e(TAG, "验证图片尺寸失败", e);
            return new ImageValidationResult(false, "无法读取图片尺寸: " + e.getMessage());
        }
    }

    /**
     * 图片验证结果类
     */
    public static class ImageValidationResult {
        private final boolean isValid;
        private final String message;

        public ImageValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getMessage() {
            return message;
        }
    }
}
