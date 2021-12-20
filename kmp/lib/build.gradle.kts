import org.jetbrains.compose.compose

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.0.0"
    id("com.android.library")
}

kotlin {
    android()

    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.foundation)
                implementation(libs.napier)
            }
        }

        val commonTest by getting {
            dependencies {
                api(libs.junit)
                api(libs.truth)
                api(compose("org.jetbrains.compose.ui:ui-test-junit4"))
            }
        }

        val androidMain by getting
        val androidTest by getting

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val desktopTest by getting
    }
}

android {
    compileSdk = 31
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 24
        targetSdk = 31
    }

    packagingOptions {
        resources.pickFirsts += "/META-INF/AL2.0"
        resources.pickFirsts += "/META-INF/LGPL2.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}