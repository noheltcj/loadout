package domain.entity.error

import kotlinx.serialization.SerializationException

sealed interface LoadoutError {
    val message: String
    val cause: Throwable? get() = null

    data class ConfigurationError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : LoadoutError

    data class RepairableRepositorySettingsError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : LoadoutError

    data class UnreadableLocalState(
        val path: String,
        override val cause: Throwable? = null,
    ) : LoadoutError {
        override val message: String = "Could not read the local state in $path"
    }

    data class UnreadableUserSettings(
        val path: String,
        override val cause: Throwable? = null,
    ) : LoadoutError {
        override val message: String = "Could not read the user settings in $path"
    }

    data class UserConfigDirectoryNotFound(val environmentVariables: List<String>) : LoadoutError {
        override val message: String =
            "Cannot find a directory for user settings. " +
                "Set ${environmentVariables.joinToString(" or ")} to an absolute path."
    }

    data class LoadoutNotFound(val name: String) : LoadoutError {
        override val message: String = "Loadout '$name' not found"
    }

    data class LoadoutAlreadyExists(val name: String) : LoadoutError {
        override val message: String = "Loadout '$name' already exists"
    }

    data class FragmentNotFound(val path: String) : LoadoutError {
        override val message: String = "Fragment not found: $path"
    }

    data class FragmentNotInLoadout(val path: String, val loadoutName: String) : LoadoutError {
        override val message: String = "Fragment '$path' is not in loadout '$loadoutName'"
    }

    data class FragmentAlreadyInLoadout(val path: String, val loadoutName: String) : LoadoutError {
        override val message: String = "Fragment '$path' is already in loadout '$loadoutName'"
    }

    data class InvalidFragment(
        val path: String,
        override val cause: Throwable? = null,
    ) : LoadoutError {
        override val message: String = "Invalid fragment: $path"
    }

    data class InvalidFragmentSource(
        val source: String,
        val reason: String,
        override val cause: Throwable? = null,
    ) : LoadoutError {
        override val message: String = "Invalid fragment source '$source': $reason"
    }

    data class UntrustedFragmentSourceHost(val host: String) : LoadoutError {
        override val message: String = "Fragment source host '$host' is not trusted"
    }

    data class FragmentCopyTooLarge(
        val maximumBytes: Int,
    ) : LoadoutError {
        override val message: String = "Fragment exceeds the configured copy limit of $maximumBytes bytes"
    }

    data class FragmentDestinationAlreadyExists(val path: String) : LoadoutError {
        override val message: String = "Fragment destination already exists: $path (use --force to replace it)"
    }

    data class FileSystemError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : LoadoutError

    data class SerializationError(
        override val message: String,
        override val cause: SerializationException,
    ) : LoadoutError

    data class ValidationError(
        val field: String,
        val validationMessage: String,
    ) : LoadoutError {
        override val message: String = "Validation error in '$field': $validationMessage"
    }
}
