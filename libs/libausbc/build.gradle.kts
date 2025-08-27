plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.jiangdg.ausbc"
    compileSdk = 34

    defaultConfig {
        minSdk = 22
        targetSdk = 22
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.addAll(listOf("armeabi-v7a","arm64-v8a")) // x86, x86_64 still in progress
        externalNativeBuild {
            cmake {
                val gstRoot = project.properties["GSTREAMER_ROOT_ANDROID"]?.toString() 
                    ?: System.getenv("GSTREAMER_ROOT_ANDROID")
                    ?: "${System.getProperty("user.home")}/Work/Programming/Workspace/Storage/gstreamer-android-1.26.0"
                
                println("Using GSTREAMER_ROOT_ANDROID: $gstRoot")
                arguments += "-DGSTREAMER_ROOT_ANDROID=$gstRoot"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    externalNativeBuild {
        cmake {
            path = File(projectDir, "CMakeLists.txt")
        }
    }

    android {
        lint {
            abortOnError = false
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.timber)
}
