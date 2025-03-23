package com.example.hisma.ui.screen

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
import com.example.hisma.model.Subscription
import com.example.hisma.ui.navigation.Screen
import com.example.hisma.ui.components.SubscriptionInfoCard
import com.example.hisma.utils.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeOwnerScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val subscriptionManager = remember { SubscriptionManager(db) }
    var userName by remember { mutableStateOf("Dueño") }
    var subscription by remember { mutableStateOf<Subscription?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Cargar información del lubricentro
                val doc = db.collection("lubricentros")
                    .document(currentUser.uid)
                    .get()
                    .await()

                userName = if (doc.exists() && doc.getString("nombreFantasia") != null)
                    doc.getString("nombreFantasia")!!
                else
                    currentUser.email ?: "Dueño"

                // Cargar información de suscripción
                subscription = subscriptionManager.getCurrentSubscription(currentUser.uid)
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

                // Información de suscripción
                SubscriptionInfoCard(subscription = subscription)

                Spacer(modifier = Modifier.height(24.dp))
                // Botones con colores y ancho consistente
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
                    onClick = { navController.navigate(Screen.Reports.route)  },
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