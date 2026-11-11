package com.tuapp.piano.domain.model

/** Rejilla de cuantización, expresada en ticks PPQ (PPQ = 960 = una negra). */
enum class Cuantizacion(val ticks: Int, val etiqueta: String) {
    NINGUNA(0, "Sin cuantizar"),
    NEGRA(PPQ, "1/4"),
    CORCHEA(PPQ / 2, "1/8"),
    SEMICORCHEA(PPQ / 4, "1/16"),
    FUSA(PPQ / 8, "1/32"),

    /** Corchea de tresillo: tres por negra (1/12 de redonda). */
    TRESILLO(PPQ / 3, "Tresillos");

    companion object {
        fun desde(nombre: String?): Cuantizacion = entries.firstOrNull { it.name == nombre } ?: SEMICORCHEA
    }
}
