package com.tuapp.piano.di

import com.tuapp.piano.data.audio.AudioBridge
import com.tuapp.piano.domain.repository.AudioEngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindAudioEngine(impl: AudioBridge): AudioEngineRepository
}
