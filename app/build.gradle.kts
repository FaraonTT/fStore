plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fworld.fstore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fworld.fstore"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.1b"
        buildConfigField("String", "GH_OWNER", "\"${System.getenv("GH_OWNER") ?: "FaraonTT"}\"")
        buildConfigField("String", "GH_REPO", "\"${System.getenv("GH_REPO") ?: "fStore"}\"")
        buildConfigField("String", "GH_TOKEN", "\"${System.getenv("FSTORE_TOKEN") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("fstore.keystore")
            storePassword = "fstore123"
            keyAlias = "fstore"
            keyPassword = "fstore123"
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
    // Обновлённый BOM — даёт material3 1.5.x с Expressive API
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
