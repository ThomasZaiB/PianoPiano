package com.tuapp.piano.domain.model

/** Cómo se escriben las notas: Do4 (latina) o C4 (inglesa). */
enum class Notacion { LATINA, INGLESA }

/** Qué teclas muestran su nombre en el teclado. */
enum class EtiquetasTeclado { NINGUNA, SOLO_DO, BLANCAS }

/** Rapidez con la que caen las notas: cuántos compases caben en la cascada. */
enum class VelocidadCascada(val compasesVisibles: Int) { LENTA(4), NORMAL(2), RAPIDA(1) }

/** Preferencias del usuario. Los valores por defecto son el comportamiento original de la app. */
data class Ajustes(
    val notacion: Notacion = Notacion.LATINA,
    val etiquetas: EtiquetasTeclado = EtiquetasTeclado.SOLO_DO,
    /** Primera nota de la mano derecha; las de abajo cuentan como mano izquierda. */
    val notaDivision: Int = 60,
    /** true: la fuerza depende de dónde se toca la tecla (más abajo = más fuerte). */
    val dinamicaTactil: Boolean = true,
    val velocidadCascada: VelocidadCascada = VelocidadCascada.NORMAL,
    /** Zoom del teclado: teclas blancas visibles. Se recuerda entre sesiones. */
    val teclasVisibles: Int = 21,
) {
    companion object {
        /** Rango ofrecido para la división de manos: Do2 … Do6. */
        val RANGO_DIVISION = 36..84
        val RANGO_TECLAS_VISIBLES = 10..52
    }
}
