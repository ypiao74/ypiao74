package com.example.myapplication.util;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.R;

import org.json.JSONArray;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 工具定义加载器：从raw资源文件读取工具配置，缓存解析结果
 */
public class ToolLoader {
    private static final String TAG = "ToolLoader";
    private static JSONArray sToolsCache; // 静态缓存，避免重复解析

    /**
     * 加载工具定义（带缓存）
     * @param context 上下文（用于访问raw资源）
     * @return 工具配置JSONArray
     * @throws IOException 读取文件失败
     * @throws JSONException JSON解析失败
     */
    public static JSONArray loadTools(Context context) throws IOException, JSONException {
        // 缓存命中，直接返回
        if (sToolsCache != null) {
            Log.d(TAG, "使用缓存的工具配置，共 " + sToolsCache.length() + " 个工具");
            return sToolsCache;
        }

        // 读取raw资源文件
        InputStream inputStream = context.getResources().openRawResource(R.raw.tools_definition);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        );

        // 拼接文件内容
        StringBuilder contentBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            contentBuilder.append(line.trim()); // 去除换行和空格，减少解析压力
        }

        // 关闭流
        reader.close();
        inputStream.close();

        // 解析JSON并缓存
        sToolsCache = new JSONArray(contentBuilder.toString());
        Log.d(TAG, "成功加载工具配置，共 " + sToolsCache.length() + " 个工具");
        return sToolsCache;
    }

    /**
     * 清除缓存（可选：用于动态更新工具配置时）
     */
    public static void clearCache() {
        sToolsCache = null;
        Log.d(TAG, "工具配置缓存已清除");
    }
}