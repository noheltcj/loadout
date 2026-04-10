package data.platform

internal expect fun platformSetEnv(key: String, value: String): Int
internal expect fun platformClearEnv(key: String): Int
