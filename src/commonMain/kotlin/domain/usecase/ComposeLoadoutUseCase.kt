package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.CompositionMetadata
import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.EnvironmentRepository
import domain.repository.FragmentRepository

class ComposeLoadoutUseCase(
    private val fragmentRepository: FragmentRepository,
    private val environmentRepository: EnvironmentRepository,
) {
    operator fun invoke(loadout: Loadout): Result<ComposedOutput, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()

        if (loadout.fragments.isEmpty()) {
            return Result.Success(
                ComposedOutput(
                    loadoutName = loadout.name,
                    content = "",
                    fragmentCount = 0,
                    metadata = CompositionMetadata.from("", emptyList(), now),
                )
            )
        }

        return loadFragmentContents(loadout.fragments)
            .map { fragmentContents ->
                val composedContent = fragmentContents
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString(separator = "\n\n")

                ComposedOutput(
                    loadoutName = loadout.name,
                    content = composedContent,
                    fragmentCount = loadout.fragments.size,
                    metadata = CompositionMetadata.from(composedContent, loadout.fragments, now),
                )
            }
    }

    private fun loadFragmentContents(fragmentPaths: List<String>): Result<List<String>, LoadoutError> {
        val contents = mutableListOf<String>()

        for (path in fragmentPaths) {
            when (val result = fragmentRepository.loadContent(path)) {
                is Result.Success -> contents += result.value
                is Result.Error -> return result
            }
        }

        return Result.Success(contents)
    }
}
