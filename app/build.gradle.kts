plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.kommunalka.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.kommunalka.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Ключ подписи берётся из файла release.jks в корне проекта и переменной окружения
    // KEYSTORE_PASSWORD (их подставляет сборка на GitHub из секретов). Сам ключ в репозиторий не кладётся.
    val keystoreFile = rootProject.file("release.jks")
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    val canSignRelease = keystoreFile.exists() && !keystorePassword.isNullOrEmpty()

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "kommunalka"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Камера
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Распознавание текста на устройстве, без интернета
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
