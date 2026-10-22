package data.platform

import data.environment.UserConfigDirectoryConvention

internal expect fun platformSetEnv(
    key: String,
    value: String,
): Int

internal expect fun platformClearEnv(key: String): Int

internal expect val platformUserConfigDirectoryConvention: UserConfigDirectoryConvention
