plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.naang360.missminutes"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.naang360.missminutes"
        minSdk = 28
        targetSdk = 36
        versionCode = 57
        versionName = "0.9.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
}
kotlin { jvmToolchain(17) }
