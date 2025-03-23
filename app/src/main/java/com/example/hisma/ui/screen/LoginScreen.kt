package com.example.hisma.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.R
import com.example.hisma.model.Subscription
import com.example.hisma.ui.navigation.Screen
import com.example.hisma.ui.components.SubscriptionExpiredDialog
import com.example.hisma.utils.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSubscriptionExpiredDialog by remember { mutableStateOf(false) }
    var subscriptionDetails by remember { mutableStateOf<Subscription?>(null) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val subscriptionManager = remember { SubscriptionManager(db) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 24.dp)
        )

        // Campo Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = "User Icon") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Campo Contraseña
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Icon") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Botón de Iniciar Sesión
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid
                            if (uid != null) {
                                scope.launch {
                                    try {
                                        // Primero, buscar en "lubricentros" (dueño)
                                        val ownerDoc = db.collection("lubricentros")
                                            .document(uid)
                                            .get()
                                            .await()
                                        if (ownerDoc.exists()) {
                                            // Verificar suscripción
                                            val subscription = subscriptionManager.getCurrentSubscription(uid)

                                            if (subscription != null && subscription.isValid()) {
                                                // Suscripción válida, ir al Home
                                                isLoading = false
                                                navController.navigate(Screen.HomeOwner.route) {
                                                    popUpTo(Screen.Login.route) { inclusive = true }
                                                }
                                            } else {
                                                // Suscripción expirada o inválida
                                                isLoading = false
                                                subscriptionDetails = subscription
                                                showSubscriptionExpiredDialog = true
                                            }
                                        } else {
                                            // Si no es dueño, buscar en la subcolección "empleados" a nivel de grupo
                                            val empQuery = db.collectionGroup("empleados")
                                                .whereEqualTo("uidAuth", uid)
                                                .get()
                                                .await()
                                            if (!empQuery.isEmpty) {
                                                // Tomamos el primer documento
                                                val empDoc = empQuery.documents[0]
                                                val estado = empDoc.getBoolean("estado") ?: true
                                                val path = empDoc.reference.path
                                                val pathParts = path.split("/")

                                                if (pathParts.size >= 2) {
                                                    val ownerUid = pathParts[1] // Obtener el UID del dueño

                                                    // Verificar suscripción del dueño
                                                    val ownerSubscription = subscriptionManager.getCurrentSubscription(ownerUid)

                                                    if (ownerSubscription != null && ownerSubscription.isValid()) {
                                                        if (!estado) {
                                                            isLoading = false
                                                            errorMessage = "Tu cuenta está desactivada. Contacta al administrador."
                                                        } else {
                                                            isLoading = false
                                                            navController.navigate(Screen.HomeEmployee.route) {
                                                                popUpTo(Screen.Login.route) { inclusive = true }
                                                            }
                                                        }
                                                    } else {
                                                        // Suscripción del dueño expirada
                                                        isLoading = false
                                                        errorMessage = "La suscripción del negocio ha expirado. Contacta al administrador."
                                                    }
                                                } else {
                                                    isLoading = false
                                                    errorMessage = "Error al obtener información del empleado."
                                                }
                                            } else {
                                                isLoading = false
                                                errorMessage = "Usuario no registrado en la base de datos."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = e.message
                                    }
                                }
                            } else {
                                isLoading = false
                                errorMessage = "Error: UID nulo"
                            }
                        } else {
                            isLoading = false
                            errorMessage = task.exception?.message
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Iniciar Sesión")
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enlace "¿Olvidaste tu contraseña?"
        TextButton(onClick = {
            navController.navigate(Screen.ForgotPassword.route)
        }) {
            Text("¿Olvidaste tu contraseña?")
        }
        // Enlace para registrarse como dueño
        TextButton(onClick = {
            navController.navigate(Screen.Register.route)
        }) {
            Text("¿No tienes cuenta? Regístrate tu negocio")
        }
        // Enlace para registrarse como empleado
        TextButton(onClick = {
            navController.navigate(Screen.RegisterEmployee.route)
        }) {
            Text("¿Eres empleado? Regístrate")
        }
    }

    // Diálogo de suscripción expirada
    if (showSubscriptionExpiredDialog) {
        SubscriptionExpiredDialog(
            subscription = subscriptionDetails,
            onDismiss = {
                showSubscriptionExpiredDialog = false
                auth.signOut()
            }
        )
    }
}