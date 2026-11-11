package com.tuapp.piano.domain.repository

/**
 * Foco de audio del sistema: el turno para sonar. Una llamada o la música de otra app lo
 * piden y, si no lo soltamos, se mezclan con el piano. La implementación vive en data/.
 */
interface FocoAudioRepository {
    /**
     * Pide el foco (idempotente si ya se tiene). [alPerder] se invoca en el hilo principal
     * cada vez que otra app lo toma, aunque sea por un momento (una notificación con sonido
     * no cuenta: el sistema solo baja el volumen).
     */
    fun solicitar(alPerder: () -> Unit): Boolean

    fun abandonar()
}
