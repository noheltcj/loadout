import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.detekt") version "2.0.0-alpha.2"
    id("dev.detekt.gradle.compiler-plugin") version "2.0.0-alpha.2"
    kotlin("multiplatform") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("io.kotest") version "6.0.0"
}

group = "com.noheltcj"
version = "0.4.0"

val kotestVersion = "6.0.0"
val cliktVersion = "5.0.3"
val detektVersion = "2.0.0-alpha.2"
val detektTaskNamePattern = Regex("""detekt.+(Main|Test)SourceSet$""")

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

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:$detektVersion")
}

detekt {
    config.setFrom(layout.projectDirectory.file("config/detekt/config.yml"))
    basePath.set(layout.projectDirectory)
    baseline.set(layout.projectDirectory.file("config/detekt/baselines/detekt-baseline.xml"))

    allRules = false
    buildUponDefaultConfig = true
    debug = false
    disableDefaultRuleSets = false
    enableCompilerPlugin.set(true)
}

val lintKotlin by tasks.registering {
    group = "verification"
    description = "Runs type-resolved detekt checks for unused functions and variables."
    dependsOn(
        tasks.withType<Detekt>().matching {
            it.name.matches(detektTaskNamePattern)
        }
    )
}

val formatKotlin by tasks.registering(Detekt::class) {
    group = "formatting"
    description = "Formats the Kotlin source files."
    ignoreFailures.set(true)
    dependsOn(
        tasks.withType<Detekt>().matching {
            it.name.matches(detektTaskNamePattern)
        }
    )
}

val check: Task by tasks.getting {
    setDependsOn(
        dependsOn.filterNot {
            it is TaskProvider<*> && it.name == "detekt"
        }
    )
    dependsOn("lintKotlin")
}

tasks.withType<Detekt>().configureEach {
    reports {
        checkstyle.required.set(false)
        checkstyle.outputLocation.set(layout.buildDirectory.file("reports/detekt/${name}.xml"))
        html.required.set(false)
        html.outputLocation.set(layout.buildDirectory.file("reports/detekt/${name}.html"))
        markdown.required.set(true)
        markdown.outputLocation.set(layout.buildDirectory.file("reports/detekt/${name}.md"))
        sarif.required.set(false)
        sarif.outputLocation.set(layout.buildDirectory.file("reports/detekt/${name}.sarif"))
    }
}
