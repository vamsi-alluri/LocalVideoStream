plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.PineApple.VideoStream"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.PineApple.VideoStream"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // Forces the linker to use 16 KB alignment for any native code you compile
                arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
            }
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
    }
    packaging {
        jniLibs {
            // This forces the packaging of .so files to be aligned
            // to 16 KB boundaries, making them compatible with 16 KB devices.
            useLegacyPackaging = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // ExoPlayer for playing RTMP streams
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // If you need specific RTMP data source support (sometimes needed depending on server type)
    implementation(libs.androidx.media3.datasource.rtmp)
    // RootEncoder: Handles Camera, Audio, and RTSP Server logic
    implementation(libs.library)
    implementation(libs.androidx.media3.exoplayer.rtsp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.github.pedroSG94:RTSP-Server:Tag")
    implementation("com.github.pedroSG94:RTSP-Server:1.3.6")
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.1")
}