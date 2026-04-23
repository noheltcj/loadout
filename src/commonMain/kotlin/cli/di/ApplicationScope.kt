package cli.di

import cli.outputPaths
import data.repository.FileBasedFragmentRepository
import data.repository.FileBasedLoadoutRepository
import data.repository.FileBasedLocalLoadoutStateRepository
import data.repository.FileBasedRepositorySettingsRepository
import data.serialization.JsonSerializer
import domain.repository.EnvironmentRepository
import domain.repository.FileRepository
import domain.repository.RepositorySettingsRepository
import domain.service.LoadoutCompositionService
import domain.service.LoadoutService
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.WriteComposedFilesUseCase

fun <T> withApplicationScope(scopedBlock: ApplicationScope.() -> T): T {
    val fileRepository = provideFileRepository()
    val environmentRepository = provideEnvironmentRepository()
    val serializer = JsonSerializer()

    val globalFragmentsDirectory =
        environmentRepository
            .getHomeDirectory()
            ?.let { home -> "$home/.loadout/fragments" }

    val defaultOutputPaths = outputPaths()

    val localLoadoutStateRepository =
        FileBasedLocalLoadoutStateRepository(fileRepository = fileRepository, serializer = serializer)
    val repositorySettingsRepository = FileBasedRepositorySettingsRepository(
        fileRepository = fileRepository,
        serializer = serializer
    )
    val loadoutRepository = FileBasedLoadoutRepository(fileRepository = fileRepository, serializer = serializer)
    val fragmentRepository =
        FileBasedFragmentRepository(
            fileRepository = fileRepository,
            environmentRepository = environmentRepository,
            globalFragmentsDirectory = globalFragmentsDirectory
        )

    val loadoutCompositionService =
        LoadoutCompositionService(
            fragmentRepository = fragmentRepository,
            environmentRepository = environmentRepository
        )
    val checkLoadoutSync =
        CheckLoadoutSyncUseCase(
            localLoadoutStateRepository = localLoadoutStateRepository,
            loadoutRepository = loadoutRepository,
            compositionService = loadoutCompositionService
        )
    val writeComposedFiles =
        WriteComposedFilesUseCase(
            fileRepository = fileRepository,
            localLoadoutStateRepository = localLoadoutStateRepository
        )
    val loadoutService =
        LoadoutService(
            loadoutRepository = loadoutRepository,
            localLoadoutStateRepository = localLoadoutStateRepository,
            repositorySettingsRepository = repositorySettingsRepository,
            environmentRepository = environmentRepository,
            writeComposedFiles = writeComposedFiles
        )

    return scopedBlock(
        ApplicationScope(
            fileRepository = fileRepository,
            environmentRepository = environmentRepository,
            loadoutService = loadoutService,
            repositorySettingsRepository = repositorySettingsRepository,
            loadoutCompositionService = loadoutCompositionService,
            checkLoadoutSync = checkLoadoutSync,
            writeComposedFiles = writeComposedFiles,
            defaultOutputPaths = defaultOutputPaths
        )
    )
}

data class ApplicationScope(
    val fileRepository: FileRepository,
    val environmentRepository: EnvironmentRepository,
    val loadoutService: LoadoutService,
    val repositorySettingsRepository: RepositorySettingsRepository,
    val loadoutCompositionService: LoadoutCompositionService,
    val checkLoadoutSync: CheckLoadoutSyncUseCase,
    val writeComposedFiles: WriteComposedFilesUseCase,
    val defaultOutputPaths: List<String>,
)

expect fun provideFileRepository(): FileRepository

expect fun provideEnvironmentRepository(): EnvironmentRepository
