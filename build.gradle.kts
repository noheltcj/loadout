plugins {
    kotlin("multiplatform") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
}

group = "com.noheltcj"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        hostOs.startsWith("Windows") -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:4.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }

        val nativeMain by getting {
            dependsOn(commonMain)
        }

        val nativeTest by getting {
            dependsOn(commonTest)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}