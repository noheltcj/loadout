package domain.policy

import domain.entity.LoadoutFileLocations
import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

/**
 * Accepts repository settings only when every field is valid. Output files are checked against the validated
 * fragments home and [loadoutFileLocations], so moving the fragments home over a configured output file is rejected.
 */
fun validateRepositorySettings(
    settings: RepositorySettings,
    loadoutFileLocations: LoadoutFileLocations,
): Result<RepositorySettings, LoadoutError> =
    validateFragmentsHome(settings.fragmentsHome).flatMap { validFragmentsHome ->
        validateFragmentSourceHosts(settings.trustedFragmentSourceHosts).flatMap { validHosts ->
            validateMaximumFragmentCopyBytes(settings.maximumFragmentCopyBytes).flatMap { validMaximumBytes ->
                validateOutputFileList(
                    outputFiles = settings.outputFiles,
                    reservedDirectories = reservedOutputDirectories(loadoutFileLocations) + validFragmentsHome.value,
                ).map { validOutputFiles ->
                    settings.copy(
                        fragmentsHome = validFragmentsHome.value,
                        trustedFragmentSourceHosts = validHosts,
                        maximumFragmentCopyBytes = validMaximumBytes,
                        outputFiles = validOutputFiles,
                    )
                }
            }
        }
    }

data class RepositorySettingsRepair(
    val settings: RepositorySettings,
    val warnings: List<RepositorySettingsRepairWarning>,
)

sealed interface RepositorySettingsRepairWarning {
    data class UnreadableSettingsReplaced(
        val error: LoadoutError.RepairableRepositorySettingsError,
    ) : RepositorySettingsRepairWarning

    data class InvalidFragmentsHomeReplaced(val invalidFragmentsHome: String) : RepositorySettingsRepairWarning

    data class InvalidTrustedHostRemoved(val invalidHost: String) : RepositorySettingsRepairWarning

    data class InvalidCopyLimitReplaced(val invalidMaximumBytes: Int) : RepositorySettingsRepairWarning

    data class InvalidOutputFileRemoved(
        val invalidOutputFile: String,
        val error: LoadoutError.ValidationError,
    ) : RepositorySettingsRepairWarning
}

/**
 * Replaces or removes every invalid field. Output files are checked against the repaired fragments home, and a list
 * left with no valid entries becomes unset.
 */
fun repairRepositorySettings(
    settings: RepositorySettings,
    loadoutFileLocations: LoadoutFileLocations,
): RepositorySettingsRepair {
    val warnings = mutableListOf<RepositorySettingsRepairWarning>()
    val fragmentsHome =
        validateFragmentsHome(settings.fragmentsHome).fold(
            onSuccess = { it.value },
            onError = {
                warnings += RepositorySettingsRepairWarning.InvalidFragmentsHomeReplaced(settings.fragmentsHome)
                RepositorySettings.DEFAULT_FRAGMENTS_HOME
            },
        )
    val trustedHosts =
        settings.trustedFragmentSourceHosts.mapNotNull { host ->
            validateFragmentSourceHost(host).fold(
                onSuccess = { it },
                onError = {
                    warnings += RepositorySettingsRepairWarning.InvalidTrustedHostRemoved(host)
                    null
                },
            )
        }.distinct().sorted()
    val maximumBytes =
        validateMaximumFragmentCopyBytes(settings.maximumFragmentCopyBytes).fold(
            onSuccess = { it },
            onError = {
                warnings += RepositorySettingsRepairWarning.InvalidCopyLimitReplaced(settings.maximumFragmentCopyBytes)
                RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES
            },
        )
    val outputFiles =
        settings.outputFiles?.let { configuredOutputFiles ->
            val repair =
                repairOutputFileList(
                    configuredOutputFiles,
                    reservedOutputDirectories(loadoutFileLocations) + fragmentsHome
                )
            repair.rejections.forEach { rejection ->
                warnings += RepositorySettingsRepairWarning.InvalidOutputFileRemoved(
                    rejection.outputFile,
                    rejection.error
                )
            }
            repair.outputFiles.map(OutputFilePath::value).ifEmpty { null }
        }

    return RepositorySettingsRepair(
        settings =
        settings.copy(
            fragmentsHome = fragmentsHome,
            trustedFragmentSourceHosts = trustedHosts,
            maximumFragmentCopyBytes = maximumBytes,
            outputFiles = outputFiles,
        ),
        warnings = warnings,
    )
}

fun validateFragmentsHome(path: String): Result<RepositoryDirectoryPath, LoadoutError> =
    validateRepositoryDirectory(path, field = "fragments-home")

fun validateFragmentSourceHost(host: String): Result<String, LoadoutError> {
    val normalized = host.trim().lowercase()
    val isIpLiteral = normalized.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$"))
    val isSafe =
        !isIpLiteral &&
            normalized.matches(
                Regex(
                    "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+" +
                        "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$"
                )
            )

    return if (isSafe) {
        Result.Success(normalized)
    } else {
        Result.Error(
            LoadoutError.ValidationError(
                field = "fragment-source-host",
                validationMessage = "Host must be an exact DNS hostname without a scheme, port, wildcard, or path"
            )
        )
    }
}

fun validateMaximumFragmentCopyBytes(bytes: Int): Result<Int, LoadoutError> =
    if (bytes in 1..RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES) {
        Result.Success(bytes)
    } else {
        Result.Error(
            LoadoutError.ValidationError(
                field = "maximum-fragment-bytes",
                validationMessage =
                "Value must be between 1 and ${RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES}"
            )
        )
    }

private fun validateFragmentSourceHosts(hosts: List<String>): Result<List<String>, LoadoutError> {
    val normalizedHosts = mutableListOf<String>()
    for (host in hosts) {
        when (val result = validateFragmentSourceHost(host)) {
            is Result.Success -> normalizedHosts += result.value
            is Result.Error -> return result
        }
    }
    return Result.Success(normalizedHosts.distinct().sorted())
}
