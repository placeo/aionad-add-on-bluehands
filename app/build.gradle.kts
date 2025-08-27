plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.skt.photobox"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.skt.photobox"
        minSdk = 22
        targetSdk = 22
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                val gstRoot = project.findProperty("GSTREAMER_ROOT_ANDROID")?.toString()
                    ?: System.getenv("GSTREAMER_ROOT_ANDROID")
                    ?: "${System.getProperty("user.home")}/Work/Programming/Workspace/Storage/gstreamer-android-1.26.0"
                println("Using GSTREAMER_ROOT_ANDROID: $gstRoot")
                arguments += listOf("-DANDROID_STL=c++_shared", "-DGSTREAMER_ROOT_ANDROID=$gstRoot", "-GNinja")
                targets += "PhotoBox"
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            // No specific configuration needed for debug, it uses the default.
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    sourceSets {
        getByName("main") {
            val gstRoot = project.findProperty("GSTREAMER_ROOT_ANDROID")?.toString()
                ?: System.getenv("GSTREAMER_ROOT_ANDROID")
                ?: "${System.getProperty("user.home")}/Work/Programming/Workspace/Storage/gstreamer-android-1.26.0"
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src", "../libs/libausbc/src/main/java", "$gstRoot/share/gst-android/java")
            res.srcDirs("res", "../libs/libausbc/src/main/res")
            assets.srcDirs("src/assets", "../conf")
            jni.srcDirs("src/main/jni", "../libs/libausbc/src/main/jni")
        }
    }

    packagingOptions {
        pickFirst("**/libgstreamer_android.so")
        pickFirst("**/lib/armeabi-v7a/libgstreamer_android.so")
        pickFirst("**/lib/arm64-v8a/libgstreamer_android.so")
        exclude("META-INF/INDEX.LIST")
    }

    externalNativeBuild {
        cmake {
            path = file("jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        abortOnError = false
    }

    ndkVersion = "25.2.9519653"
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.transition:transition:1.5.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.material:material:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    // libausbc 의존성
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Ktor
    val ktor_version = "2.3.12"
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:1.3.11")
}
