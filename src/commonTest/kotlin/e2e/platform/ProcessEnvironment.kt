package e2e.platform

@DslMarker
annotation class EnvironmentOverlayDsl

sealed interface EnvironmentMutation {
    data class Set(
        val value: String,
    ) : EnvironmentMutation

    data object Unset : EnvironmentMutation
}

class EnvironmentOverlay private constructor(
    private val entries: Map<String, EnvironmentMutation> = emptyMap(),
) {
    operator fun plus(other: EnvironmentOverlay): EnvironmentOverlay = EnvironmentOverlay(entries + other.entries)

    fun mutationKeys(): Set<String> = entries.keys

    fun forEachMutation(action: (key: String, mutation: EnvironmentMutation) -> Unit) {
        entries.forEach { (key, mutation) -> action(key, mutation) }
    }

    internal companion object {
        fun fromEntries(entries: Map<String, EnvironmentMutation>): EnvironmentOverlay = EnvironmentOverlay(entries)
    }
}

@EnvironmentOverlayDsl
class EnvironmentOverlayBuilder {
    private val entries = linkedMapOf<String, EnvironmentMutation>()

    infix fun String.setTo(value: String) {
        entries[this] = EnvironmentMutation.Set(value)
    }

    fun set(
        key: String,
        value: String,
    ) {
        key setTo value
    }

    fun unset(vararg keys: String) {
        keys.forEach { key ->
            entries[key] = EnvironmentMutation.Unset
        }
    }

    internal fun build(): EnvironmentOverlay = EnvironmentOverlay.fromEntries(entries.toMap())
}

fun environmentOverlay(block: EnvironmentOverlayBuilder.() -> Unit = {}): EnvironmentOverlay =
    EnvironmentOverlayBuilder().apply(block).build()

data class ExternalProcessResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

expect fun createTemporaryDirectory(prefix: String): String

expect fun deleteRecursively(path: String)

expect fun <T> withWorkingDirectoryAndHome(
    workingDirectory: String,
    homeDirectory: String,
    block: () -> T,
): T

expect fun <T> withWorkingDirectoryAndEnvironment(
    workingDirectory: String,
    environment: EnvironmentOverlay,
    block: () -> T,
): T

expect fun runExternalProcess(
    workingDirectory: String,
    command: List<String>,
    environment: EnvironmentOverlay = environmentOverlay(),
): ExternalProcessResult

expect fun readEnvironmentVariable(key: String): String?

expect fun currentWorkingDirectory(): String

expect fun isExecutablePath(path: String): Boolean

expect fun setExecutable(path: String)
