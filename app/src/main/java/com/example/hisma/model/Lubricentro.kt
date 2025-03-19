package com.example.hisma.model


data class Lubricentronew(
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