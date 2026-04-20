package e2e.platform

expect fun createTemporaryDirectory(prefix: String): String

expect fun deleteRecursively(path: String)

expect fun <T> withWorkingDirectoryAndHome(workingDirectory: String, homeDirectory: String, block: () -> T): T
