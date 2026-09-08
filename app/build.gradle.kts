// 🚀 關鍵修復 1：必須在檔案最頂端匯入 Protobuf 擴充函式庫，才能消除紅線並解決語法衝突
import com.google.protobuf.gradle.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.protobuf)
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

    androidResources {
        generateLocaleConfig = true
    }

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
    implementation("androidx.compose.material:material-icons-extended")

    // libsu 核心模組
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // 新增 Protobuf Java Lite (適合 Android 輕量化使用)
    implementation("com.google.protobuf:protobuf-javalite:3.24.4")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.4"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                // 🚀 關鍵修復 2：有了最上方的 import 後，這裡就能安全使用 id() 且不會報錯了
                id("java") {
                    option("lite")
                }
            }
        }
    }
}