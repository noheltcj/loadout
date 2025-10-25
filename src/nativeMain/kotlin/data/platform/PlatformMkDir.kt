package data.platform

/**
 * Platform-specific directory operations for native targets.
 *
 * Creates a directory with standard permissions.
 *
 * @param path The directory path to create
 * @return 0 on success, non-zero on failure
 */
internal expect fun platformMkdir(path: String): Int
