package data.platform

import data.environment.UserConfigDirectoryConvention
import platform.posix.putenv

internal actual fun platformSetEnv(
    key: String,
    value: String,
): Int = putenv("$key=$value")

internal actual fun platformClearEnv(key: String): Int = putenv("$key=")

internal actual val platformUserConfigDirectoryConvention: UserConfigDirectoryConvention =
    UserConfigDirectoryConvention.Windows
