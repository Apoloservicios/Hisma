package com.example.hisma.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.ui.navigation.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterEmployeeScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    // Campos
    var cuitNegocio by remember { mutableStateOf("") }
    var nombreFantasia by remember { mutableStateOf("") } // Mostrado al usuario para confirmación
    var negocioEncontrado by remember { mutableStateOf(false) }

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
            text = "Registro de Empleado",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Campo CUIT
        OutlinedTextField(
            value = cuitNegocio,
            onValueChange = { cuitNegocio = it },
            label = { Text("CUIT del negocio") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Botón "Buscar"
        Button(
            onClick = {
                if (cuitNegocio.isBlank()) {
                    errorMessage = "Ingresa el CUIT del negocio"
                    return@Button
                }
                errorMessage = null
                isLoading = true
                scope.launch {
                    try {
                        val lubQuery = db.collection("lubricentros")
                            .whereEqualTo("cuit", cuitNegocio)
                            .get()
                            .await()
                        if (!lubQuery.isEmpty) {
                            val docLub = lubQuery.documents[0]
                            nombreFantasia = docLub.getString("nombreFantasia") ?: ""
                            negocioEncontrado = true
                            isLoading = false
                        } else {
                            negocioEncontrado = false
                            nombreFantasia = ""
                            isLoading = false
                            errorMessage = "No se encontró un negocio con ese CUIT"
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
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Buscar")
            }
        }

        // Mostrar error
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        if (negocioEncontrado) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Negocio: $nombreFantasia",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Campos de registro de empleado
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirmar Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                        errorMessage = "Completa todos los campos de registro"
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
                            val result = auth.createUserWithEmailAndPassword(email, password).await()
                            val empleadoUid = result.user?.uid
                            if (empleadoUid != null) {
                                // Volver a buscar el negocio
                                val lubQuery = db.collection("lubricentros")
                                    .whereEqualTo("cuit", cuitNegocio)
                                    .get()
                                    .await()
                                if (!lubQuery.isEmpty) {
                                    val docLub = lubQuery.documents[0]
                                    val ownerUid = docLub.id

                                    // Crear empleado con estado=false
                                    val empData = mapOf(
                                        "uidAuth" to empleadoUid,
                                        "nombre" to "",  // O podrías pedir un campo "nombre"
                                        "email" to email,
                                        "rol" to "empleado",
                                        "estado" to false
                                    )
                                    db.collection("lubricentros")
                                        .document(ownerUid)
                                        .collection("empleados")
                                        .document()
                                        .set(empData)
                                        .await()

                                    isLoading = false
                                    // Navegar a Login
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.RegisterEmployee.route) { inclusive = true }
                                    }
                                } else {
                                    isLoading = false
                                    errorMessage = "No se encontró el negocio nuevamente"
                                }
                            } else {
                                isLoading = false
                                errorMessage = "No se pudo obtener el UID del empleado"
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
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Registrarse como Empleado")
                }
            }

            // Mostrar error si sale algo
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón para regresar al Login
        TextButton(onClick = { navController.navigateUp() }) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }
}
