package data.platform

import data.environment.UserConfigDirectoryConvention
import platform.posix.setenv
import platform.posix.unsetenv

internal actual fun platformSetEnv(
    key: String,
    value: String,
): Int = setenv(key, value, 1)

internal actual fun platformClearEnv(key: String): Int = unsetenv(key)

internal actual val platformUserConfigDirectoryConvention: UserConfigDirectoryConvention =
    UserConfigDirectoryConvention.Xdg
