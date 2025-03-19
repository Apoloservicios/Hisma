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

    // Nuevas variables para control de estado de activación
    var isLubricentroActive by remember { mutableStateOf(true) }
    var accountDisabledReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val doc = db.collection("lubricentros").document(currentUser.uid).get().await()

                // Verificar si el lubricentro está activo en la base de datos
                isLubricentroActive = doc.getBoolean("activo") ?: true

                if (!isLubricentroActive) {
                    accountDisabledReason = "Su cuenta ha sido desactivada por el administrador."
                    isLoading = false
                    return@LaunchedEffect
                }

                userName = if (doc.exists() && doc.getString("nombreFantasia") != null)
                    doc.getString("nombreFantasia")!!
                else
                    currentUser.email ?: "Dueño"

                // Obtener información de suscripción
                val subscriptionMap = doc.get("subscription") as? Map<*, *>

                if (subscriptionMap != null) {
                    val isActive = subscriptionMap["active"] as? Boolean ?: false

                    if (!isActive) {
                        isLubricentroActive = false
                        accountDisabledReason = "Su suscripción ha expirado o está inactiva."
                        isLoading = false
                        return@LaunchedEffect
                    }

                    // Verificar estado de la fecha de fin
                    val endDateTimestamp = subscriptionMap["endDate"] as? com.google.firebase.Timestamp
                    if (endDateTimestamp != null) {
                        val endDate = endDateTimestamp.toDate().time
                        val currentDate = System.currentTimeMillis()

                        // Calcular días restantes
                        val millisUntilEnd = endDate - currentDate
                        val daysUntilEnd = (millisUntilEnd / (1000 * 60 * 60 * 24)).toInt()

                        // Si han pasado 7 días desde la fecha de fin, desactivar cuenta
                        if (daysUntilEnd < -7) {
                            isLubricentroActive = false
                            accountDisabledReason = "Su suscripción ha vencido hace más de 7 días."

                            // Actualizar estado en la base de datos
                            db.collection("lubricentros")
                                .document(currentUser.uid)
                                .update(
                                    "activo", false,
                                    "subscription.active", false
                                )

                            isLoading = false
                            return@LaunchedEffect
                        }

                        // Si ya venció pero no han pasado 7 días, mostrar pantalla de contacto
                        if (daysUntilEnd < 0) {
                            isLubricentroActive = false
                            accountDisabledReason = "Su suscripción ha vencido. Tiene ${-daysUntilEnd} días para renovar antes de que su cuenta se desactive completamente."
                            isLoading = false
                            return@LaunchedEffect
                        }

                        // Comprobar si quedan 7 días o menos
                        val showWarning = daysUntilEnd <= 7

                        // Verificar número de cambios disponibles
                        val availableChanges = (subscriptionMap["availableChanges"] as? Number)?.toInt() ?: 0
                        val plan = (subscriptionMap["plan"] as? String ?: "").capitalize()
                        val isTrial = subscriptionMap["trialActivated"] as? Boolean ?: false

                        if (availableChanges <= 0) {
                            // Si no hay cambios disponibles, mostrar pantalla de contacto
                            isLubricentroActive = false
                            accountDisabledReason = "Ha agotado los cambios disponibles en su plan. Por favor contacte con nosotros para ampliar su plan."
                            isLoading = false
                            return@LaunchedEffect
                        } else {
                            // Formatear mensaje según estado
                            subscriptionInfo = if (isTrial) {
                                "Plan de Prueba: $daysUntilEnd días restantes, $availableChanges cambios disponibles"
                            } else {
                                "Plan $plan: $daysUntilEnd días restantes, $availableChanges cambios disponibles"
                            }

                            // Agregar advertencia si queda poco tiempo
                            if (showWarning) {
                                showRenewalWarning = true
                                renewalWarningMessage = "¡ATENCIÓN! Su plan vencerá en $daysUntilEnd día${if (daysUntilEnd == 1) "" else "s"}. Contacte con nosotros para renovar."
                            }
                        }
                    }
                } else {
                    // No tiene suscripción configurada
                    isLubricentroActive = false
                    accountDisabledReason = "No tiene un plan de suscripción activo."
                    isLoading = false
                    return@LaunchedEffect
                }
            } else {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.HomeOwner.route) { inclusive = true }
                }
            }
        } catch (e: Exception) {
            // Manejo de error
            Log.e("HomeOwnerScreen", "Error cargando datos: ${e.message}")
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
            }
        }
    }
}
