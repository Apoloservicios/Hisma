package com.example.hisma.model

import com.google.firebase.Timestamp

data class Suscription(
    val id: String = "",
    val lubricentroId: String = "",
    val planId: String = "",
    val fechaInicio: Timestamp = Timestamp.now(),
    val fechaFin: Timestamp = Timestamp.now(),
    var estado: String = "activa", // "activa", "pendiente", "cancelada"
    val cambiosTotales: Int = 100,
    val cambiosRealizados: Int = 0,
    val cambiosRestantes: Int = 100,
    val isPaqueteAdicional: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
) {
    fun isValid(): Boolean {
        val now = Timestamp.now()
        return estado == "activa" && fechaFin > now && cambiosRestantes > 0
    }
}