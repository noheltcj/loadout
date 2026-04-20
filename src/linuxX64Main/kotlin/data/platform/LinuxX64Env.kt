package data.platform

import platform.posix.setenv
import platform.posix.unsetenv

internal actual fun platformSetEnv(key: String, value: String): Int = setenv(key, value, 1)

internal actual fun platformClearEnv(key: String): Int = unsetenv(key)
