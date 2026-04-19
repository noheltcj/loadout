package e2e.platform

sealed interface EnvironmentMutation {
    data class Set(
        val value: String,
    ) : EnvironmentMutation

    data object Unset : EnvironmentMutation
}

data class EnvironmentOverlay(
    val mutations: Map<String, EnvironmentMutation> = emptyMap(),
) {
    operator fun plus(other: EnvironmentOverlay): EnvironmentOverlay = EnvironmentOverlay(mutations + other.mutations)

    companion object {
        val empty = EnvironmentOverlay()

        fun set(vararg entries: Pair<String, String>): EnvironmentOverlay =
            EnvironmentOverlay(entries.associate { (key, value) -> key to EnvironmentMutation.Set(value) })

        fun unset(vararg keys: String): EnvironmentOverlay =
            EnvironmentOverlay(keys.associateWith { EnvironmentMutation.Unset })
    }
}

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
    environment: EnvironmentOverlay = EnvironmentOverlay.empty,
): ExternalProcessResult

expect fun readEnvironmentVariable(key: String): String?

expect fun currentWorkingDirectory(): String

expect fun isExecutablePath(path: String): Boolean

expect fun setExecutable(path: String)
