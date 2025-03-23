package com.example.hisma.model

import com.google.firebase.Timestamp
import java.util.Date

data class Subscription(
    val id: String = "",
    val lubricentroId: String = "",
    val planId: String = "",
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp = Timestamp.now(),
    val active: Boolean = true,
    val valid: Boolean = true,
    val totalChangesAllowed: Int = 0,
    val changesUsed: Int = 0,
    val availableChanges: Int = 0,
    val isPaqueteAdicional: Boolean = false
) {
    fun isValid(): Boolean {
        val currentTime = Timestamp.now()
        return active &&
                endDate.compareTo(currentTime) > 0 &&
                availableChanges > 0
    }

    fun getDiasRestantes(): Int {
        val currentTimeMillis = System.currentTimeMillis()
        val vencimientoMillis = endDate.toDate().time
        val diffMillis = vencimientoMillis - currentTimeMillis
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }
}