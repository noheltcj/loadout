import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
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
    val helperScriptName: String,
)

private val posixHelperScriptName = "loadout-e2e-helper"
private val windowsHelperScriptName = "loadout-e2e-helper.cmd"
val loadoutHelperEnvironmentVariable = "LOADOUT_E2E_HELPER_PATH"

fun currentHostBinarySpec(): HostBinarySpec {
    val operatingSystem = System.getProperty("os.name").lowercase(Locale.US)
    val architecture = System.getProperty("os.arch").lowercase(Locale.US)

    return when {
        operatingSystem == "mac os x" && (architecture == "aarch64" || architecture == "arm64") ->
            HostBinarySpec(
                targetName = "macosArm64",
                targetTaskSuffix = "MacosArm64",
                executableExtension = ".kexe",
                helperScriptName = posixHelperScriptName
            )
        operatingSystem == "mac os x" ->
            HostBinarySpec(
                targetName = "macosX64",
                targetTaskSuffix = "MacosX64",
                executableExtension = ".kexe",
                helperScriptName = posixHelperScriptName
            )
        operatingSystem == "linux" && (architecture == "aarch64" || architecture == "arm64") ->
            HostBinarySpec(
                targetName = "linuxArm64",
                targetTaskSuffix = "LinuxArm64",
                executableExtension = ".kexe",
                helperScriptName = posixHelperScriptName
            )
        operatingSystem == "linux" ->
            HostBinarySpec(
                targetName = "linuxX64",
                targetTaskSuffix = "LinuxX64",
                executableExtension = ".kexe",
                helperScriptName = posixHelperScriptName
            )
        operatingSystem.startsWith("windows") ->
            HostBinarySpec(
                targetName = "mingwX64",
                targetTaskSuffix = "MingwX64",
                executableExtension = ".exe",
                helperScriptName = windowsHelperScriptName
            )
        else -> error("Unsupported host platform: $operatingSystem ($architecture)")
    }
}

abstract class WriteE2eHelperTask : DefaultTask() {
    @get:InputFile
    abstract val mainExecutable: RegularFileProperty

    @get:OutputFile
    abstract val helperScript: RegularFileProperty

    @get:Input
    var isWindowsHelper: Boolean = false

    @TaskAction
    fun writeHelperScript() {
        val helperScriptFile = helperScript.get().asFile
        val mainExecutableFile = mainExecutable.get().asFile
        helperScriptFile.parentFile.mkdirs()

        // Future hook-installation tests must keep using an explicit helper path instead of host PATH resolution.
        val helperScriptContent =
            if (isWindowsHelper) {
                windowsHelperScript(mainExecutableFile.absolutePath)
            } else {
                posixHelperScript(mainExecutableFile.absolutePath)
            }

        helperScriptFile.writeText(helperScriptContent)
        helperScriptFile.setExecutable(true)
    }

    private fun quoteForShell(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun posixHelperScript(mainExecutablePath: String): String {
        val shell = "$"
        return """
            #!/bin/sh
            if [ "${shell}1" = "__printenv__" ]; then
              shift
              for key in "${shell}@"; do
                case "${shell}key" in
                  HOME) value="${shell}HOME" ;;
                  XDG_CONFIG_HOME) value="${shell}XDG_CONFIG_HOME" ;;
                  XDG_DATA_HOME) value="${shell}XDG_DATA_HOME" ;;
                  XDG_STATE_HOME) value="${shell}XDG_STATE_HOME" ;;
                  XDG_CACHE_HOME) value="${shell}XDG_CACHE_HOME" ;;
                  PATH) value="${shell}PATH" ;;
                  GIT_DIR) value="${shell}GIT_DIR" ;;
                  GIT_WORK_TREE) value="${shell}GIT_WORK_TREE" ;;
                  *) value="" ;;
                esac
                printf '%s=%s\n' "${shell}key" "${shell}value"
              done
              exit 0
            fi
            exec ${quoteForShell(mainExecutablePath)} "${shell}@"
            """.trimIndent() + "\n"
    }

    private fun windowsHelperScript(mainExecutablePath: String): String =
        """
        @echo off
        if "%~1"=="__printenv__" goto printenv
        "${mainExecutablePath}" %*
        exit /b %errorlevel%
        :printenv
        shift
        :printenv_loop
        if "%~1"=="" exit /b 0
        call :resolve_env_value "%~1"
        echo %~1=%ENV_VALUE%
        shift
        goto printenv_loop
        :resolve_env_value
        set "ENV_VALUE="
        if /I "%~1"=="HOME" set "ENV_VALUE=%HOME%"
        if /I "%~1"=="XDG_CONFIG_HOME" set "ENV_VALUE=%XDG_CONFIG_HOME%"
        if /I "%~1"=="XDG_DATA_HOME" set "ENV_VALUE=%XDG_DATA_HOME%"
        if /I "%~1"=="XDG_STATE_HOME" set "ENV_VALUE=%XDG_STATE_HOME%"
        if /I "%~1"=="XDG_CACHE_HOME" set "ENV_VALUE=%XDG_CACHE_HOME%"
        if /I "%~1"=="PATH" set "ENV_VALUE=%PATH%"
        if /I "%~1"=="GIT_DIR" set "ENV_VALUE=%GIT_DIR%"
        if /I "%~1"=="GIT_WORK_TREE" set "ENV_VALUE=%GIT_WORK_TREE%"
        exit /b 0
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
}

val hostBinarySpec = currentHostBinarySpec()
val hostMainExecutable =
    layout.buildDirectory.file(
        "bin/${hostBinarySpec.targetName}/debugExecutable/${project.name}${hostBinarySpec.executableExtension}"
    )
val e2eHelperScript =
    layout.buildDirectory.file("e2e-helper/${hostBinarySpec.helperScriptName}")

val prepareE2eHelper by tasks.registering(WriteE2eHelperTask::class) {
    dependsOn("linkDebugExecutable${hostBinarySpec.targetTaskSuffix}")
    mainExecutable.set(hostMainExecutable)
    helperScript.set(e2eHelperScript)
    isWindowsHelper = hostBinarySpec.helperScriptName == windowsHelperScriptName
}

tasks.withType<KotlinNativeTest>().configureEach {
    dependsOn(prepareE2eHelper)
    environment(loadoutHelperEnvironmentVariable, e2eHelperScript.get().asFile.absolutePath)
}
