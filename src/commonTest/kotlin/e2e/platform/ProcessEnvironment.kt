package e2e.platform

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
    environment: Map<String, String>,
    block: () -> T,
): T

expect fun runExternalProcess(
    workingDirectory: String,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
): ExternalProcessResult

expect fun readEnvironmentVariable(key: String): String?

expect fun currentWorkingDirectory(): String

expect fun isExecutablePath(path: String): Boolean

expect fun setExecutable(path: String)
