import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask

plugins {
    kotlin("multiplatform") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("io.kotest") version "6.0.0"
    id("org.jmailen.kotlinter") version "5.2.0"
}

group = "com.noheltcj"
version = "0.4.0"

val kotestVersion = "6.0.0"
val cliktVersion = "5.0.3"

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
                implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation("io.kotest:kotest-framework-engine:$kotestVersion")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            }
        }

        val nativeMain by registering {
            dependsOn(commonMain)
        }

        val nativeTest by registering {
            dependsOn(commonTest)
        }

        val linuxX64Main by extending(nativeMain)
        val linuxArm64Main by extending(nativeMain)

        val macosX64Main by extending(nativeMain)
        val macosArm64Main by extending(nativeMain)

        val mingwX64Main by extending(nativeMain)

        val linuxX64Test by extending(nativeTest)
        val linuxArm64Test by extending(nativeTest)

        val macosX64Test by extending(nativeTest)
        val macosArm64Test by extending(nativeTest)

        val mingwX64Test by extending(nativeTest)
    }
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

tasks.withType<ConfigurableKtLintTask>().configureEach {
    exclude { element ->
        element.file
            .invariantSeparatorsPath
            .contains("/build/generated/")
    }
}
