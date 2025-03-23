package com.example.hisma.ui.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.model.Subscription
import com.example.hisma.ui.navigation.Screen
import com.example.hisma.utils.SubscriptionManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisSuscripcionesScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val subscriptionManager = remember { SubscriptionManager(context, auth, db) }

    var subscriptions by remember { mutableStateOf<List<Subscription>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var mostrarDialogoSolicitud by remember { mutableStateOf(false) }

    // Función para cargar suscripciones
    fun loadSubscriptions() {
        isLoading = true
        subscriptionManager.getAllSubscriptions { subscriptionsList ->
            subscriptions = subscriptionsList
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        // Cargar suscripciones
        loadSubscriptions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Suscripciones") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (subscriptions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tienes suscripciones registradas",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Resumen de suscripciones
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Resumen",
                                style = MaterialTheme.typography.titleLarge
                            )

                            val activas = subscriptions.filter { it.active && it.valid }
                            val activasPrincipales = activas.filter { !it.isPaqueteAdicional }
                            val activasAdicionales = activas.filter { it.isPaqueteAdicional }

                            val totalCambiosRestantes = activas.sumOf { it.availableChanges }

                            val suscripcionPrincipal = activasPrincipales.firstOrNull()
                            val diasRestantes = if (suscripcionPrincipal != null) {
                                val hoy = Calendar.getInstance().time.time
                                val vencimiento = suscripcionPrincipal.endDate.toDate().time
                                ((vencimiento - hoy) / (1000 * 60 * 60 * 24)).toInt()
                            } else 0

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Suscripciones activas: ${activasPrincipales.size}")
                            Text("Paquetes adicionales activos: ${activasAdicionales.size}")
                            Text("Total cambios restantes: $totalCambiosRestantes")

                            if (suscripcionPrincipal != null) {
                                val fechaVencimiento = SimpleDateFormat(
                                    "dd/MM/yyyy", Locale.getDefault()
                                ).format(suscripcionPrincipal.endDate.toDate())

                                Text("Vencimiento: $fechaVencimiento ($diasRestantes días)")
                            }
                        }
                    }

                    // Guardar la última sincronización en SharedPreferences
                    val sharedPrefs = LocalContext.current.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
                    val lastSyncTime = remember { mutableStateOf(sharedPrefs.getLong("last_sync_time", 0)) }
                    val currentTime = System.currentTimeMillis()
                    val oneDayInMillis = 24 * 60 * 60 * 1000

                    // Calcular si puede sincronizar
                    val canSync = currentTime - lastSyncTime.value > oneDayInMillis ||
                            subscriptions.all { !it.active || !it.valid }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    isLoading = true
                                    val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                                    // Obtener datos del lubricentro
                                    val lubDoc = db.collection("lubricentros")
                                        .document(currentUser.uid)
                                        .get()
                                        .await()

                                    val subscription = lubDoc.get("subscription") as? Map<String, Any>

                                    if (subscription != null) {
                                        val availableChanges = (subscription["availableChanges"] as? Number)?.toInt() ?: 0
                                        val totalChangesAllowed = (subscription["totalChangesAllowed"] as? Number)?.toInt() ?: 0

                                        // Obtener suscripciones activas
                                        val suscripcionesSnapshot = db.collection("suscripciones")
                                            .whereEqualTo("lubricentroId", currentUser.uid)
                                            .whereEqualTo("estado", "activa")
                                            .get()
                                            .await()

                                        if (!suscripcionesSnapshot.isEmpty) {
                                            // Solo actualizar si los cambios disponibles son mayores
                                            for (doc in suscripcionesSnapshot.documents) {
                                                val currentAvailableChanges = doc.getLong("cambiosRestantes")?.toInt() ?: 0
                                                if (availableChanges > currentAvailableChanges) {
                                                    db.collection("suscripciones")
                                                        .document(doc.id)
                                                        .update(
                                                            "cambiosTotales", totalChangesAllowed,
                                                            "cambiosRestantes", availableChanges
                                                        )
                                                        .await()
                                                }
                                            }

                                            // Guardar timestamp de sincronización
                                            lastSyncTime.value = currentTime
                                            sharedPrefs.edit().putLong("last_sync_time", currentTime).apply()

                                            Toast.makeText(context, "Suscripción sincronizada correctamente", Toast.LENGTH_SHORT).show()
                                            loadSubscriptions()
                                        } else {
                                            Toast.makeText(context, "No hay suscripciones activas para sincronizar", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No hay información de suscripción disponible", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        enabled = canSync && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                if (canSync) "Sincronizar Información"
                                else "Sincronización no disponible (24h)"
                            )
                        }
                    }

                    Text(
                        text = "Detalle de Suscripciones",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(subscriptions) { subscription ->
                            SubscriptionCard(subscription = subscription)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { mostrarDialogoSolicitud = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Solicitar Nueva Suscripción")
                }
            }
        }
    }

    if (mostrarDialogoSolicitud) {
        SolicitarSuscripcionDialog(
            onDismiss = { mostrarDialogoSolicitud = false },
            onSolicitar = { tipoSuscripcion, isPaqueteAdicional ->
                // Implementar la lógica de solicitud
                subscriptionManager.requestNewSubscription(
                    tipoSuscripcion,
                    isPaqueteAdicional
                ) { success ->
                    if (success) {
                        Toast.makeText(
                            context,
                            "Solicitud enviada. Un administrador la revisará pronto.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Error al enviar solicitud",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    mostrarDialogoSolicitud = false
                }
            }
        )
    }
}

@Composable
fun SubscriptionCard(subscription: Subscription) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (subscription.isPaqueteAdicional) "Paquete Adicional" else "Suscripción",
                    style = MaterialTheme.typography.titleMedium
                )

                val estadoColor = when {
                    subscription.active && subscription.valid -> Color.Green
                    !subscription.active -> Color.Red
                    else -> Color(0xFFFFA000) // Naranja
                }

                val estadoTexto = when {
                    subscription.active && subscription.valid -> "Activa"
                    !subscription.active -> "Cancelada"
                    else -> "Vencida"
                }

                Text(
                    text = estadoTexto,
                    color = estadoColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Plan ID: ${subscription.planId}")

            val inicioStr = dateFormat.format(subscription.startDate.toDate())
            val finStr = dateFormat.format(subscription.endDate.toDate())

            Text("Inicio: $inicioStr")
            Text("Fin: $finStr")

            if (subscription.active && subscription.valid) {
                Text("Cambios restantes: ${subscription.availableChanges}")
                Text("Cambios utilizados: ${subscription.changesUsed}")

                val porcentajeUtilizado = if (subscription.totalChangesAllowed > 0) {
                    (subscription.changesUsed.toFloat() / subscription.totalChangesAllowed.toFloat()) * 100f
                } else 0f

                Text("Utilizado: ${String.format("%.1f", porcentajeUtilizado)}%")

                LinearProgressIndicator(
                    progress = porcentajeUtilizado / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SolicitarSuscripcionDialog(
    onDismiss: () -> Unit,
    onSolicitar: (tipoSuscripcion: String, isPaqueteAdicional: Boolean) -> Unit
) {
    var tipoSeleccionado by remember { mutableStateOf("basica") }
    var esPaqueteAdicional by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar Suscripción") },
        text = {
            Column {
                Text("Selecciona el tipo de suscripción:")

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !esPaqueteAdicional,
                        onClick = { esPaqueteAdicional = false }
                    )
                    Text(
                        "Suscripción completa",
                        modifier = Modifier.clickable { esPaqueteAdicional = false }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = esPaqueteAdicional,
                        onClick = { esPaqueteAdicional = true }
                    )
                    Text(
                        "Paquete adicional de cambios",
                        modifier = Modifier.clickable { esPaqueteAdicional = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!esPaqueteAdicional) {
                    Text("Selecciona el plan:")

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tipoSeleccionado == "basica",
                            onClick = { tipoSeleccionado = "basica" }
                        )
                        Column {
                            Text(
                                "Plan Básico",
                                modifier = Modifier.clickable { tipoSeleccionado = "basica" }
                            )
                            Text(
                                "100 cambios - 6 meses",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tipoSeleccionado == "premium",
                            onClick = { tipoSeleccionado = "premium" }
                        )
                        Column {
                            Text(
                                "Plan Premium",
                                modifier = Modifier.clickable { tipoSeleccionado = "premium" }
                            )
                            Text(
                                "300 cambios - 12 meses",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Text("Selecciona el paquete:")

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tipoSeleccionado == "paquete50",
                            onClick = { tipoSeleccionado = "paquete50" }
                        )
                        Column {
                            Text(
                                "Paquete 50",
                                modifier = Modifier.clickable { tipoSeleccionado = "paquete50" }
                            )
                            Text(
                                "50 cambios adicionales",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tipoSeleccionado == "paquete100",
                            onClick = { tipoSeleccionado = "paquete100" }
                        )
                        Column {
                            Text(
                                "Paquete 100",
                                modifier = Modifier.clickable { tipoSeleccionado = "paquete100" }
                            )
                            Text(
                                "100 cambios adicionales",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSolicitar(tipoSeleccionado, esPaqueteAdicional) }
            ) {
                Text("Solicitar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}