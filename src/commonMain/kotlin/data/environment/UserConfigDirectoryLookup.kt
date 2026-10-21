package data.environment

import domain.entity.UserConfigDirectory
import domain.policy.isAbsolutePath

private const val XDG_CONFIG_HOME = "XDG_CONFIG_HOME"
private const val HOME = "HOME"
private const val APPDATA = "APPDATA"
private const val XDG_DEFAULT_CONFIG_DIRECTORY_IN_HOME = ".config"

enum class UserConfigDirectoryConvention {
    Xdg,

    Windows,
}

/** A relative `XDG_CONFIG_HOME` is ignored, as the XDG Base Directory specification requires. */
fun lookUpUserConfigDirectory(
    convention: UserConfigDirectoryConvention,
    readEnvironmentVariable: (String) -> String?,
): UserConfigDirectory {
    fun absoluteDirectory(variable: String): String? =
        readEnvironmentVariable(variable)
            ?.takeIf { value -> value.isNotEmpty() && isAbsolutePath(value) }
            ?.let { value -> if (value.length > 1) value.trimEnd('/', '\\') else value }

    return when (convention) {
        UserConfigDirectoryConvention.Xdg -> {
            val homeDirectory = absoluteDirectory(HOME)
            val xdgConfigHome = absoluteDirectory(XDG_CONFIG_HOME)

            when {
                xdgConfigHome != null ->
                    UserConfigDirectory.Determined(
                        path = xdgConfigHome,
                        pathInHome = homeDirectory?.let { home -> xdgConfigHome.pathRelativeTo(home) },
                    )

                homeDirectory != null ->
                    UserConfigDirectory.Determined(
                        path = "$homeDirectory/$XDG_DEFAULT_CONFIG_DIRECTORY_IN_HOME",
                        pathInHome = XDG_DEFAULT_CONFIG_DIRECTORY_IN_HOME,
                    )

                else -> UserConfigDirectory.Undeterminable(listOf(XDG_CONFIG_HOME, HOME))
            }
        }

        UserConfigDirectoryConvention.Windows ->
            absoluteDirectory(APPDATA)
                ?.let { appData -> UserConfigDirectory.Determined(path = appData, pathInHome = null) }
                ?: UserConfigDirectory.Undeterminable(listOf(APPDATA))
    }
}

private fun String.pathRelativeTo(directory: String): String? =
    takeIf { startsWith("$directory/") }?.removePrefix("$directory/")
