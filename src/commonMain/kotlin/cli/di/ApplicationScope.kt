package cli.di

import cli.outputPaths
import data.repository.FileBasedConfigRepository
import data.repository.FileBasedFragmentRepository
import data.repository.FileBasedLoadoutRepository
import data.serialization.JsonSerializer
import domain.repository.EnvironmentRepository
import domain.repository.FileRepository
import domain.service.LoadoutCompositionService
import domain.service.LoadoutService
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.WriteComposedFilesUseCase

fun withApplicationScope(scopedBlock: ApplicationScope.() -> Unit) {
    val fileRepository = provideFileRepository()
    val environmentRepository = provideEnvironmentRepository()
    val serializer = JsonSerializer()

    val globalFragmentsDirectory =
        environmentRepository
            .getHomeDirectory()
            ?.let { home -> "$home/.loadout/fragments" }

    val defaultOutputPaths = outputPaths()

    val configRepository = FileBasedConfigRepository(fileRepository = fileRepository, serializer = serializer)
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
            configRepository = configRepository,
            loadoutRepository = loadoutRepository,
            compositionService = loadoutCompositionService
        )
    val writeComposedFiles =
        WriteComposedFilesUseCase(
            fileRepository = fileRepository,
            configRepository = configRepository
        )
    val loadoutService =
        LoadoutService(
            loadoutRepository = loadoutRepository,
            configRepository = configRepository,
            environmentRepository = environmentRepository,
            writeComposedFiles = writeComposedFiles
        )

    scopedBlock(
        ApplicationScope(
            fileRepository = fileRepository,
            environmentRepository = environmentRepository,
            loadoutService = loadoutService,
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
    val loadoutCompositionService: LoadoutCompositionService,
    val checkLoadoutSync: CheckLoadoutSyncUseCase,
    val writeComposedFiles: WriteComposedFilesUseCase,
    val defaultOutputPaths: List<String>,
)

expect fun provideFileRepository(): FileRepository

expect fun provideEnvironmentRepository(): EnvironmentRepository
