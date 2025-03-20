package com.example.hisma.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.ui.navigation.Screen
import com.example.hisma.utils.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current  // Añadido para resolver el error

    // Campos de registro
    var nombreFantasia by remember { mutableStateOf("") }
    var responsable by remember { mutableStateOf("") }
    var cuit by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Registro de Lubricentro",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // ... (resto del código de campos de texto)

        Button(
            onClick = {
                // Validaciones básicas
                if (nombreFantasia.isBlank() || responsable.isBlank() ||
                    cuit.isBlank() || direccion.isBlank() || telefono.isBlank() ||
                    email.isBlank() || password.isBlank() || confirmPassword.isBlank()
                ) {
                    errorMessage = "Completa todos los campos"
                    return@Button
                }
                if (password != confirmPassword) {
                    errorMessage = "Las contraseñas no coinciden"
                    return@Button
                }
                if (password.length < 6) {
                    errorMessage = "La contraseña debe tener al menos 6 caracteres"
                    return@Button
                }

                isLoading = true
                errorMessage = null

                scope.launch {
                    try {
                        // Registrar en Firebase Auth
                        val result = auth.createUserWithEmailAndPassword(email, password).await()
                        val uid = result.user?.uid
                        if (uid != null) {
                            // Enviar email de verificación (opcional)
                            auth.currentUser?.sendEmailVerification()

                            // Guardar datos del lubricentro en Firestore
                            val lubricentroData = mapOf(
                                "uid" to uid,
                                "nombreFantasia" to nombreFantasia,
                                "responsable" to responsable,
                                "cuit" to cuit,
                                "direccion" to direccion,
                                "telefono" to telefono,
                                "email" to email,
                                "trialUsed" to false // Para saber si ya usó la prueba
                            )
                            db.collection("lubricentros").document(uid).set(lubricentroData).await()

                            // Crear suscripción de prueba
                            val subscriptionManager = SubscriptionManager(context, auth, db)
                            val trialResult = subscriptionManager.createTrial(uid)
                            if (trialResult.isFailure) {
                                Log.e("RegisterScreen", "Error al crear suscripción de prueba: ${trialResult.exceptionOrNull()?.message}")
                            }

                            isLoading = false
                            navController.navigate(Screen.EmailVerification.route) {
                                popUpTo(Screen.Register.route) { inclusive = true }
                            }
                        } else {
                            isLoading = false
                            errorMessage = "No se pudo obtener el UID"
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Registrarse")
            }
        }

        // ... (resto del código)
    }
}
