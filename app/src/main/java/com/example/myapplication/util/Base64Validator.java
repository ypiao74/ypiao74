package com.example.myapplication.util;

/**
 * Base64验证工具类
 */
public final class Base64Validator {
    private Base64Validator() {
        // 防止实例化
    }

    private static final String BASE64_REGEX = "^[A-Za-z0-9+/=]*$";

    /**
     * 验证字符串是否为有效的Base64格式
     */
    public static boolean isValid(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return false;
        }
        return base64.matches(BASE64_REGEX);
    }

    /**
     * 清理并规范化Base64字符串
     */
    public static String normalize(String base64) {
        if (base64 == null) {
            return "";
        }
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
}


