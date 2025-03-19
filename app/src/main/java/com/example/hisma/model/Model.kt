package com.example.hisma.model

import com.google.firebase.Timestamp

// Data class del lubricentro (tiene el logoUrl)
data class Lubricentro(
    val uid: String = "",
    val nombreFantasia: String = "",
    val responsable: String = "",
    val cuit: String = "",
    val direccion: String = "",
    val telefono: String = "",
    val email: String = "",
    val logoUrl: String = "",
    val subscription: Subscription = Subscription()
)



// Data class del cambio de aceite, incluyendo nuevos campos: ticketNumber, contactName, contactCell y periodicity
data class OilChange(
    val id: String = "",
    val dominio: String = "",
    val fecha: String = "",
    val km: String = "",
    val proxKm: String = "",
    val aceite: String = "",
    val aceiteCustom: String = "",
    val sae: String = "",
    val saeCustom: String = "",
    val tipo: String = "",
    val extras: Map<String, String> = emptyMap(),
    val filtros: Map<String, String> = emptyMap(),
    val observaciones: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val userName: String = "",
    // Nuevos campos
    val ticketNumber: String = "",
    val contactName: String = "",
    val contactCell: String = "",
    val periodicity: String = "",
    val proximaFecha: String = "" // Nueva propiedad para la fecha del próximo cambio
)
