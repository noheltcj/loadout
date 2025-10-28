package domain.service

import domain.entity.ComposedOutput
import domain.entity.CompositionMetadata
import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.EnvironmentRepository
import domain.repository.FragmentRepository

class LoadoutCompositionService(
    private val fragmentRepository: FragmentRepository,
    private val environmentRepository: EnvironmentRepository,
) {
    operator fun invoke(loadout: Loadout): Result<ComposedOutput, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()

        if (loadout.fragments.isEmpty()) {
            val emptyOutput =
                ComposedOutput(
                    loadoutName = loadout.name,
                    content = "",
                    fragmentCount = 0,
                    metadata = CompositionMetadata.from("", emptyList(), now),
                )
            return Result.Success(emptyOutput)
        }

        return loadFragments(loadout.fragments)
            .map { fragmentContents ->
                val composedContent = composeContent(fragmentContents)
                val metadata = CompositionMetadata.from(composedContent, loadout.fragments, now)

                ComposedOutput(
                    loadoutName = loadout.name,
                    content = composedContent,
                    fragmentCount = loadout.fragments.size,
                    metadata = metadata,
                )
            }
    }

    fun validateLoadoutFragments(loadout: Loadout): Result<List<String>, LoadoutError> {
        val missingFragments = mutableListOf<String>()

        for (fragmentPath in loadout.fragments) {
            when (val result = fragmentRepository.findByPath(fragmentPath)) {
                is Result.Success -> {
                    if (result.value == null) {
                        missingFragments.add(fragmentPath)
                    }
                }
                is Result.Error -> return result
            }
        }

        return Result.Success(missingFragments)
    }

    private fun loadFragments(fragmentPaths: List<String>): Result<List<String>, LoadoutError> {
        val contents = mutableListOf<String>()

        for (path in fragmentPaths) {
            when (val result = fragmentRepository.loadContent(path)) {
                is Result.Success -> contents.add(result.value)
                is Result.Error -> return result
            }
        }

        return Result.Success(contents)
    }

    private fun composeContent(fragmentContents: List<String>): String =
        fragmentContents
            .map { content -> content.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
}
