plugins {
    kotlin("multiplatform")
    alias(libs.plugins.jetbrainsCompose)
    id("com.android.library")
    id("maven-publish")
    id("kotlinx-atomicfu")
}

kotlin {
    android {
        publishLibraryVariants("release")

        mavenPublication {
            artifactId = "${project.ext["POM_ARTIFACT_ID"].toString()}-android"
        }
    }

    iosX64()
    iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.foundation)
            }
        }

        val commonTest by getting
        val jvmCommonTest by creating

        val androidMain by getting
        val androidTest by getting {
            dependsOn(jvmCommonTest)
            dependencies {
                implementation(project(":internal-testutils"))
                implementation(libs.junit)
                implementation(libs.truth)
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
                implementation(libs.androidx.test.runner)
                implementation(libs.robolectric)
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting

        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
        }

        val iosX64Test by getting
        val iosArm64Test by getting

        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
        }
    }
}

android {
    compileSdk = 33
    namespace = "dev.chrisbanes.snapper"

    defaultConfig {
        minSdk = 24
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

publishing {
    publications {
        this.withType(MavenPublication::class) {
            group = project.ext["GROUP"].toString()
            version = project.ext["VERSION_NAME"].toString()
            // TODO make it nicer
            artifactId = artifactId.replace("lib", project.ext["POM_ARTIFACT_ID"].toString())

            pom {
                name.set(project.ext["POM_NAME"].toString())
                url.set(project.ext["POM_URL"].toString())
                scm {
                    url.set(project.ext["POM_SCM_URL"].toString())
                    connection.set(project.ext["POM_SCM_CONNECTION"].toString())
                    developerConnection.set(project.ext["POM_SCM_DEV_CONNECTION"].toString())
                }
                licenses {
                    license {
                        name.set(project.ext["POM_LICENCE_NAME"].toString())
                        url.set(project.ext["POM_LICENCE_URL"].toString())
                        distribution.set(project.ext["POM_LICENCE_DIST"].toString())
                    }
                }

                developers {
                    developer {
                        id.set(project.ext["POM_DEVELOPER_ID"].toString())
                        name.set(project.ext["POM_DEVELOPER_NAME"].toString())
                    }
                }
            }
        }
    }
}
