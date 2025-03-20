package com.example.hisma.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class OilChange(
    @DocumentId
    val id: String = "",
    val dominio: String = "",
    val fecha: String = "",
    val km: String = "",
    val proxKm: String = "",
    val proximaFecha: String = "", // Añadir campo faltante
    val aceite: String = "",
    val aceiteCustom: String = "",
    val sae: String = "",
    val saeCustom: String = "",
    val tipo: String = "",
    val filtros: Map<String, String> = mapOf(),
    val extras: Map<String, String> = mapOf(),
    val observaciones: String = "",
    val ticketNumber: String = "",
    val contactName: String = "",
    val contactCell: String = "",
    val periodicity: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val userName: String = ""
)