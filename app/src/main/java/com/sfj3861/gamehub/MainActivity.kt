package com.sfj3861.gamehub

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Ink = Color(0xFF17152F)
private val Purple = Color(0xFF7658F5)
private val Cyan = Color(0xFF55E6F2)
private val Muted = Color(0xFFB8B8D1)
private val ConsoleOptions = listOf(
    "Game Boy", "Game Boy Color", "Game Boy Advance", "NES", "SNES",
    "Nintendo 64", "Nintendo DS", "Sega Master System", "Mega Drive / Genesis",
    "PlayStation", "PSP", "Other"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Purple, secondary = Cyan, background = Ink,
                surface = Color(0xFF24213F), onBackground = Color.White, onSurface = Color.White
            )) {
                GameHubApp(displayName = { uri -> resolveName(uri) })
            }
        }
    }

    private fun resolveName(uri: Uri): String {
        var result = uri.lastPathSegment ?: "Juego importado"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) result = cursor.getString(index) ?: result
            }
        }
        return result
    }
}

private enum class HubTab(val title: String) {
    HOME("Inicio"), LIBRARY("Biblioteca"), STORE("Juegos"), DOWNLOADS("Descargas"), SETTINGS("Ajustes"), PUBLISH("Publicar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubApp(displayName: (Uri) -> String) {
    var tab by remember { mutableStateOf(HubTab.HOME) }
    var notice by remember { mutableStateOf("Tu centro de juegos retro") }
    val importedGames = remember { mutableStateListOf<Pair<Uri, String>>() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(uri)
            if (importedGames.none { it.first == uri }) importedGames.add(uri to name)
            notice = "Añadido a la biblioteca: $name"
            tab = HubTab.LIBRARY
        }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).background(
                                Brush.linearGradient(listOf(Purple, Cyan)), RoundedCornerShape(11.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Gamepad, contentDescription = null, tint = Ink) }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("GameHub", fontWeight = FontWeight.ExtraBold)
                            Text("RETRO • HOMEBREW", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF211E3B)) {
                val tabs = listOf(
                    HubTab.HOME to Icons.Default.Home,
                    HubTab.LIBRARY to Icons.Default.LibraryBooks,
                    HubTab.STORE to Icons.Default.Storefront,
                    HubTab.DOWNLOADS to Icons.Default.Download,
                    HubTab.SETTINGS to Icons.Default.Settings
                )
                tabs.forEach { (item, icon) ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, contentDescription = item.title) },
                        label = { Text(item.title, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            when (tab) {
                HubTab.HOME -> HomeScreen(
                    importedCount = importedGames.size, notice = notice,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onStore = { tab = HubTab.STORE }, onLibrary = { tab = HubTab.LIBRARY },
                    onPublish = { tab = HubTab.PUBLISH }
                )
                HubTab.LIBRARY -> LibraryScreen(
                    games = importedGames.map { it.second },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onSelect = { notice = "Archivo seleccionado: $it. El motor de emulación aún debe integrarse." }
                )
                HubTab.STORE -> StoreScreen(
                    onNotice = { notice = it }, onPublish = { tab = HubTab.PUBLISH }
                )
                HubTab.DOWNLOADS -> DownloadsScreen()
                HubTab.SETTINGS -> SettingsScreen()
                HubTab.PUBLISH -> PublishScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(
    importedCount: Int, notice: String, onImport: () -> Unit,
    onStore: () -> Unit, onLibrary: () -> Unit, onPublish: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.background(Brush.linearGradient(
                    listOf(Color(0xFF4932A6), Color(0xFF202C53), Color(0xFF12616A))
                )).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TU PRÓXIMA AVENTURA", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Todo tu mundo de juegos, en un sitio.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Emulación retro y proyectos homebrew en una sola app.", color = Color(0xFFE2E3F7))
                        Button(onClick = onImport) { Icon(Icons.Default.Gamepad, null); Spacer(Modifier.size(8.dp)); Text("Importar un juego") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Biblioteca", importedCount.toString(), Modifier.weight(1f), onLibrary)
                StatCard("Juegos", "Abrir tienda", Modifier.weight(1f), onStore)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("¿Has creado un homebrew?", fontWeight = FontWeight.Bold)
                        Text("Publícalo para que otros puedan descargarlo.", color = Muted)
                    }
                    Button(onClick = onPublish) { Text("Publicar") }
                }
            }
        }
        item { Text(notice, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF282442)),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Muted)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryScreen(games: List<String>, onImport: () -> Unit, onSelect: (String) -> Unit) {
    Text("Tu biblioteca", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Los archivos que importes aparecerán aquí.", color = Muted)
    Button(onClick = onImport) { Text("＋ Importar archivo") }
    if (games.isEmpty()) EmptyCard("Todavía no hay juegos", "Importa un archivo compatible para preparar tu biblioteca.")
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(games) { game ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(game) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Gamepad, null, tint = Cyan, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(game, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StoreScreen(onNotice: (String) -> Unit, onPublish: () -> Unit) {
    Text("Juegos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Homebrew seleccionado para descubrir nuevos proyectos.", color = Muted)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Catálogo en preparación", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("La API en la nube ya tiene una estructura inicial. Cuando se despliegue y configures su dirección, aquí podremos cargar las publicaciones reales.", color = Muted)
            Button(onClick = onPublish) { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.size(8.dp)); Text("Publicar un homebrew") }
            TextButton(onClick = { onNotice("La tienda necesita que se despliegue el servidor de GameHub y se configure su URL.") }) {
                Text("Estado de conexión")
            }
        }
    }
    EmptyCard("Comparte tus creaciones", "Publica únicamente juegos y ROMs que tengas derecho a distribuir.")
}

@Composable
private fun PublishScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var consoleIndex by remember { mutableIntStateOf(2) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var rightsConfirmed by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("Elige una ROM homebrew, completa los datos y publícala en tu servidor.") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryDisplayName(context, uri)
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Text("Publicar un juego", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Sube el archivo a tu servidor en la nube y crea una ficha descargable para la tienda Juegos.", color = Muted)
        }
        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nombre del juego *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Consola *", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { consoleIndex = (consoleIndex - 1 + ConsoleOptions.size) % ConsoleOptions.size }) { Text("‹") }
                    Text(ConsoleOptions[consoleIndex], modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    OutlinedButton(onClick = { consoleIndex = (consoleIndex + 1) % ConsoleOptions.size }) { Text("›") }
                }
                Text("Usa las flechas para elegir la consola.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Autor (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item {
            OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text("Versión") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        }
        item {
            OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AttachFile, null); Spacer(Modifier.size(8.dp))
                Text(if (selectedFileName.isBlank()) "Seleccionar archivo ROM *" else selectedFileName)
            }
            Text("Formatos admitidos por el servidor; tamaño máximo predeterminado: 256 MiB.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedTextField(
                value = serverUrl, onValueChange = { serverUrl = it.trim() },
                label = { Text("URL del servidor HTTPS *") },
                placeholder = { Text("https://juegos.tudominio.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("Token de publicación *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Text("No incluyas el token en una APK pública. Para publicar, úsalo solo en tu dispositivo de administración.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rightsConfirmed, onCheckedChange = { rightsConfirmed = it })
                Text("Confirmo que tengo permiso para distribuir este juego y sus archivos.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Button(
                enabled = !uploading && title.isNotBlank() && selectedUri != null && serverUrl.startsWith("https://") && token.isNotBlank() && rightsConfirmed,
                onClick = {
                    val uri = selectedUri ?: return@Button
                    uploading = true
                    result = "Subiendo archivo…"
                    scope.launch {
                        result = try {
                            withContext(Dispatchers.IO) {
                                uploadRom(context, serverUrl, token, uri, title, ConsoleOptions[consoleIndex], author, version, description)
                            }
                        } catch (e: Exception) {
                            "No se pudo publicar: " + (e.message ?: "error de conexión")
                        }
                        uploading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.size(8.dp))
                Text(if (uploading) "Publicando…" else "Subir y publicar")
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
                Text(result, modifier = Modifier.padding(16.dp), color = if (result.startsWith("Publicado")) Cyan else Muted)
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "ROM seleccionada"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) name = cursor.getString(i) ?: name
        }
    }
    return name
}

private fun uploadRom(
    context: Context, baseUrl: String, token: String, uri: Uri,
    title: String, console: String, author: String, version: String, description: String
): String {
    val boundary = "----GameHub" + UUID.randomUUID().toString().replace("-", "")
    val url = URL(baseUrl.trimEnd('/') + "/api/publish")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 120_000
        doOutput = true
        useCaches = false
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        setRequestProperty("Accept", "application/json")
        setChunkedStreamingMode(64 * 1024)
    }
    fun field(out: java.io.OutputStream, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray())
    }
    try {
        connection.outputStream.buffered().use { out ->
            field(out, "title", title)
            field(out, "console", console)
            field(out, "author", author)
            field(out, "version", version.ifBlank { "1.0" })
            field(out, "description", description)
            field(out, "rightsConfirmed", "true")
            val filename = queryDisplayName(context, uri).replace("\"", "").replace("\r", "").replace("\n", "")
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"rom\"; filename=\"$filename\"\r\n".toByteArray())
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            context.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(out, 64 * 1024) }
                ?: throw IllegalStateException("No se pudo leer el archivo seleccionado.")
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code in 200..299) {
            val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            "Publicado correctamente en la nube. ID: ${id ?: "disponible en el catálogo"}. Comprueba el catálogo público del servidor."
        } else {
            val message = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            "Error HTTP $code: ${message ?: body.take(240)}"
        }
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun DownloadsScreen() {
    Text("Descargas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    EmptyCard("No hay descargas", "La tienda descargable se conectará al catálogo publicado en tu servidor.")
}

@Composable
private fun SettingsScreen() {
    Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingRow("Interfaz", "Tema oscuro")
            SettingRow("Controles", "Configuración al integrar el motor")
            SettingRow("Emulación", "Motor pendiente de integrar")
            SettingRow("Nube", "Servidor autohospedado de GameHub")
            SettingRow("Acerca de", "GameHub 0.2.0 · publicación homebrew")
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(value, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Gamepad, contentDescription = null, tint = Cyan, modifier = Modifier.size(34.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, color = Muted)
        }
    }
}
