package cli.di

import cli.outputPaths
import data.filesystem.CanonicalPathResolver
import data.repository.FileBasedFragmentRepository
import data.repository.FileBasedLoadoutRepository
import data.repository.FileBasedLocalLoadoutStateRepository
import data.repository.FileBasedRepositorySettingsRepository
import data.repository.FileSystemFragmentDestinationRepository
import data.repository.KtorRemoteFragmentSourceRepository
import data.serialization.JsonSerializer
import domain.repository.EnvironmentRepository
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.usecase.ActivateComposedLoadoutUseCase
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.ComposeLoadoutUseCase
import domain.usecase.ConfigureGitHooksUseCase
import domain.usecase.CopyFragmentUseCase
import domain.usecase.CreateLoadoutUseCase
import domain.usecase.GetCurrentLoadoutUseCase
import domain.usecase.GetLoadoutUseCase
import domain.usecase.GetRepairableRepositorySettingsUseCase
import domain.usecase.GetRepositorySettingsUseCase
import domain.usecase.GetTargetLoadoutForSyncUseCase
import domain.usecase.InitializeLoadoutProjectUseCase
import domain.usecase.LinkFragmentToLoadoutUseCase
import domain.usecase.ListLoadoutsUseCase
import domain.usecase.ReadCurrentLoadoutStatusUseCase
import domain.usecase.RemoveLoadoutUseCase
import domain.usecase.SyncLoadoutUseCase
import domain.usecase.UnlinkFragmentFromLoadoutUseCase
import domain.usecase.UpdateRepositorySettingsUseCase
import domain.usecase.UseLoadoutUseCase
import domain.usecase.WriteComposedFilesUseCase

fun <T> withApplicationScope(scopedBlock: ApplicationScope.() -> T): T {
    val fileRepositories = provideFileRepositories()
    val fileQueryRepository = fileRepositories.query
    val fileMutationRepository = fileRepositories.mutation
    val environmentRepository = provideEnvironmentRepository()
    val serializer = JsonSerializer()

    val defaultOutputPaths = outputPaths()

    val localLoadoutStateRepository =
        FileBasedLocalLoadoutStateRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            serializer = serializer,
        )
    val repositorySettingsRepository =
        FileBasedRepositorySettingsRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            serializer = serializer,
        )
    val remoteFragmentSourceRepository = provideRemoteFragmentSourceRepository()
    val fragmentDestinationRepository = FileSystemFragmentDestinationRepository(fileRepositories.pathResolver)
    val loadoutRepository =
        FileBasedLoadoutRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            serializer = serializer,
        )
    val fragmentRepository =
        FileBasedFragmentRepository(
            fileQueryRepository = fileQueryRepository,
            environmentRepository = environmentRepository,
        )

    val composeLoadout =
        ComposeLoadoutUseCase(
            fragmentRepository = fragmentRepository,
            environmentRepository = environmentRepository
        )
    val writeComposedFiles =
        WriteComposedFilesUseCase(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            localLoadoutStateRepository = localLoadoutStateRepository
        )
    val activateComposedLoadout =
        ActivateComposedLoadoutUseCase(
            writeComposedFiles = writeComposedFiles,
            localLoadoutStateRepository = localLoadoutStateRepository
        )
    val getLoadout =
        GetLoadoutUseCase(
            loadoutRepository = loadoutRepository,
        )
    val getCurrentLoadout =
        GetCurrentLoadoutUseCase(
            localLoadoutStateRepository = localLoadoutStateRepository,
            getLoadout = getLoadout
        )
    val listLoadouts =
        ListLoadoutsUseCase(
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
            loadoutRepository = loadoutRepository
        )
    val unlinkFragmentFromLoadout =
        UnlinkFragmentFromLoadoutUseCase(
            environmentRepository = environmentRepository,
            getLoadout = getLoadout,
            loadoutRepository = loadoutRepository
        )
    val removeLoadout =
        RemoveLoadoutUseCase(
            loadoutRepository = loadoutRepository,
            localLoadoutStateRepository = localLoadoutStateRepository
        )
    val useLoadout =
        UseLoadoutUseCase(
            getLoadout = getLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout
        )
    val getRepositorySettings =
        GetRepositorySettingsUseCase(
            repositorySettingsRepository = repositorySettingsRepository
        )
    val getRepairableRepositorySettings =
        GetRepairableRepositorySettingsUseCase(
            repositorySettingsRepository = repositorySettingsRepository,
        )
    val getTargetLoadoutForSync =
        GetTargetLoadoutForSyncUseCase(
            getRepositorySettings = getRepositorySettings,
            getCurrentLoadout = getCurrentLoadout,
            getLoadout = getLoadout
        )
    val syncLoadout =
        SyncLoadoutUseCase(
            getTargetLoadoutForSync = getTargetLoadoutForSync,
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
            localLoadoutStateRepository = localLoadoutStateRepository,
            getCurrentLoadout = getCurrentLoadout,
            composeLoadout = composeLoadout
        )
    val updateRepositorySettings =
        UpdateRepositorySettingsUseCase(
            repositorySettingsRepository = repositorySettingsRepository,
            loadoutRepository = loadoutRepository,
            getRepairableRepositorySettings = getRepairableRepositorySettings,
        )
    val configureGitHooks =
        ConfigureGitHooksUseCase(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
        )
    val copyFragment =
        CopyFragmentUseCase(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            fragmentDestinationRepository = fragmentDestinationRepository,
            remoteFragmentSourceRepository = remoteFragmentSourceRepository,
            getRepositorySettings = getRepositorySettings,
        )
    val initializeLoadoutProject =
        InitializeLoadoutProjectUseCase(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            listLoadouts = listLoadouts,
            createLoadout = createLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout,
            getRepositorySettings = getRepositorySettings,
            updateRepositorySettings = updateRepositorySettings,
            configureGitHooks = configureGitHooks
        )

    return try {
        scopedBlock(
            ApplicationScope(
                fileQueryRepository = fileQueryRepository,
                fileMutationRepository = fileMutationRepository,
                environmentRepository = environmentRepository,
                createLoadout = createLoadout,
                listLoadouts = listLoadouts,
                linkFragmentToLoadout = linkFragmentToLoadout,
                unlinkFragmentFromLoadout = unlinkFragmentFromLoadout,
                removeLoadout = removeLoadout,
                useLoadout = useLoadout,
                syncLoadout = syncLoadout,
                initializeLoadoutProject = initializeLoadoutProject,
                readCurrentLoadoutStatus = readCurrentLoadoutStatus,
                checkLoadoutSync = checkLoadoutSync,
                writeComposedFiles = writeComposedFiles,
                getRepositorySettings = getRepositorySettings,
                getRepairableRepositorySettings = getRepairableRepositorySettings,
                updateRepositorySettings = updateRepositorySettings,
                copyFragment = copyFragment,
                defaultOutputPaths = defaultOutputPaths
            )
        )
    } finally {
        remoteFragmentSourceRepository.close()
    }
}

data class ApplicationScope(
    val fileQueryRepository: FileQueryRepository,
    val fileMutationRepository: FileMutationRepository,
    val environmentRepository: EnvironmentRepository,
    val createLoadout: CreateLoadoutUseCase,
    val listLoadouts: ListLoadoutsUseCase,
    val linkFragmentToLoadout: LinkFragmentToLoadoutUseCase,
    val unlinkFragmentFromLoadout: UnlinkFragmentFromLoadoutUseCase,
    val removeLoadout: RemoveLoadoutUseCase,
    val useLoadout: UseLoadoutUseCase,
    val syncLoadout: SyncLoadoutUseCase,
    val initializeLoadoutProject: InitializeLoadoutProjectUseCase,
    val readCurrentLoadoutStatus: ReadCurrentLoadoutStatusUseCase,
    val checkLoadoutSync: CheckLoadoutSyncUseCase,
    val writeComposedFiles: WriteComposedFilesUseCase,
    val getRepositorySettings: GetRepositorySettingsUseCase,
    val getRepairableRepositorySettings: GetRepairableRepositorySettingsUseCase,
    val updateRepositorySettings: UpdateRepositorySettingsUseCase,
    val copyFragment: CopyFragmentUseCase,
    val defaultOutputPaths: List<String>,
)

data class FileRepositories(
    val query: FileQueryRepository,
    val mutation: FileMutationRepository,
    val pathResolver: CanonicalPathResolver,
)

expect fun provideFileRepositories(): FileRepositories

expect fun provideEnvironmentRepository(): EnvironmentRepository

expect fun provideRemoteFragmentSourceRepository(): KtorRemoteFragmentSourceRepository
