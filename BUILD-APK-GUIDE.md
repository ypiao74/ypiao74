# 📱 Android Hello World - APK 构建指南

**项目位置：** `worktrees/feat/android-hello/`

---

## 🎯 方案 1：Android Studio（最简单）⭐

### 步骤

```
1. 下载并安装 Android Studio
   https://developer.android.com/studio

2. 打开项目
   File → Open → 选择 worktrees/feat/android-hello

3. 等待 Gradle 同步完成

4. 生成 APK
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   
   或按快捷键：Ctrl+Shift+A → 输入 "Build APK"
```

### APK 位置

```
worktrees/feat/android-hello/app/build/outputs/apk/debug/app-debug.apk
```

### 安装到手机

```bash
# 通过 ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# 或直接传输 APK 文件到手机安装
```

---

## 🎯 方案 2：GitHub Actions 自动构建

### 已配置工作流

项目已包含 `.github/workflows/build.yml`

### 触发构建

```bash
cd worktrees/feat/android-hello
git add .
git commit -m "build: Trigger APK build"
git push origin feat/android-hello
```

### 下载 APK

```
1. 访问：https://github.com/ypiao74/ypiao74/actions
2. 点击最新的 "Build Android APK"
3. 下载 "app-debug"  artifact
```

---

## 🎯 方案 3：命令行构建

### 安装依赖

```bash
# 1. 安装 JDK
sudo apt-get install openjdk-17-jdk

# 2. 下载 Gradle
cd /tmp
wget https://services.gradle.org/distributions/gradle-8.5-bin.zip
unzip gradle-8.5-bin.zip
sudo mv gradle-8.5 /opt/gradle
sudo ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle

# 3. 安装 Android SDK
# 下载 Command Line Tools: https://developer.android.com/studio#command-tools
```

### 构建命令

```bash
cd worktrees/feat/android-hello

# Debug APK
gradle assembleDebug

# Release APK (需要签名配置)
gradle assembleRelease
```

### APK 位置

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

---

## 🎯 方案 4：在线构建服务

### 使用 Codemagic

```
1. 访问：https://codemagic.io/
2. 连接 GitHub 仓库
3. 选择 android-hello 项目
4. 自动构建并下载 APK
```

---

## 📋 项目信息

### 配置

| 项目 | 值 |
|------|-----|
| **minSdk** | 21 (Android 5.0) |
| **targetSdk** | 34 (Android 14) |
| **compileSdk** | 34 |
| **Kotlin** | 1.8+ |
| **应用 ID** | com.example.helloworld |

### 文件结构

```
android-hello/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/helloworld/
│   │   │   └── MainActivity.kt
│   │   ├── res/layout/
│   │   │   └── activity_main.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
└── settings.gradle
```

---

## 🔧 快速构建脚本

创建 `build.sh`：

```bash
#!/bin/bash
cd "$(dirname "$0")"

echo "🔨 Building Android APK..."

# 检查 Gradle
if ! command -v gradle &> /dev/null; then
    echo "❌ Gradle not found. Please install Android Studio or Gradle."
    exit 1
fi

# 构建
gradle assembleDebug

# 检查输出
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ APK built successfully!"
    echo "📍 Location: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed"
    exit 1
fi
```

---

## 📱 安装 APK

### 方式 1：ADB

```bash
adb install app-debug.apk
```

### 方式 2：直接传输

```
1. 复制 APK 到手机
2. 在手机上打开 APK 文件
3. 允许安装未知来源
4. 点击安装
```

---

## ⚠️ 常见问题

### Gradle 同步失败

```
解决：File → Invalidate Caches / Restart
```

### SDK 未找到

```
解决：File → Project Structure → SDK Location
```

### 构建超时

```
解决：增加 Gradle 内存
org.gradle.jvmargs=-Xmx2048m
```

---

**推荐：** 使用 Android Studio（方案 1），最简单可靠！
