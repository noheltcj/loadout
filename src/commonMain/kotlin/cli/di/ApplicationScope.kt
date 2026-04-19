package cli.di

import cli.outputPaths
import data.repository.FileBasedConfigRepository
import data.repository.FileBasedFragmentRepository
import data.repository.FileBasedLoadoutRepository
import data.serialization.JsonSerializer
import domain.repository.EnvironmentRepository
import domain.repository.FileRepository
import domain.usecase.ActivateComposedLoadoutUseCase
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.ComposeLoadoutUseCase
import domain.usecase.CreateLoadoutUseCase
import domain.usecase.GetCurrentLoadoutUseCase
import domain.usecase.GetLoadoutUseCase
import domain.usecase.InitializeLoadoutProjectUseCase
import domain.usecase.LinkFragmentToLoadoutUseCase
import domain.usecase.ListLoadoutsUseCase
import domain.usecase.ReadCurrentLoadoutStatusUseCase
import domain.usecase.RemoveLoadoutUseCase
import domain.usecase.SyncCurrentLoadoutUseCase
import domain.usecase.UnlinkFragmentFromLoadoutUseCase
import domain.usecase.UpdateLoadoutUseCase
import domain.usecase.UseLoadoutUseCase
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

    val configRepository = FileBasedConfigRepository(fileRepository = fileRepository, serializer = serializer)
    val loadoutRepository = FileBasedLoadoutRepository(fileRepository = fileRepository, serializer = serializer)
    val fragmentRepository =
        FileBasedFragmentRepository(
            fileRepository = fileRepository,
            environmentRepository = environmentRepository,
            globalFragmentsDirectory = globalFragmentsDirectory
        )

    val composeLoadout =
        ComposeLoadoutUseCase(
            fragmentRepository = fragmentRepository,
            environmentRepository = environmentRepository
        )
    val writeComposedFiles =
        WriteComposedFilesUseCase(
            fileRepository = fileRepository,
            configRepository = configRepository
        )
    val activateComposedLoadout =
        ActivateComposedLoadoutUseCase(
            writeComposedFiles = writeComposedFiles,
            configRepository = configRepository
        )
    val getLoadout =
        GetLoadoutUseCase(
            loadoutRepository = loadoutRepository,
        )
    val getCurrentLoadout =
        GetCurrentLoadoutUseCase(
            configRepository = configRepository,
            getLoadout = getLoadout
        )
    val listLoadouts =
        ListLoadoutsUseCase(
            loadoutRepository = loadoutRepository
        )
    val updateLoadout =
        UpdateLoadoutUseCase(
            loadoutRepository = loadoutRepository
        )
    val createLoadout =
        CreateLoadoutUseCase(
            loadoutRepository = loadoutRepository,
            fragmentRepository = fragmentRepository,
            environmentRepository = environmentRepository
        )
    val linkFragmentToLoadout =
        LinkFragmentToLoadoutUseCase(
            fragmentRepository = fragmentRepository,
            environmentRepository = environmentRepository,
            getLoadout = getLoadout,
            updateLoadout = updateLoadout
        )
    val unlinkFragmentFromLoadout =
        UnlinkFragmentFromLoadoutUseCase(
            environmentRepository = environmentRepository,
            getLoadout = getLoadout,
            updateLoadout = updateLoadout
        )
    val removeLoadout =
        RemoveLoadoutUseCase(
            loadoutRepository = loadoutRepository,
            configRepository = configRepository
        )
    val useLoadout =
        UseLoadoutUseCase(
            getLoadout = getLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout
        )
    val syncCurrentLoadout =
        SyncCurrentLoadoutUseCase(
            getCurrentLoadout = getCurrentLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout
        )
    val readCurrentLoadoutStatus =
        ReadCurrentLoadoutStatusUseCase(
            getCurrentLoadout = getCurrentLoadout,
            composeLoadout = composeLoadout
        )
    val checkLoadoutSync =
        CheckLoadoutSyncUseCase(
            configRepository = configRepository,
            getCurrentLoadout = getCurrentLoadout,
            composeLoadout = composeLoadout
        )
    val initializeLoadoutProject =
        InitializeLoadoutProjectUseCase(
            fileRepository = fileRepository,
            listLoadouts = listLoadouts,
            createLoadout = createLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout
        )

    return scopedBlock(
        ApplicationScope(
            fileRepository = fileRepository,
            environmentRepository = environmentRepository,
            createLoadout = createLoadout,
            listLoadouts = listLoadouts,
            linkFragmentToLoadout = linkFragmentToLoadout,
            unlinkFragmentFromLoadout = unlinkFragmentFromLoadout,
            removeLoadout = removeLoadout,
            useLoadout = useLoadout,
            syncCurrentLoadout = syncCurrentLoadout,
            initializeLoadoutProject = initializeLoadoutProject,
            readCurrentLoadoutStatus = readCurrentLoadoutStatus,
            checkLoadoutSync = checkLoadoutSync,
            writeComposedFiles = writeComposedFiles,
            defaultOutputPaths = defaultOutputPaths
        )
    )
}

data class ApplicationScope(
    val fileRepository: FileRepository,
    val environmentRepository: EnvironmentRepository,
    val createLoadout: CreateLoadoutUseCase,
    val listLoadouts: ListLoadoutsUseCase,
    val linkFragmentToLoadout: LinkFragmentToLoadoutUseCase,
    val unlinkFragmentFromLoadout: UnlinkFragmentFromLoadoutUseCase,
    val removeLoadout: RemoveLoadoutUseCase,
    val useLoadout: UseLoadoutUseCase,
    val syncCurrentLoadout: SyncCurrentLoadoutUseCase,
    val initializeLoadoutProject: InitializeLoadoutProjectUseCase,
    val readCurrentLoadoutStatus: ReadCurrentLoadoutStatusUseCase,
    val checkLoadoutSync: CheckLoadoutSyncUseCase,
    val writeComposedFiles: WriteComposedFilesUseCase,
    val defaultOutputPaths: List<String>,
)

expect fun provideFileRepository(): FileRepository

expect fun provideEnvironmentRepository(): EnvironmentRepository
