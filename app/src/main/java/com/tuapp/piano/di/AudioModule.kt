package com.tuapp.piano.di

import com.tuapp.piano.data.audio.AudioBridge
import com.tuapp.piano.data.audio.FocoAudioAndroid
import com.tuapp.piano.domain.repository.AudioEngineRepository
import com.tuapp.piano.domain.repository.FocoAudioRepository
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

    @Binds
    @Singleton
    abstract fun bindFocoAudio(impl: FocoAudioAndroid): FocoAudioRepository
}
