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
        versionCode=2
        versionName="0.2.0"
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled=false } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin {
    jvmToolchain(17)
}
