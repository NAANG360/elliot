plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="com.naang360.missminutes"
    compileSdk=36
    defaultConfig {
        applicationId="com.naang360.missminutes"
        minSdk=28
        targetSdk=36
        versionCode=53
        versionName="0.8.8"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
    signingConfigs {
        create("release") {
            storeFile=file("miss-minutes-release.jks")
            storePassword="***REMOVED***"
            keyAlias="missminutes"
            keyPassword="***REMOVED***"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled=false
            signingConfig=signingConfigs.getByName("release")
        }
    }
    externalNativeBuild { cmake { path=file("src/main/cpp/CMakeLists.txt") } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }\n\ndependencies {\n    implementation("androidx.documentfile:documentfile:1.1.0")\n    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.8")\n}
}
kotlin {
    jvmToolchain(17)
}