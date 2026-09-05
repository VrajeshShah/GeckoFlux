plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.geckoflux"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geckoflux"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

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
        create("suite") {
            dimension = "app"
            applicationId = "com.geckoflux"
            manifestPlaceholders["appName"] = "GeckoFlux"
            manifestPlaceholders["tubeEnabled"] = "true"
            manifestPlaceholders["musicEnabled"] = "true"
            buildConfigField("String", "TARGET_URL", "\"https://m.youtube.com\"")
        }
        create("youtube") {
            dimension = "app"
            applicationId = "com.geckoflux.tube"
            manifestPlaceholders["appName"] = "GeckoTube"
            manifestPlaceholders["tubeEnabled"] = "true"
            manifestPlaceholders["musicEnabled"] = "false"
            buildConfigField("String", "TARGET_URL", "\"https://m.youtube.com\"")
        }
        create("music") {
            dimension = "app"
            applicationId = "com.geckoflux.music"
            manifestPlaceholders["appName"] = "GeckoMusic"
            manifestPlaceholders["tubeEnabled"] = "false"
            manifestPlaceholders["musicEnabled"] = "true"
            buildConfigField("String", "TARGET_URL", "\"https://music.youtube.com\"")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = "geckofluxpass"
            keyAlias = "geckoflux"
            keyPassword = "geckofluxpass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    implementation("org.mozilla.geckoview:geckoview:154.0.20260824154132")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    }
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}