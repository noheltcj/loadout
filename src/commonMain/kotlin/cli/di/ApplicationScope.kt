package cli.di

import domain.service.LoadoutService
import data.*
import data.repository.FileBasedConfigRepository
import data.repository.FileBasedFragmentRepository
import data.repository.FileBasedLoadoutRepository
import data.serialization.JsonSerializer
import domain.service.LoadoutCompositionService
import domain.repository.FileRepository
import domain.repository.EnvironmentRepository

fun withApplicationScope(scopedBlock: ApplicationScope.() -> Unit) {
    val fileRepository = provideFileRepository()
    val environmentRepository = provideEnvironmentRepository()
    val serializer = JsonSerializer()

    val globalFragmentsDirectory = environmentRepository.getHomeDirectory()
        ?.let { home -> "$home/.loadout/fragments" }

    val configRepository = FileBasedConfigRepository(fileRepository, serializer)
    val loadoutRepository = FileBasedLoadoutRepository(fileRepository, serializer)
    val fragmentRepository = FileBasedFragmentRepository(
        fileRepository = fileRepository,
        environmentRepository = environmentRepository,
        globalFragmentsDirectory = globalFragmentsDirectory
    )

    scopedBlock(
        ApplicationScope(
            fileRepository = fileRepository,
            environmentRepository = environmentRepository,
            loadoutService = LoadoutService(loadoutRepository, configRepository, environmentRepository),
            loadoutCompositionService = LoadoutCompositionService(fragmentRepository, environmentRepository)
        )
    )
}

data class ApplicationScope(
    val fileRepository: FileRepository,
    val environmentRepository: EnvironmentRepository,
    val loadoutService: LoadoutService,
    val loadoutCompositionService: LoadoutCompositionService,
)

expect fun provideFileRepository(): FileRepository
expect fun provideEnvironmentRepository(): EnvironmentRepository
