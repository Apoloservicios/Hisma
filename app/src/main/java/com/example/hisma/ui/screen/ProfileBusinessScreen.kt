package com.example.hisma.ui.screen

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.hisma.ui.navigation.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBusinessScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    // Campos del negocio
    var uid by remember { mutableStateOf("") }
    var nombreFantasia by remember { mutableStateOf("") }
    var responsable by remember { mutableStateOf("") }
    var cuit by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Imagen seleccionada (para subir a Cloudinary)
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher para seleccionar imagen desde la galería
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("ProfileBusiness", "Imagen seleccionada: $uri")
        } else {
            Log.d("ProfileBusiness", "No se seleccionó imagen")
        }
        selectedImageUri = uri
    }

    // Cargar datos desde Firestore
    LaunchedEffect(Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            uid = currentUser.uid
            try {
                val doc = db.collection("lubricentros").document(uid).get().await()
                if (doc.exists()) {
                    nombreFantasia = doc.getString("nombreFantasia") ?: ""
                    responsable = doc.getString("responsable") ?: ""
                    cuit = doc.getString("cuit") ?: ""
                    direccion = doc.getString("direccion") ?: ""
                    telefono = doc.getString("telefono") ?: ""
                    logoUrl = doc.getString("logoUrl") ?: ""
                    Log.d("ProfileBusiness", "Datos cargados: nombreFantasia=$nombreFantasia, logoUrl=$logoUrl")
                }
            } catch (e: Exception) {
                Log.e("ProfileBusiness", "Error al cargar datos", e)
            } finally {
                isLoading = false
            }
        } else {
            navController.navigateUp()
        }
    }

    // Función para subir imagen a Cloudinary (ejecuta en Dispatchers.IO)
    fun uploadImageToCloudinary(fileUri: Uri, callback: (String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d("ProfileBusiness", "Iniciando subida de imagen a Cloudinary...")
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    Log.e("ProfileBusiness", "InputStream es nulo")
                    callback(null)
                    return@launch
                }
                val tempFile = File(context.cacheDir, "temp_logo.jpg")
                val outputStream = FileOutputStream(tempFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()

                val cloudName = "dcf4bewcl"
                val uploadPreset = "hismafoto"

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        tempFile.name,
                        tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                    )
                    .addFormDataPart("upload_preset", uploadPreset)
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("ProfileBusiness", "Respuesta de Cloudinary: $responseBody")
                        val jsonObject = JSONObject(responseBody ?: "")
                        val secureUrl = jsonObject.getString("secure_url")
                        Log.d("ProfileBusiness", "URL obtenida: $secureUrl")
                        callback(secureUrl)
                    } else {
                        Log.e("ProfileBusiness", "Error en Cloudinary: ${response.code}")
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileBusiness", "Excepción en la subida", e)
                callback(null)
            }
        }
    }

    // Actualizar Firestore usando set con merge
    suspend fun updateFirestore() {
        val data = mapOf(
            "uid" to uid,
            "nombreFantasia" to nombreFantasia,
            "responsable" to responsable,
            "cuit" to cuit,
            "direccion" to direccion,
            "telefono" to telefono,
            "logoUrl" to logoUrl
        )
        try {
            db.collection("lubricentros").document(uid).set(data, SetOptions.merge()).await()
            Log.d("ProfileBusiness", "Datos actualizados en Firestore: $data")
        } catch (e: Exception) {
            Log.e("ProfileBusiness", "Error actualizando Firestore", e)
            throw e
        }
    }

    // Guardar perfil: sube imagen (si hay) y actualiza Firestore
    fun saveProfile() {
        isSaving = true
        if (selectedImageUri != null) {
            uploadImageToCloudinary(selectedImageUri!!) { url ->
                scope.launch {
                    if (url != null) {
                        logoUrl = url
                        Log.d("ProfileBusiness", "Logo actualizado: $logoUrl")
                    } else {
                        Log.e("ProfileBusiness", "Error: URL del logo es nula")
                    }
                    try {
                        updateFirestore()
                    } catch (e: Exception) {
                        // Manejar el error, por ejemplo mostrar un mensaje
                    }
                    isEditing = false
                    isSaving = false
                }
            }
        } else {
            scope.launch {
                try {
                    updateFirestore()
                } catch (e: Exception) {
                    // Manejo del error
                }
                isEditing = false
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil del Negocio") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Text("<")
                    }
                },
                actions = {

                    if (!isEditing) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo del negocio
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(enabled = isEditing) {
                            if (isEditing) {
                                imagePickerLauncher.launch("image/*")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(context)
                                    .data(selectedImageUri)
                                    .build()
                            ),
                            contentDescription = "Logo seleccionado",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (logoUrl.isNotEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(context)
                                    .data(logoUrl)
                                    .build()
                            ),
                            contentDescription = "Logo del negocio",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Logo", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isEditing) {
                    OutlinedTextField(
                        value = nombreFantasia,
                        onValueChange = { nombreFantasia = it },
                        label = { Text("Nombre Fantasía") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = responsable,
                        onValueChange = { responsable = it },
                        label = { Text("Responsable") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = cuit,
                        onValueChange = { cuit = it },
                        label = { Text("CUIT") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = direccion,
                        onValueChange = { direccion = it },
                        label = { Text("Dirección") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        label = { Text("Teléfono") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { saveProfile() },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Guardar")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                selectedImageUri = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving
                        ) {
                            Text("Cancelar")
                        }
                    }
                } else {
                    ProfileInfoItem("Nombre Fantasía", nombreFantasia)
                    ProfileInfoItem("Responsable", responsable)
                    ProfileInfoItem("CUIT", cuit)
                    ProfileInfoItem("Dirección", direccion)
                    ProfileInfoItem("Teléfono", telefono)
                }
            }
        }
    }
}

@Composable
fun ProfileInfoItem(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
        Divider(modifier = Modifier.padding(top = 4.dp))
    }
}
