plugins {
    id("org.jetbrains.compose") version "1.0.0"
    id("com.android.application")
    kotlin("android")
}

group = "me.chris"
version = "1.0"

dependencies {
    implementation(project(":kmp:lib"))
    implementation("androidx.activity:activity-compose:1.3.0")
}

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "me.chris.android"
        minSdk = 24
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}