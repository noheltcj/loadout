package domain.entity

sealed interface UserConfigDirectory {
    /** [pathInHome] is [path] relative to the home directory when it lies inside it. */
    data class Determined(
        val path: String,
        val pathInHome: String?,
    ) : UserConfigDirectory

    /** None of [environmentVariables] names an absolute directory. */
    data class Undeterminable(
        val environmentVariables: List<String>,
    ) : UserConfigDirectory
}
