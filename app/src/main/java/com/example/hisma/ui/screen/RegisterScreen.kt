package com.example.hisma.ui.screen

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
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nombreFantasia,
            onValueChange = { nombreFantasia = it },
            label = { Text("Nombre de Fantasía") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = responsable,
            onValueChange = { responsable = it },
            label = { Text("Responsable") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = cuit,
            onValueChange = { cuit = it },
            label = { Text("CUIT") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = direccion,
            onValueChange = { direccion = it },
            label = { Text("Dirección") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmar Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                                "trialUsed" to false // Añadimos este campo para controlar si ha usado prueba
                            )
                            db.collection("lubricentros").document(uid).set(lubricentroData).await()

                            // Crear suscripción de prueba
                            val subscriptionManager = SubscriptionManager(context, auth, db)
                            val trialCreated = subscriptionManager.createTrial(uid)

                            if (!trialCreated) {
                                Log.e("RegisterScreen", "No se pudo crear la suscripción de prueba")
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

        Spacer(modifier = Modifier.height(8.dp))

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enlace a "Olvidé mi contraseña"
        TextButton(onClick = {
            navController.navigate(Screen.ForgotPassword.route)
        }) {
            Text("¿Olvidaste tu contraseña?")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Enlace para regresar al Login
        TextButton(onClick = { navController.navigateUp() }) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }
}