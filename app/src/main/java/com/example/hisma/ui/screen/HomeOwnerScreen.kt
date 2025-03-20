package com.example.hisma.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.ui.navigation.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.example.hisma.R
import com.example.hisma.utils.SubscriptionManager
import java.util.Calendar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeOwnerScreen(navController: NavController) {
    // Variables para manejo de advertencias
    var showRenewalWarning by remember { mutableStateOf(false) }
    var renewalWarningMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    var userName by remember { mutableStateOf("Dueño") }
    var isLoading by remember { mutableStateOf(true) }
    var subscriptionInfo by remember { mutableStateOf("") }
    var suscripcionActiva by remember { mutableStateOf(false) }
    var cambiosRestantes by remember { mutableStateOf(0) }
    var diasRestantes by remember { mutableStateOf(0) }
    var fechaVencimiento by remember { mutableStateOf("") }
    var isLoadingSuscripcion by remember { mutableStateOf(true) }


    // Nuevas variables para control de estado de activación
    var isLubricentroActive by remember { mutableStateOf(true) }
    var accountDisabledReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val doc = db.collection("lubricentros").document(currentUser.uid).get().await()
                userName = if (doc.exists() && doc.getString("nombreFantasia") != null)
                    doc.getString("nombreFantasia")!!
                else
                    currentUser.email ?: "Dueño"

                // Cargar información de suscripción
                val subscriptionManager = SubscriptionManager(context)
                subscriptionManager.checkActiveSubscription { result ->
                    if (result.isSuccess) {
                        val subscription = result.getOrNull()
                        if (subscription != null) {
                            suscripcionActiva = subscription.active && subscription.valid
                            cambiosRestantes = subscription.availableChanges

                            // Calcular días restantes
                            val hoy = Calendar.getInstance().time.time
                            val vencimiento = subscription.endDate.toDate().time
                            diasRestantes = ((vencimiento - hoy) / (1000 * 60 * 60 * 24)).toInt()

                            // Formatear fecha
                            fechaVencimiento = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                .format(subscription.endDate.toDate())
                        } else {
                            suscripcionActiva = false
                        }
                    } else {
                        suscripcionActiva = false
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
        } finally {
            isLoading = false
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
        } else if (!isLubricentroActive) {
            // Pantalla de cuenta desactivada o membresía vencida
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Aquí podrías cargar el logo de Hisma
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "Logo Hisma",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 24.dp)
                )

                Text(
                    text = accountDisabledReason,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "Para continuar utilizando la aplicación, por favor comuníquese con soporte:",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Botón para contactar por WhatsApp
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/542604515854?text=Hola,%20necesito%20ayuda%20con%20mi%20suscripción%20de%20HISMA")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Puedes agregar un icono de WhatsApp si lo tienes
                        // Icon(painter = painterResource(id = R.drawable.ic_whatsapp), contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contactar por WhatsApp: 2604515854")
                    }
                }

                // Botón para enviar email
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:ventas@hisma.com.ar?subject=Consulta%20sobre%20mi%20suscripción")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Icon(imageVector = Icons.Default.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Email: ventas@hisma.com.ar")
                    }
                }

                // Botón para cerrar sesión
                TextButton(
                    onClick = {
                        auth.signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.HomeOwner.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Text("Cerrar sesión")
                }
            }
        } else {
            // Contenido normal - Panel de Dueño
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

                // Agregar un Log para depuración
                Log.d("HomeOwnerScreen", "Mostrando tarjeta de información: $userName, $subscriptionInfo")

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
                        // Agregar información de suscripción
                        Text(
                            text = subscriptionInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (subscriptionInfo.contains("Sin plan activo"))
                                Color.Red else Color(0xFF4CAF50)
                        )
                    }
                }

                // Mostrar advertencia si está próximo a vencer
                if (showRenewalWarning) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)) // Color amarillo claro
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Advertencia",
                                tint = Color(0xFFF57C00) // Color naranja
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = renewalWarningMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF5F4B32) // Color marrón oscuro
                            )
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
            }
        }
    }
}
