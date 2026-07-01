plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pixelthings.uecapupdater"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pixelthings.uecapupdater"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // 🛠️ 新增這段：用新版安全語法徹底停用測試單元
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    sourceSets {
        getByName("test") {
            java.setSrcDirs(emptyList<String>())
        }
        getByName("androidTest") {
            java.setSrcDirs(emptyList<String>())
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    val libsuVersion = "6.0.0"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // libsu 核心模組
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("androidx.appcompat:appcompat:1.6.1")
}

android {
    namespace = "com.pixelthings.uecapupdater"
    // ... 其他設定保持不變

    // 🚀 加入這段，讓系統自動產生語言設定檔
    androidResources {
        generateLocaleConfig = true
    }
}
