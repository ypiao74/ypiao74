plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.OmniMobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ✅ Kotlin DSL 正确的签名配置（核心修正！）
    signingConfigs {
        create("release") { // 必须用create("release")创建配置
            storeFile = file("../AppSignedKey/g9phplatform.jks") // 加=
            storePassword = "aa123456" // 加=+双引号
            keyAlias = "g9ph" // 加=+双引号
            keyPassword = "aa123456" // 加=+双引号
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // proguardFiles必须用listOf包裹（Kotlin DSL要求）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release") // 加=，关联签名
        }
        debug {
            // 系统应用：debug版本也需要使用系统签名
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // OpenAI-Java SDK
    implementation("com.openai:openai-java:4.15.0")
    // 阿里云DashScope SDK
    implementation("com.alibaba:dashscope-sdk-java:2.21.8")
    // Lifecycle组件
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // 协程依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}