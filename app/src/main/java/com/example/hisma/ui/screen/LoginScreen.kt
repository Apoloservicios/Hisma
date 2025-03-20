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
import com.example.hisma.ui.navigation.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.example.hisma.model.Subscription
import com.example.hisma.utils.SubscriptionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
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
                .size(320.dp)
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
                                            val lubricentro = ownerDoc.toObject(com.example.hisma.model.Lubricentro::class.java)
                                            val subscription = lubricentro?.subscription

                                            if (subscription == null) {
                                                // El usuario no tiene suscripción, activar el período de prueba
                                                val subscriptionManager = SubscriptionManager(context, auth, db)
                                                subscriptionManager.activateTrial { success ->
                                                    if (success) {
                                                        // Período de prueba activado correctamente
                                                        isLoading = false
                                                        navController.navigate(Screen.HomeOwner.route) {
                                                            popUpTo(Screen.Login.route) { inclusive = true }
                                                        }
                                                    } else {
                                                        isLoading = false
                                                        errorMessage = "No se pudo activar el período de prueba. Contacta con soporte."
                                                    }
                                                }
                                            } else if (subscription.active) {  // CAMBIADO: verificar solo active primero
                                                // La suscripción está marcada como activa
                                                isLoading = false
                                                navController.navigate(Screen.HomeOwner.route) {
                                                    popUpTo(Screen.Login.route) { inclusive = true }
                                                }
                                            } else {
                                                // La suscripción no está activa
                                                isLoading = false
                                                errorMessage = "Tu cuenta está desactivada. Contacta con soporte."
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
}
