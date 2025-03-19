package com.example.hisma.model

import com.google.firebase.Timestamp
import java.util.Date

data class Subscription(
    val active: Boolean = false,
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp = Timestamp.now(),
    val plan: String = "free",
    val availableChanges: Int = 0,  // Ya no necesitamos duplicar esto
    val changesUsed: Int = 0,
    val totalChangesAllowed: Int = 0,
    val trialActivated: Boolean = false,
    val trialEndDate: Timestamp = Timestamp.now(),
    val valid: Boolean = false,
    val remainingDays: Int = 0      // Ya no necesitamos duplicar esto
) {
    // Cambiamos nombres de estos métodos para evitar el conflicto
    fun calculateIsValid(): Boolean {
        val now = Timestamp.now()
        return active &&
                now.compareTo(endDate) <= 0 &&
                (availableChanges > changesUsed || totalChangesAllowed == 0)
    }

    fun calculateRemainingDays(): Int {
        val now = Date()
        val end = endDate.toDate()
        val diff = end.time - now.time
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    fun calculateAvailableChanges(): Int {
        return if (totalChangesAllowed == 0) {
            Int.MAX_VALUE
        } else {
            (totalChangesAllowed - changesUsed).coerceAtLeast(0)
        }
    }

    companion object {
        fun createTrial(): Subscription {
            val now = Timestamp.now()
            val calendar = java.util.Calendar.getInstance()
            calendar.time = now.toDate()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 7)
            val trialEnd = Timestamp(calendar.time)

            return Subscription(
                active = true,
                startDate = now,
                endDate = trialEnd,
                plan = "trial",
                availableChanges = 10,
                changesUsed = 0,
                totalChangesAllowed = 10,
                trialActivated = true,
                trialEndDate = trialEnd,
                valid = true,
                remainingDays = 7
            )
        }
    }
}