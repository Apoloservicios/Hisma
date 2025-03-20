package com.example.hisma.model

import com.google.firebase.Timestamp

data class Subscription(
    val id: String = "",
    val lubricentroId: String = "",
    val planId: String = "",
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp = Timestamp.now(),
    val active: Boolean = true,
    val valid: Boolean = true,
    val totalChangesAllowed: Int = 100,
    val changesUsed: Int = 0,
    val availableChanges: Int = 100,
    val trialActivated: Boolean = false,
    val isPaqueteAdicional: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    fun calculateIsValid(): Boolean {
        val now = Timestamp.now()
        return active && endDate > now && availableChanges > 0
    }
}