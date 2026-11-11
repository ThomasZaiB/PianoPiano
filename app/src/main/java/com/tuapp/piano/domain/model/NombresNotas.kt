package com.tuapp.piano.domain.model

/** Nombres de nota: 60 -> "Do4" / "C4", 61 -> "Do#4" / "C#4", 21 -> "La0" / "A0". */
object NombresNotas {
    private val LATINOS = arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    private val INGLESES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun nombre(midi: Int, notacion: Notacion = Notacion.LATINA): String = sinOctava(midi, notacion) + (midi / 12 - 1)

    /** Solo la nota, sin número de octava ("Do", "C"). */
    fun sinOctava(midi: Int, notacion: Notacion = Notacion.LATINA): String =
        (if (notacion == Notacion.LATINA) LATINOS else INGLESES)[midi % 12]

    fun lista(midis: Collection<Int>, notacion: Notacion = Notacion.LATINA): String =
        midis.sorted().joinToString(" · ") { nombre(it, notacion) }
}
