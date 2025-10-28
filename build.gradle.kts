import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("org.jmailen.kotlinter") version "5.2.0"
}

group = "com.noheltcj"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    macosX64().configureBinaries()
    macosArm64().configureBinaries()

    linuxArm64().configureBinaries()
    linuxX64().configureBinaries()

    mingwX64().configureBinaries()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:4.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val nativeMain by registering {
            dependsOn(commonMain)
        }

        val linuxX64Main by extending(nativeMain)
        val linuxArm64Main by extending(nativeMain)

        val macosX64Main by extending(nativeMain)
        val macosArm64Main by extending(nativeMain)

        val mingwX64Main by extending(nativeMain)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

fun NamedDomainObjectContainer<KotlinSourceSet>.extending(
    parentSourceSet: NamedDomainObjectProvider<KotlinSourceSet>,
    configure: KotlinSourceSet.() -> Unit = {}
): NamedDomainObjectCollectionDelegateProvider<KotlinSourceSet> {
    val parent by parentSourceSet
    return getting {
        dependsOn(parent)
        configure()
    }
}

fun KotlinNativeTarget.configureBinaries() {
    binaries {
        executable {
            entryPoint = "main"
        }
    }
}