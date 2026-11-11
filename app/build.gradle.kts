plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.streamdesk.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.streamdesk.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 86
        versionName = "3.65"
    }

    // Keep every build signed with the same key, so a new version always installs over the old one
    signingConfigs {
        getByName("debug") {
            val keystore = rootProject.file("debug.keystore")
            if (keystore.exists()) storeFile = keystore
        }
    }

    buildTypes {
        // What gets installed: shrunk and optimized, so the app opens and scrolls much faster than a debug build.
        // Signed with the same key as the debug builds, so it installs over them without uninstalling.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // About what Android gives one app on a typical phone (largeHeap raises it on some)
            it.maxHeapSize = "1g"   // the screen tests run a whole simulated Android
            // The simulated Android's system files, from Google's copy of Maven Central (the main site often says "too many requests")
            System.getProperty("tvshots")?.let { dir -> it.systemProperty("tvshots", dir) }   // pictures of the TV screens, on request
            System.getProperty("qa")?.let { dir -> it.systemProperty("qa", dir) }   // QA walk-through pictures, on request
            System.getProperty("compat")?.let { dir -> it.systemProperty("compat", dir) }   // compatibility-matrix pictures, on request
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        }
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-datasource-rtmp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
    // Compiles the app's startup code ahead of time when it's installed from a file (no Play Store), for a faster first open
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
}

dependencies {
    // Screen tests: the real activities on a simulated Android (Robolectric), driven like a user
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
