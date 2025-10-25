package domain.entity.error

import kotlinx.serialization.SerializationException

sealed class LoadoutError(open val message: String, open val cause: Throwable? = null) {

    data class ConfigurationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : LoadoutError(message, cause)

    data class LoadoutNotFound(val name: String) : LoadoutError("Loadout '$name' not found")

    data class LoadoutAlreadyExists(val name: String) : LoadoutError("Loadout '$name' already exists")

    data class FragmentNotFound(val path: String) : LoadoutError("Fragment not found: $path")

    data class InvalidFragment(
        val path: String,
        override val cause: Throwable? = null
    ) : LoadoutError("Invalid fragment: $path", cause)

    data class FileSystemError(
        override val message: String,
        override val cause: Throwable? = null
    ) : LoadoutError(message, cause)

    data class SerializationError(
        override val message: String,
        override val cause: SerializationException
    ) : LoadoutError(message, cause)

    data class ValidationError(
        val field: String,
        val validationMessage: String
    ) : LoadoutError("Validation error in '$field': $validationMessage")
}