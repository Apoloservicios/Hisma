package com.example.hisma.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisma.model.OilChange
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ReportsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _activitySummary = MutableStateFlow<ActivitySummary?>(null)
    val activitySummary: StateFlow<ActivitySummary?> = _activitySummary

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadActivitySummary() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _error.value = "Usuario no autenticado"
                    return@launch
                }

                // Obtener todos los cambios de aceite
                val snapshot = db.collection("lubricentros")
                    .document(currentUser.uid)
                    .collection("cambiosAceite")
                    .orderBy("fecha", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val oilChanges = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(OilChange::class.java)?.copy(id = doc.id)
                }

                // Análisis de datos
                val today = Calendar.getInstance()
                val currentMonth = today.get(Calendar.MONTH)
                val currentYear = today.get(Calendar.YEAR)

                // Filtrar por periodos
                val thisMonthChanges = oilChanges.filter {
                    val calendar = parseDate(it.fecha)
                    calendar?.get(Calendar.MONTH) == currentMonth &&
                            calendar.get(Calendar.YEAR) == currentYear
                }

                // Obtener mes anterior
                val lastMonth = if (currentMonth == 0) {
                    Pair(11, currentYear - 1) // Diciembre del año anterior
                } else {
                    Pair(currentMonth - 1, currentYear)
                }

                val lastMonthChanges = oilChanges.filter {
                    val calendar = parseDate(it.fecha)
                    calendar?.get(Calendar.MONTH) == lastMonth.first &&
                            calendar.get(Calendar.YEAR) == lastMonth.second
                }

                // Construir resumen
                val summary = ActivitySummary(
                    totalChanges = oilChanges.size,
                    thisMonthChanges = thisMonthChanges.size,
                    lastMonthChanges = lastMonthChanges.size,
                    monthlyData = getMonthlyData(oilChanges),
                    topOils = getTopOils(oilChanges),
                    topFilters = getTopFilters(oilChanges)
                )

                _activitySummary.value = summary
            } catch (e: Exception) {
                _error.value = "Error al cargar datos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseDate(dateString: String): Calendar? {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = sdf.parse(dateString) ?: return null
            Calendar.getInstance().apply { time = date }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMonthlyData(oilChanges: List<OilChange>): Map<String, Int> {
        val monthlyData = mutableMapOf<String, Int>()
        val sdf = SimpleDateFormat("MM/yyyy", Locale.getDefault())

        // Inicializar los últimos 6 meses con cero
        val calendar = Calendar.getInstance()
        for (i in 0 until 6) {
            val monthKey = sdf.format(calendar.time)
            monthlyData[monthKey] = 0
            calendar.add(Calendar.MONTH, -1)
        }

        // Contar cambios por mes
        oilChanges.forEach { change ->
            val calendar = parseDate(change.fecha) ?: return@forEach
            val monthKey = sdf.format(calendar.time)
            monthlyData[monthKey] = (monthlyData[monthKey] ?: 0) + 1
        }

        return monthlyData.toSortedMap(compareBy {
            val parts = it.split("/")
            val year = parts[1].toInt()
            val month = parts[0].toInt()
            year * 100 + month
        })
    }

    private fun getTopOils(oilChanges: List<OilChange>): Map<String, Int> {
        return oilChanges
            .groupBy { if (it.aceite.isBlank()) it.aceiteCustom else it.aceite }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .toMap()
    }

    private fun getTopFilters(oilChanges: List<OilChange>): Map<String, Int> {
        val filterCounts = mutableMapOf<String, Int>()

        oilChanges.forEach { change ->
            change.filtros.keys.forEach { filterType ->
                filterCounts[filterType] = (filterCounts[filterType] ?: 0) + 1
            }
        }

        return filterCounts.toList()
            .sortedByDescending { it.second }
            .toMap()
    }

    data class ActivitySummary(
        val totalChanges: Int,
        val thisMonthChanges: Int,
        val lastMonthChanges: Int,
        val monthlyData: Map<String, Int>,
        val topOils: Map<String, Int>,
        val topFilters: Map<String, Int>
    ) {
        val percentageChange: Double = if (lastMonthChanges > 0) {
            ((thisMonthChanges - lastMonthChanges) / lastMonthChanges.toDouble()) * 100
        } else if (thisMonthChanges > 0) {
            100.0
        } else {
            0.0
        }
    }
}