package cli.di

import data.repository.NativeEnvironmentRepository
import data.repository.NativeFileRepository
import domain.repository.EnvironmentRepository
import domain.repository.FileRepository

actual fun provideFileRepository(): FileRepository = NativeFileRepository()

actual fun provideEnvironmentRepository(): EnvironmentRepository = NativeEnvironmentRepository()
