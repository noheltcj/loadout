package cli.di

import data.platform.platformHttpClientEngine
import data.repository.KtorRemoteFragmentSourceRepository
import data.repository.NativeEnvironmentRepository
import data.repository.NativeFileRepository
import domain.repository.EnvironmentRepository

actual fun provideFileRepositories(): FileRepositories {
    val repository = NativeFileRepository()
    return FileRepositories(query = repository, mutation = repository, pathResolver = repository)
}

actual fun provideEnvironmentRepository(): EnvironmentRepository = NativeEnvironmentRepository()

actual fun provideRemoteFragmentSourceRepository(): KtorRemoteFragmentSourceRepository =
    KtorRemoteFragmentSourceRepository(createEngine = ::platformHttpClientEngine)
