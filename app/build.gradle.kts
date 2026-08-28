plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.geckoflux"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.geckoflux"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Enable ABI splits if desired, or support all standard ABIs
        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    flavorDimensions += "app"
    productFlavors {
        create("youtube") {
            dimension = "app"
            applicationId = "com.geckoflux.tube"
            versionNameSuffix = "-tube"
            manifestPlaceholders["appName"] = "GeckoTube"
            buildConfigField("String", "APP_PROFILE", "\"YOUTUBE\"")
        }
        create("music") {
            dimension = "app"
            applicationId = "com.geckoflux.music"
            versionNameSuffix = "-music"
            manifestPlaceholders["appName"] = "GeckoMusic"
            buildConfigField("String", "APP_PROFILE", "\"YOUTUBE_MUSIC\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // GeckoView - Mozilla Firefox Browser Engine for Android (128 ESR LTS)
    implementation("org.mozilla.geckoview:geckoview-omni:128.0.20240725162350")

    // AndroidX & UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
