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
        versionCode=3
        versionName="0.2.2"
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled=false } }
    dependencies { implementation("androidx.webkit:webkit:1.14.0") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin {
    jvmToolchain(17)
}
