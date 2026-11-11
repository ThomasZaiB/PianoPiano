package com.tuapp.piano.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.tuapp.piano.domain.repository.FocoAudioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Foco de audio con AudioFocusRequest (API 26+, igual que minSdk). */
@Singleton
class FocoAudioAndroid @Inject constructor(
    @ApplicationContext context: Context,
) : FocoAudioRepository {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bloqueo = Any()
    private var tieneFoco = false
    @Volatile private var alPerder: (() -> Unit)? = null

    private val oyente = AudioManager.OnAudioFocusChangeListener { cambio ->
        when (cambio) {
            AudioManager.AUDIOFOCUS_GAIN -> synchronized(bloqueo) { tieneFoco = true }
            // Pérdida definitiva (otra app de música) o temporal (llamada): se pausa. No se
            // reanuda solo al recuperarlo: el usuario decide cuándo seguir tocando.
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                synchronized(bloqueo) { if (cambio == AudioManager.AUDIOFOCUS_LOSS) tieneFoco = false }
                alPerder?.invoke()
            }
            // LOSS_TRANSIENT_CAN_DUCK: el sistema baja nuestro volumen solo (API 26+).
        }
    }

    private val peticion = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(oyente, Handler(Looper.getMainLooper()))
        .build()

    override fun solicitar(alPerder: () -> Unit): Boolean {
        this.alPerder = alPerder
        synchronized(bloqueo) {
            if (tieneFoco) return true
            tieneFoco = audioManager.requestAudioFocus(peticion) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return tieneFoco
        }
    }

    override fun abandonar() {
        synchronized(bloqueo) {
            if (!tieneFoco) return
            tieneFoco = false
            audioManager.abandonAudioFocusRequest(peticion)
        }
    }
}
