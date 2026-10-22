package cli.di

import cli.Constants
import data.filesystem.CanonicalPathResolver
import data.repository.FileBasedFragmentRepository
import data.repository.FileBasedGitConfigRepository
import data.repository.FileBasedGitExcludeRepository
import data.repository.FileBasedGitIndexRepository
import data.repository.FileBasedGitRepositoryLayoutRepository
import data.repository.FileBasedLoadoutRepository
import data.repository.FileBasedLocalLoadoutStateRepository
import data.repository.FileBasedRepositorySettingsRepository
import data.repository.FileBasedUserSettingsRepository
import data.repository.FileSystemFragmentDestinationRepository
import data.repository.KtorRemoteFragmentSourceRepository
import data.repository.Sha256FileDigestRepository
import data.serialization.JsonSerializer
import domain.entity.LoadoutFileLocations
import domain.repository.EnvironmentRepository
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.usecase.ActivateComposedLoadoutUseCase
import domain.usecase.BackUpOutputFilesUseCase
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.ComposeLoadoutUseCase
import domain.usecase.ConfigureGitHooksUseCase
import domain.usecase.ConfigureGitIgnoreRulesUseCase
import domain.usecase.CopyFragmentUseCase
import domain.usecase.CreateLoadoutUseCase
import domain.usecase.GetCurrentLoadoutUseCase
import domain.usecase.GetLoadoutUseCase
import domain.usecase.GetLocalLoadoutStateUseCase
import domain.usecase.GetRepairableRepositorySettingsUseCase
import domain.usecase.GetRepositorySettingsUseCase
import domain.usecase.GetTargetLoadoutForSyncUseCase
import domain.usecase.GetUserSettingsUseCase
import domain.usecase.InitializeLoadoutProjectUseCase
import domain.usecase.InspectOutputPathUseCase
import domain.usecase.LinkFragmentToLoadoutUseCase
import domain.usecase.ListLoadoutsUseCase
import domain.usecase.PruneOutputFilesUseCase
import domain.usecase.ReadCurrentLoadoutStatusUseCase
import domain.usecase.RemoveLoadoutUseCase
import domain.usecase.ResolveOutputFilesUseCase
import domain.usecase.SyncLoadoutUseCase
import domain.usecase.UnlinkFragmentFromLoadoutUseCase
import domain.usecase.UpdateRepositorySettingsUseCase
import domain.usecase.UpdateUserSettingsUseCase
import domain.usecase.UseLoadoutUseCase
import domain.usecase.WriteComposedFilesUseCase

fun <T> withApplicationScope(scopedBlock: ApplicationScope.() -> T): T {
    val fileRepositories = provideFileRepositories()
    val fileQueryRepository = fileRepositories.query
    val fileMutationRepository = fileRepositories.mutation
    val environmentRepository = provideEnvironmentRepository()
    val serializer = JsonSerializer()
    val loadoutFileLocations =
        LoadoutFileLocations(
            localStateFile = Constants.LOCAL_LOADOUT_STATE_FILE,
            repositorySettingsFile = Constants.REPOSITORY_SETTINGS_FILE,
            loadoutsDirectory = Constants.LOADOUTS_DIR,
            backupsDirectory = Constants.BACKUPS_DIR,
            hooksDirectory = Constants.HOOKS_DIR,
        )

    val localLoadoutStateRepository =
        FileBasedLocalLoadoutStateRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            serializer = serializer,
            localStateFile = loadoutFileLocations.localStateFile,
        )
    val repositorySettingsRepository =
        FileBasedRepositorySettingsRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            serializer = serializer,
            repositorySettingsFile = loadoutFileLocations.repositorySettingsFile,
        )
    val userSettingsRepository =
        FileBasedUserSettingsRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            environmentRepository = environmentRepository,
            serializer = serializer,
        )
    val remoteFragmentSourceRepository = provideRemoteFragmentSourceRepository()
    val fragmentDestinationRepository = FileSystemFragmentDestinationRepository(fileRepositories.pathResolver)
    val loadoutRepository =
        FileBasedLoadoutRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            serializer = serializer,
            loadoutsDirectory = loadoutFileLocations.loadoutsDirectory,
        )
    val fragmentRepository =
        FileBasedFragmentRepository(
            fileQueryRepository = fileQueryRepository,
            environmentRepository = environmentRepository,
        )
    val gitRepositoryLayoutRepository =
        FileBasedGitRepositoryLayoutRepository(
            fileQueryRepository = fileQueryRepository,
        )
    val gitIndexRepository =
        FileBasedGitIndexRepository(
            fileQueryRepository = fileQueryRepository,
            gitRepositoryLayoutRepository = gitRepositoryLayoutRepository,
        )
    val gitExcludeRepository =
        FileBasedGitExcludeRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
        )
    val gitConfigRepository =
        FileBasedGitConfigRepository(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
        )
    val fileDigestRepository =
        Sha256FileDigestRepository(
            fileQueryRepository = fileQueryRepository,
        )

    val composeLoadout =
        ComposeLoadoutUseCase(
            fragmentRepository = fragmentRepository,
            environmentRepository = environmentRepository
        )
    val getRepositorySettings =
        GetRepositorySettingsUseCase(
            repositorySettingsRepository = repositorySettingsRepository,
            loadoutFileLocations = loadoutFileLocations,
        )
    val getRepairableRepositorySettings =
        GetRepairableRepositorySettingsUseCase(
            repositorySettingsRepository = repositorySettingsRepository,
            loadoutFileLocations = loadoutFileLocations,
        )
    val getLocalLoadoutState =
        GetLocalLoadoutStateUseCase(
            localLoadoutStateRepository = localLoadoutStateRepository,
        )
    val resolveOutputFiles =
        ResolveOutputFilesUseCase(
            userSettingsRepository = userSettingsRepository,
            loadoutFileLocations = loadoutFileLocations,
        )
    val getUserSettings =
        GetUserSettingsUseCase(
            userSettingsRepository = userSettingsRepository,
            loadoutFileLocations = loadoutFileLocations,
        )
    val updateUserSettings =
        UpdateUserSettingsUseCase(
            userSettingsRepository = userSettingsRepository,
            loadoutFileLocations = loadoutFileLocations,
        )
    val inspectOutputPath =
        InspectOutputPathUseCase(
            fileQueryRepository = fileQueryRepository,
            fileDigestRepository = fileDigestRepository,
        )
    val configureGitIgnoreRules =
        ConfigureGitIgnoreRulesUseCase(
            gitRepositoryLayoutRepository = gitRepositoryLayoutRepository,
            gitExcludeRepository = gitExcludeRepository,
            getRepositorySettings = getRepairableRepositorySettings,
            loadoutFileLocations = loadoutFileLocations,
        )
    val backUpOutputFiles =
        BackUpOutputFilesUseCase(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            environmentRepository = environmentRepository,
            getRepositorySettings = getRepairableRepositorySettings,
            loadoutFileLocations = loadoutFileLocations,
        )
    val pruneOutputFiles =
        PruneOutputFilesUseCase(
            inspectOutputPath = inspectOutputPath,
            fileMutationRepository = fileMutationRepository,
        )
    val writeComposedFiles =
        WriteComposedFilesUseCase(
            fileQueryRepository = fileQueryRepository,
            fileMutationRepository = fileMutationRepository,
            fileDigestRepository = fileDigestRepository,
            getLocalLoadoutState = getLocalLoadoutState,
            localLoadoutStateRepository = localLoadoutStateRepository,
            inspectOutputPath = inspectOutputPath,
            backUpOutputFiles = backUpOutputFiles,
            pruneOutputFiles = pruneOutputFiles,
            gitIndexRepository = gitIndexRepository,
        )
    val activateComposedLoadout =
        ActivateComposedLoadoutUseCase(
            getRepositorySettings = getRepairableRepositorySettings,
            resolveOutputFiles = resolveOutputFiles,
            writeComposedFiles = writeComposedFiles,
            configureGitIgnoreRules = configureGitIgnoreRules,
        )
    val getLoadout =
        GetLoadoutUseCase(
            loadoutRepository = loadoutRepository,
        )
    val getCurrentLoadout =
        GetCurrentLoadoutUseCase(
            getLocalLoadoutState = getLocalLoadoutState,
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
            getLocalLoadoutState = getLocalLoadoutState,
            localLoadoutStateRepository = localLoadoutStateRepository
        )
    val useLoadout =
        UseLoadoutUseCase(
            getLoadout = getLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout
        )
    val getTargetLoadoutForSync =
        GetTargetLoadoutForSyncUseCase(
            getRepositorySettings = getRepairableRepositorySettings,
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
            getLocalLoadoutState = getLocalLoadoutState,
            getLoadout = getLoadout,
            composeLoadout = composeLoadout,
            getRepositorySettings = getRepairableRepositorySettings,
            resolveOutputFiles = resolveOutputFiles,
        )
    val updateRepositorySettings =
        UpdateRepositorySettingsUseCase(
            repositorySettingsRepository = repositorySettingsRepository,
            loadoutRepository = loadoutRepository,
            getRepairableRepositorySettings = getRepairableRepositorySettings,
            loadoutFileLocations = loadoutFileLocations,
        )
    val configureGitHooks =
        ConfigureGitHooksUseCase(
            fileMutationRepository = fileMutationRepository,
            gitRepositoryLayoutRepository = gitRepositoryLayoutRepository,
            gitConfigRepository = gitConfigRepository,
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
            listLoadouts = listLoadouts,
            createLoadout = createLoadout,
            composeLoadout = composeLoadout,
            activateComposedLoadout = activateComposedLoadout,
            resolveOutputFiles = resolveOutputFiles,
            getRepositorySettings = getRepairableRepositorySettings,
            updateRepositorySettings = updateRepositorySettings,
            configureGitHooks = configureGitHooks,
            configureGitIgnoreRules = configureGitIgnoreRules,
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
                resolveOutputFiles = resolveOutputFiles,
                getUserSettings = getUserSettings,
                updateUserSettings = updateUserSettings,
                copyFragment = copyFragment,
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
    val resolveOutputFiles: ResolveOutputFilesUseCase,
    val getUserSettings: GetUserSettingsUseCase,
    val updateUserSettings: UpdateUserSettingsUseCase,
    val copyFragment: CopyFragmentUseCase,
)

data class FileRepositories(
    val query: FileQueryRepository,
    val mutation: FileMutationRepository,
    val pathResolver: CanonicalPathResolver,
)

expect fun provideFileRepositories(): FileRepositories

expect fun provideEnvironmentRepository(): EnvironmentRepository

expect fun provideRemoteFragmentSourceRepository(): KtorRemoteFragmentSourceRepository
