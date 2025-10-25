package cli.di

import data.repository.NativeFileRepository
import data.repository.NativeEnvironmentRepository
import domain.repository.FileRepository
import domain.repository.EnvironmentRepository

actual fun provideFileRepository(): FileRepository = NativeFileRepository()
actual fun provideEnvironmentRepository(): EnvironmentRepository = NativeEnvironmentRepository()