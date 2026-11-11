package com.tuapp.piano.domain.model

/**
 * Filtro de la biblioteca. Toda canción nace CREADA y pasa a EXPORTADA
 * la primera vez que se guarda como .mid.
 */
enum class OrigenCancion { CREADA, EXPORTADA }
