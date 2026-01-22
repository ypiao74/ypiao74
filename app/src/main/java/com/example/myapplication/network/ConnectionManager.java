package com.example.myapplication.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Response;

/**
 * HTTP连接管理器
 * 负责管理HTTP请求的生命周期：创建、取消、关闭
 */
public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    
    // 当前正在进行的请求
    private volatile Call currentCall;
    
    // 当前正在读取的 BufferedReader（用于取消时立即关闭）
    private volatile BufferedReader currentReader;
    
    /**
     * 设置当前请求的Call对象
     * @param call OkHttp的Call对象
     */
    public void setCurrentCall(Call call) {
        this.currentCall = call;
    }
    
    /**
     * 获取当前请求的Call对象
     * @return 当前Call对象，如果不存在则返回null
     */
    public Call getCurrentCall() {
        return currentCall;
    }
    
    /**
     * 设置当前正在读取的 BufferedReader
     * @param reader BufferedReader对象
     */
    public void setCurrentReader(BufferedReader reader) {
        this.currentReader = reader;
    }
    
    /**
     * 清除当前 BufferedReader 引用
     */
    public void clearReader() {
        this.currentReader = null;
    }
    
    /**
     * 取消当前正在进行的请求
     * 如果请求已完成或已取消，则不会执行任何操作
     */
    public void cancelCurrentRequest() {
        // 先关闭 BufferedReader（如果存在）
        if (currentReader != null) {
            try {
                Log.d(TAG, "取消请求时关闭 BufferedReader");
                closeReader(currentReader);
            } catch (Exception e) {
                Log.w(TAG, "关闭 BufferedReader 失败", e);
            } finally {
                currentReader = null;
            }
        }
        
        // 然后取消 HTTP 请求
        if (currentCall != null && !currentCall.isCanceled()) {
            Log.d(TAG, "取消当前请求");
            currentCall.cancel();
            currentCall = null;
        }
    }
    
    /**
     * 取消当前请求（如果满足条件）
     * @param shouldCancel 是否应该取消
     */
    public void cancelIfNeeded(boolean shouldCancel) {
        if (shouldCancel) {
            cancelCurrentRequest();
        }
    }
    
    /**
     * 安全关闭BufferedReader
     * @param reader 要关闭的BufferedReader
     */
    public static void closeReader(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
                Log.d(TAG, "BufferedReader已关闭");
            } catch (IOException e) {
                Log.w(TAG, "关闭BufferedReader失败", e);
            }
        }
    }
    
    /**
     * 安全关闭Response对象
     * @param response 要关闭的Response对象
     */
    public static void closeResponse(Response response) {
        if (response != null) {
            try {
                response.close();
                Log.d(TAG, "Response已关闭");
            } catch (Exception e) {
                Log.w(TAG, "关闭Response失败", e);
            }
        }
    }
    
    /**
     * 关闭所有资源：取消请求、关闭流、关闭响应
     * @param reader BufferedReader对象（可为null）
     * @param response Response对象（可为null）
     * @param shouldCancelCall 是否取消Call请求
     */
    public void closeAll(BufferedReader reader, Response response, boolean shouldCancelCall) {
        // 1. 关闭流
        closeReader(reader);
        
        // 2. 取消请求（如果需要）
        if (shouldCancelCall) {
            cancelCurrentRequest();
        }
        
        // 3. 关闭响应
        closeResponse(response);
    }
    
    /**
     * 关闭所有资源（默认不取消Call）
     * @param reader BufferedReader对象（可为null）
     * @param response Response对象（可为null）
     */
    public void closeAll(BufferedReader reader, Response response) {
        closeAll(reader, response, false);
    }
    
    /**
     * 清除当前Call引用（请求完成后调用）
     * @param call 要清除的Call对象
     */
    public void clearCall(Call call) {
        if (currentCall == call) {
            currentCall = null;
            Log.d(TAG, "已清除Call引用");
        }
    }
    
    /**
     * 检查当前是否有正在进行的请求
     * @return true表示有请求正在进行，false表示没有
     */
    public boolean hasActiveRequest() {
        return currentCall != null && !currentCall.isCanceled();
    }
    
    /**
     * 重置连接管理器（清除所有引用）
     */
    public void reset() {
        cancelCurrentRequest();
        Log.d(TAG, "连接管理器已重置");
    }
}
