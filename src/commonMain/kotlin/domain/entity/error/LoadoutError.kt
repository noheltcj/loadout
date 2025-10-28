package domain.entity.error

import kotlinx.serialization.SerializationException

sealed interface LoadoutError {
    val message: String
    val cause: Throwable? get() = null

    data class ConfigurationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : LoadoutError

    data class LoadoutNotFound(val name: String) : LoadoutError {
        override val message: String = "Loadout '$name' not found"
    }

    data class LoadoutAlreadyExists(val name: String) : LoadoutError {
        override val message: String = "Loadout '$name' already exists"
    }

    data class FragmentNotFound(val path: String) : LoadoutError {
        override val message: String = "Fragment not found: $path"
    }

    data class InvalidFragment(
        val path: String,
        override val cause: Throwable? = null
    ) : LoadoutError {
        override val message: String = "Invalid fragment: $path"
    }

    data class FileSystemError(
        override val message: String,
        override val cause: Throwable? = null
    ) : LoadoutError

    data class SerializationError(
        override val message: String,
        override val cause: SerializationException
    ) : LoadoutError

    data class ValidationError(
        val field: String,
        val validationMessage: String
    ) : LoadoutError {
        override val message: String = "Validation error in '$field': $validationMessage"
    }
}