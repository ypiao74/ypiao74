# 📱 Android Hello World

由 Qwen-Coder 智能体生成的 Android Hello World 应用

## 📁 项目结构

```
android-hello/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/helloworld/
│   │       │   └── MainActivity.kt          ← 主活动
│   │       ├── res/layout/
│   │       │   └── activity_main.xml        ← 布局文件
│   │       └── AndroidManifest.xml          ← 应用清单
│   └── build.gradle                         ← 模块构建配置
├── settings.gradle                          ← 项目设置
└── README.md                                ← 本文件
```

## 🚀 如何运行

### 方式 1：Android Studio（推荐）

1. **打开 Android Studio**
2. **File → Open** → 选择此目录
3. **等待 Gradle 同步**
4. **点击运行按钮** 或 `Shift + F10`
5. **选择模拟器或真机**

### 方式 2：命令行

```bash
# 1. 进入项目目录
cd worktrees/feat/android-hello

# 2. 使用 Gradle Wrapper 构建
./gradlew assembleDebug

# 3. 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📋 系统要求

| 要求 | 版本 |
|------|------|
| **minSdk** | 21 (Android 5.0) |
| **targetSdk** | 34 (Android 14) |
| **compileSdk** | 34 |
| **Kotlin** | 1.8+ |
| **Java** | 1.8+ |

## 🎯 功能

- ✅ Kotlin 编写
- ✅ RelativeLayout 布局
- ✅ 居中显示 Hello World 文本
- ✅ 支持 Android 5.0+

## 📸 预览

```
┌─────────────────────────┐
│                         │
│     ┌─────────────┐     │
│     │             │     │
│     │Hello World! │     │
│     │             │     │
│     └─────────────┘     │
│                         │
└─────────────────────────┘
```

## 🔧 修改文本

编辑 `app/src/main/res/layout/activity_main.xml`:

```xml
<TextView
    android:id="@+id/textView"
    android:text="Hello World!"  ← 修改这里
    ... />
```

## 📦 生成 APK

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (需要签名配置)
./gradlew assembleRelease
```

APK 位置：`app/build/outputs/apk/`

---

**生成时间:** 2026-03-04  
**智能体:** Qwen-Coder  
**任务 ID:** android-hello
