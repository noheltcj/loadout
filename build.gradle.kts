import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.util.Locale

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

    allRules = false
    basePath = layout.projectDirectory
    baseline = layout.projectDirectory.file("config/detekt/baselines/detekt-baseline.xml")
    buildUponDefaultConfig = true

    disableDefaultRuleSets = true
    enableCompilerPlugin = true

    debug = false
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

val formatKotlin by tasks.registering {
    group = "formatting"
    description = "Formats the Kotlin source files."
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

val formatKotlinRequested =
    providers.provider {
        gradle.startParameter.taskNames.any { taskName ->
            taskName.substringAfterLast(':') == "formatKotlin"
        }
    }

tasks.withType<Detekt>().configureEach {
    if (name.matches(detektTaskNamePattern)) {
        autoCorrect.convention(formatKotlinRequested)
        ignoreFailures.convention(formatKotlinRequested)
    }

    // Generated KSP output is not repo-owned code and should not gate lint.
    exclude { element ->
        element.file.invariantSeparatorsPath.contains("/build/generated/")
    }

    reports {
        checkstyle.required = false
        checkstyle.outputLocation = layout.buildDirectory.file("reports/detekt/$name.xml")
        html.required = false
        html.outputLocation = layout.buildDirectory.file("reports/detekt/$name.html")
        markdown.required = true
        markdown.outputLocation = layout.buildDirectory.file("reports/detekt/$name.md")
        sarif.required = false
        sarif.outputLocation = layout.buildDirectory.file("reports/detekt/$name.sarif")
    }
}

data class HostBinarySpec(
    val targetName: String,
    val targetTaskSuffix: String,
    val executableExtension: String,
    val helperScriptFileName: String,
)

private val POSIX_HELPER_SCRIPT_NAME = "loadout-e2e-helper.sh"
private val WINDOWS_HELPER_SCRIPT_NAME = "loadout-e2e-helper.cmd"
private val LOADOUT_BIN_ENVIRONMENT_VARIABLE = "LOADOUT_BIN"
private val LOADOUT_E2E_BINARY_PATH_ENVIRONMENT_VARIABLE = "LOADOUT_E2E_BINARY_PATH"
private val LOADOUT_E2E_HELPER_PATH_ENVIRONMENT_VARIABLE = "LOADOUT_E2E_HELPER_PATH"

fun currentHostBinarySpec(): HostBinarySpec {
    val operatingSystem = System.getProperty("os.name").lowercase(Locale.US)
    val architecture = System.getProperty("os.arch").lowercase(Locale.US)

    return when {
        operatingSystem == "mac os x" && (architecture == "aarch64" || architecture == "arm64") ->
            HostBinarySpec(
                targetName = "macosArm64",
                targetTaskSuffix = "MacosArm64",
                executableExtension = ".kexe",
                helperScriptFileName = POSIX_HELPER_SCRIPT_NAME
            )
        operatingSystem == "mac os x" ->
            HostBinarySpec(
                targetName = "macosX64",
                targetTaskSuffix = "MacosX64",
                executableExtension = ".kexe",
                helperScriptFileName = POSIX_HELPER_SCRIPT_NAME
            )
        operatingSystem == "linux" && (architecture == "aarch64" || architecture == "arm64") ->
            HostBinarySpec(
                targetName = "linuxArm64",
                targetTaskSuffix = "LinuxArm64",
                executableExtension = ".kexe",
                helperScriptFileName = POSIX_HELPER_SCRIPT_NAME
            )
        operatingSystem == "linux" ->
            HostBinarySpec(
                targetName = "linuxX64",
                targetTaskSuffix = "LinuxX64",
                executableExtension = ".kexe",
                helperScriptFileName = POSIX_HELPER_SCRIPT_NAME
            )
        operatingSystem.startsWith("windows") ->
            HostBinarySpec(
                targetName = "mingwX64",
                targetTaskSuffix = "MingwX64",
                executableExtension = ".exe",
                helperScriptFileName = WINDOWS_HELPER_SCRIPT_NAME
            )
        else -> error("Unsupported host platform: $operatingSystem ($architecture)")
    }
}

val hostBinarySpec = currentHostBinarySpec()
val hostMainExecutable =
    layout.buildDirectory.file(
        "bin/${hostBinarySpec.targetName}/debugExecutable/${project.name}${hostBinarySpec.executableExtension}"
    )
val e2eHelperScript =
    layout.projectDirectory.file("scripts/e2e/${hostBinarySpec.helperScriptFileName}")

tasks.withType<KotlinNativeTest>().configureEach {
    dependsOn("linkDebugExecutable${hostBinarySpec.targetTaskSuffix}")
    environment(LOADOUT_BIN_ENVIRONMENT_VARIABLE, e2eHelperScript.asFile.absolutePath)
    environment(LOADOUT_E2E_BINARY_PATH_ENVIRONMENT_VARIABLE, hostMainExecutable.get().asFile.absolutePath)
    environment(LOADOUT_E2E_HELPER_PATH_ENVIRONMENT_VARIABLE, e2eHelperScript.asFile.absolutePath)
}
