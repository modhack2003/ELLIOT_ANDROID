plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.eiliot.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.eiliot.app"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 24
        versionName = "0.6.3-dev.14"
        buildConfigField("boolean", "VOICE_REFERENCE_CAPTURE", "false")
        buildConfigField("boolean", "VOICE_DIAGNOSTICS", "false")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "VOICE_REFERENCE_CAPTURE", providers.gradleProperty("voiceReferenceCapture").orElse("false").get().toBooleanStrict().toString())
            buildConfigField("boolean", "VOICE_DIAGNOSTICS", providers.gradleProperty("voiceDiagnostics").orElse("false").get().toBooleanStrict().toString())
        }
    }

    testBuildType = "preview"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // All weights are streamed into verified internal files, so APK compression is safe.
    packaging { jniLibs.useLegacyPackaging = true }
    val voiceArm64Only=providers.gradleProperty("voiceArm64Only").orElse("false").get().toBooleanStrict()
    splits { abi { isEnable = true; reset(); if(voiceArm64Only)include("arm64-v8a")else include("arm64-v8a", "armeabi-v7a", "x86", "x86_64"); isUniversalApk = !voiceArm64Only } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.maxHeapSize = "1g"
            // Native Android runtimes from different SDK sandboxes must not share a JVM.
            test.setForkEvery(1)
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { property ->
                System.getProperty(property)?.let { test.systemProperty(property, it) }
            }
            test.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
