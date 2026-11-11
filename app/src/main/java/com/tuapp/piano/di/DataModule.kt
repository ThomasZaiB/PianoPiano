package com.tuapp.piano.di

import android.content.Context
import androidx.room.Room
import com.tuapp.piano.data.actividad.ActividadRepositoryImpl
import com.tuapp.piano.data.ajustes.AjustesRepositoryImpl
import com.tuapp.piano.data.local.PianoDatabase
import com.tuapp.piano.data.local.SemillasCallback
import com.tuapp.piano.data.local.dao.CancionDao
import com.tuapp.piano.data.midi.ExportadorMidiSaf
import com.tuapp.piano.data.repository.CancionRepositoryImpl
import com.tuapp.piano.domain.repository.ActividadRepository
import com.tuapp.piano.domain.repository.AjustesRepository
import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.repository.ExportadorMidi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PianoDatabase =
        Room.databaseBuilder(context, PianoDatabase::class.java, PianoDatabase.NOMBRE)
            .addCallback(SemillasCallback(context))
            .build()

    @Provides
    fun provideCancionDao(db: PianoDatabase): CancionDao = db.cancionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCancionRepository(impl: CancionRepositoryImpl): CancionRepository

    @Binds
    abstract fun bindExportadorMidi(impl: ExportadorMidiSaf): ExportadorMidi

    @Binds
    @Singleton
    abstract fun bindAjustesRepository(impl: AjustesRepositoryImpl): AjustesRepository

    @Binds
    @Singleton
    abstract fun bindActividadRepository(impl: ActividadRepositoryImpl): ActividadRepository
}
