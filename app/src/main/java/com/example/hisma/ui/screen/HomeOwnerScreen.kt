package com.example.hisma.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.ui.navigation.Screen
import com.example.hisma.utils.OilChangeManager
import com.example.hisma.utils.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeOwnerScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    var userName by remember { mutableStateOf("Dueño") }
    var isLoading by remember { mutableStateOf(true) }

    // Variables para información de suscripción
    var suscripcionActiva by remember { mutableStateOf(false) }
    var cambiosRestantes by remember { mutableStateOf(0) }
    var diasRestantes by remember { mutableStateOf(0) }
    var fechaVencimiento by remember { mutableStateOf("") }
    var isLoadingSuscripcion by remember { mutableStateOf(true) }


    // En HomeOwnerScreen.kt, en LaunchedEffect:

    LaunchedEffect(Unit) {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val doc = db.collection("lubricentros").document(currentUser.uid).get().await()
                userName = if (doc.exists() && doc.getString("nombreFantasia") != null)
                    doc.getString("nombreFantasia")!!
                else
                    currentUser.email ?: "Dueño"

                isLoading = false

                // Usar el SubscriptionManager actualizado
                val subscriptionManager = SubscriptionManager(context, auth, db)
                subscriptionManager.checkActiveSubscription { isActive, subscription ->
                    suscripcionActiva = isActive
                    if (isActive && subscription != null) {
                        cambiosRestantes = subscription.availableChanges

                        // Calcular días restantes
                        val hoy = Calendar.getInstance().time.time
                        val vencimiento = subscription.endDate.toDate().time
                        diasRestantes = ((vencimiento - hoy) / (1000L * 60L * 60L * 24L)).toInt()

                        // Formatear fecha de vencimiento
                        fechaVencimiento = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                            .format(subscription.endDate.toDate())
                    }
                    isLoadingSuscripcion = false
                }
            } else {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.HomeOwner.route) { inclusive = true }
                }
            }
        } catch (e: Exception) {
            // Manejo de error
            isLoading = false
            isLoadingSuscripcion = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel - Dueño") },
                actions = {
                    IconButton(onClick = {
                        auth.signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.HomeOwner.route) { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar sesión"
                        )
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "¡Bienvenido!",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Panel de Dueño",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Estado de Suscripción
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Estado de Suscripción",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isLoadingSuscripcion) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))

                            if (suscripcionActiva) {
                                Text(
                                    text = "Suscripción Activa",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Green
                                )
                                Text("Cambios restantes: $cambiosRestantes")
                                Text("Días restantes: $diasRestantes")
                                Text("Vence el: $fechaVencimiento")
                            } else {
                                Text(
                                    text = "Sin Suscripción Activa",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Red
                                )
                                Text("No puedes crear nuevos cambios de aceite")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { navController.navigate(Screen.MisSuscripciones.route) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Ver Mis Suscripciones")
                            }
                        }
                    }
                }

                // Botones con colores y ancho consistente
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { navController.navigate(Screen.ProfileBusiness.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Perfil del Negocio")
                }
                Button(
                    onClick = { navController.navigate(Screen.ManageUsers.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("Gestión de Usuarios")
                }
                Button(
                    onClick = { navController.navigate(Screen.OilChangesList.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                ) {
                    Text("Lista de Cambios de Aceite")
                }
                Button(
                    onClick = { navController.navigate(Screen.Reports.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text("Informes")
                }
            }
        }
    }
}