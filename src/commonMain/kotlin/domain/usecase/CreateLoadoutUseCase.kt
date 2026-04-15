package domain.usecase

import domain.entity.Loadout
import domain.entity.LoadoutMetadata
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.normalizeFragmentPath
import domain.policy.validateDescription
import domain.policy.validateLoadoutName
import domain.policy.validateMarkdownFragmentPaths
import domain.policy.validateNoDuplicateFragments
import domain.repository.EnvironmentRepository
import domain.repository.FragmentRepository
import domain.repository.LoadoutRepository

sealed interface CreateLoadoutInput {
    data class New(
        val name: String,
        val description: String?,
        val fragmentPaths: List<String>,
    ) : CreateLoadoutInput

    data class Clone(
        val name: String,
        val cloneFrom: String,
        val description: String?,
        val additionalFragmentPaths: List<String>,
    ) : CreateLoadoutInput
}

class CreateLoadoutUseCase(
    private val loadoutRepository: LoadoutRepository,
    private val fragmentRepository: FragmentRepository,
    private val environmentRepository: EnvironmentRepository,
) {
    operator fun invoke(input: CreateLoadoutInput): Result<Loadout, LoadoutError> =
        when (input) {
            is CreateLoadoutInput.New ->
                createLoadout(
                    name = input.name,
                    description = input.description,
                    fragmentPaths = input.fragmentPaths,
                )

            is CreateLoadoutInput.Clone ->
                createClonedLoadout(input)
        }

    private fun createLoadout(
        name: String,
        description: String?,
        fragmentPaths: List<String>,
    ): Result<Loadout, LoadoutError> =
        validateLoadoutName(name)
            .flatMap { validName ->
                validateDescription(description).map { validName }
            }
            .flatMap { validName ->
                validateMarkdownFragmentPaths(fragmentPaths)
                    .flatMap { normalizedFragmentPaths ->
                        validateNoDuplicateFragments(normalizedFragmentPaths)
                            .map { validName to normalizedFragmentPaths }
                    }
            }
            .flatMap { (validName, normalizedFragmentPaths) ->
                ensureLoadoutDoesNotExist(validName)
                    .flatMap { ensureFragmentsExist(normalizedFragmentPaths) }
                    .flatMap {
                        saveNewLoadout(
                            name = validName,
                            description = description.orEmpty(),
                            fragmentPaths = normalizedFragmentPaths,
                        )
                    }
            }

    private fun createClonedLoadout(input: CreateLoadoutInput.Clone): Result<Loadout, LoadoutError> =
        validateLoadoutName(input.name)
            .flatMap { validName ->
                validateDescription(input.description).map { validName }
            }
            .flatMap { validName ->
                validateLoadoutName(input.cloneFrom).map { validName }
            }
            .flatMap {
                loadoutRepository.findByName(input.cloneFrom).flatMap { sourceLoadout ->
                    sourceLoadout
                        ?.let { Result.Success(it) }
                        ?: Result.Error(LoadoutError.LoadoutNotFound(input.cloneFrom))
                }
            }
            .flatMap { sourceLoadout ->
                validateMarkdownFragmentPaths(input.additionalFragmentPaths)
                    .flatMap { normalizedAdditionalFragmentPaths ->
                        val mergedFragmentPaths =
                            sourceLoadout.fragments.map(::normalizeFragmentPath) + normalizedAdditionalFragmentPaths

                        validateNoDuplicateFragments(mergedFragmentPaths)
                            .map { sourceLoadout to mergedFragmentPaths }
                    }
            }
            .flatMap { (sourceLoadout, mergedFragmentPaths) ->
                ensureLoadoutDoesNotExist(input.name)
                    .flatMap { ensureFragmentsExist(mergedFragmentPaths) }
                    .flatMap {
                        saveNewLoadout(
                            name = input.name,
                            description = input.description ?: sourceLoadout.description,
                            fragmentPaths = mergedFragmentPaths,
                        )
                    }
            }

    private fun ensureLoadoutDoesNotExist(name: String): Result<Unit, LoadoutError> =
        if (loadoutRepository.exists(name)) {
            Result.Error(LoadoutError.LoadoutAlreadyExists(name))
        } else {
            Result.Success(Unit)
        }

    private fun ensureFragmentsExist(fragmentPaths: List<String>): Result<Unit, LoadoutError> {
        for (fragmentPath in fragmentPaths) {
            when (val result = fragmentRepository.findByPath(fragmentPath)) {
                is Result.Success -> {
                    if (result.value == null) {
                        return Result.Error(LoadoutError.FragmentNotFound(fragmentPath))
                    }
                }

                is Result.Error -> return result
            }
        }

        return Result.Success(Unit)
    }

    private fun saveNewLoadout(
        name: String,
        description: String,
        fragmentPaths: List<String>,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()
        val loadout =
            Loadout(
                name = name,
                description = description,
                fragments = fragmentPaths,
                metadata = LoadoutMetadata(createdAt = now, updatedAt = now),
            )

        val validationErrors = loadout.validate()
        if (validationErrors.isNotEmpty()) {
            return Result.Error(LoadoutError.ValidationError("loadout", validationErrors.first()))
        }

        return loadoutRepository.save(loadout).map { loadout }
    }
}
