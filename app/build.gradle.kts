plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun bc(name: String, fallback: String = ""): String {
    val value = System.getenv(name) ?: fallback
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    return "\"$escaped\""
}

android {
    namespace = "com.fworld.fstore"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fworld.fstore"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.4"

        buildConfigField("String", "GH_OWNER", bc("GH_OWNER", "FaraonTT"))
        buildConfigField("String", "GH_REPO", bc("GH_REPO", "fStore"))

        // FSTORE_TOKEN из GitHub Secrets передаётся в BuildConfig.GH_TOKEN
        buildConfigField("String", "GH_TOKEN", bc("FSTORE_TOKEN"))

        buildConfigField("String", "INBOX_TOKEN", bc("INBOX_TOKEN"))
        buildConfigField("String", "DROP_TOKEN", bc("DROP_TOKEN"))
        buildConfigField("String", "SERVER_PUB", bc("FSTORE_SERVER_PUB"))
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("fstore.keystore")
            storePassword = System.getenv("KS_PASS") ?: ""
            keyAlias = "fstore"
            keyPassword = System.getenv("KS_PASS") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.core:core-ktx:1.16.0")
    // Фоновая проверка новых релизов для уведомлений
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
