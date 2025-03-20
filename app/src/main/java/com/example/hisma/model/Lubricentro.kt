package com.example.hisma.model

import com.google.firebase.Timestamp

data class Lubricentro(
    val id: String = "",
    val nombreFantasia: String = "",
    val responsable: String = "",
    val cuit: String = "",
    val direccion: String = "",
    val telefono: String = "",
    val email: String = "",
    val logoUrl: String = "",
    val subscription: Subscription? = null  // Hacerlo nullable evita problemas de inicialización
)