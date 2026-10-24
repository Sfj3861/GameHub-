package com.sfj3861.gamehub

import android.content.Context
import android.app.DownloadManager
import android.os.Environment
import android.content.Intent
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.Locale
import org.json.JSONObject

private val Ink = Color(0xFF17152F)
private val Purple = Color(0xFF7658F5)
private val Cyan = Color(0xFF55E6F2)
private val Muted = Color(0xFFB8B8D1)
private val ConsoleOptions = listOf(
    "Game Boy", "Game Boy Color", "Game Boy Advance", "NES", "SNES",
    "Nintendo 64", "Nintendo DS", "Sega Master System", "Mega Drive / Genesis",
    "PlayStation", "PSP", "CHIP-8", "Other"
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

private fun readChip8Rom(context: Context, uri: Uri): ByteArray {
    val limit = Chip8Machine.MAX_PROGRAM_SIZE
    val buffer = ByteArray(limit + 1)
    var total = 0
    context.contentResolver.openInputStream(uri)?.use { input ->
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            if (count == 0) continue
            total += count
        }
    } ?: throw IllegalStateException("Android no pudo abrir el archivo.")
    require(total in 1..limit) {
        if (total == 0) "El archivo está vacío." else "La ROM CHIP-8 supera el límite de $limit bytes."
    }
    return buffer.copyOf(total)
}

private enum class HubTab(val title: String) {
    HOME("Inicio"), LIBRARY("Biblioteca"), STORE("Juegos"), DOWNLOADS("Descargas"), SETTINGS("Ajustes"), PUBLISH("Publicar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubApp(displayName: (Uri) -> String) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("gamehub_preferences", Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(HubTab.HOME) }
    var notice by remember { mutableStateOf("Tu centro de juegos retro") }
    var activeMachine by remember { mutableStateOf<Chip8Machine?>(null) }
    var activeRomName by remember { mutableStateOf("") }
    var loadingRom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val importedGames = remember(context) {
        mutableStateListOf<Pair<Uri, String>>().apply {
            runCatching {
                val saved = org.json.JSONArray(preferences.getString("library_items", "[]"))
                for (i in 0 until saved.length()) {
                    val item = saved.optJSONObject(i) ?: continue
                    val uri = Uri.parse(item.optString("uri"))
                    val name = item.optString("name")
                    if (uri.scheme != null && name.isNotBlank()) add(uri to name)
                }
            }
        }
    }
    fun saveLibrary() {
        val saved = org.json.JSONArray()
        importedGames.forEach { (uri, name) -> saved.put(JSONObject().put("uri", uri.toString()).put("name", name)) }
        preferences.edit().putString("library_items", saved.toString()).apply()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(uri)
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (importedGames.none { it.first == uri }) importedGames.add(uri to name)
            saveLibrary()
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
        Box(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            if (activeMachine != null) {
                EmulatorScreen(
                    machine = activeMachine!!,
                    romName = activeRomName,
                    onExit = { activeMachine = null; tab = HubTab.LIBRARY }
                )
            } else when (tab) {
                HubTab.HOME -> HomeScreen(
                    importedCount = importedGames.size, notice = notice,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onStore = { tab = HubTab.STORE }, onLibrary = { tab = HubTab.LIBRARY },
                    onPublish = { tab = HubTab.PUBLISH }
                )
                HubTab.LIBRARY -> LibraryScreen(
                    games = importedGames.toList(), notice = notice, loading = loadingRom,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onSelect = { game ->
                        val extension = game.second.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (extension !in setOf("ch8", "c8", "chip8")) {
                            notice = "Formato aún no compatible: ${game.second}. GameHub ejecuta ahora ROMs CHIP-8 (.ch8, .c8, .chip8)."
                        } else {
                            loadingRom = true
                            notice = "Cargando ${game.second}…"
                            scope.launch {
                                try {
                                    val bytes = withContext(Dispatchers.IO) { readChip8Rom(context, game.first) }
                                    activeRomName = game.second
                                    activeMachine = Chip8Machine(bytes)
                                    notice = "Ejecutando ${game.second}"
                                } catch (e: Exception) {
                                    notice = "No se pudo abrir la ROM: ${e.message ?: "archivo no válido"}"
                                } finally {
                                    loadingRom = false
                                }
                            }
                        }
                    }
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
private fun LibraryScreen(
    games: List<Pair<Uri, String>>,
    notice: String,
    loading: Boolean,
    onImport: () -> Unit,
    onSelect: (Pair<Uri, String>) -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tu biblioteca", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("GameHub ejecuta ROMs CHIP-8 (.ch8, .c8, .chip8). Otros sistemas están en desarrollo.", color = Muted)
        Text(notice, color = Cyan, style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = onImport) { Text("＋ Importar archivo") }
        if (games.isEmpty()) {
            EmptyCard("Todavía no hay juegos", "Importa un archivo compatible para preparar tu biblioteca.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(games, key = { it.first.toString() }) { game ->
                    Card(Modifier.fillMaxWidth().clickable { onSelect(game) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gamepad, null, tint = Cyan, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.size(12.dp))
                            Text(game.second, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private data class CloudGame(
    val id: String, val title: String, val console: String, val author: String,
    val version: String, val description: String, val size: Long, val downloadUrl: String
)

@Composable
private fun StoreScreen(onNotice: (String) -> Unit, onPublish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("gamehub_preferences", Context.MODE_PRIVATE) }
    var serverUrl by remember { mutableStateOf(preferences.getString("catalog_server_url", "") ?: "") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Introduce la URL HTTPS de tu servidor para cargar el catálogo.") }
    val games = remember { mutableStateListOf<CloudGame>() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Text("Juegos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Homebrew compartido por la comunidad.", color = Muted)
        }
        item {
            OutlinedTextField(
                value = serverUrl, onValueChange = {
                    serverUrl = it.trim()
                    preferences.edit().putString("catalog_server_url", serverUrl).apply()
                },
                label = { Text("Servidor del catálogo (HTTPS)") },
                placeholder = { Text("https://juegos.tudominio.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(
                enabled = !loading && serverUrl.startsWith("https://"),
                onClick = {
                    loading = true
                    status = "Cargando catálogo…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { fetchCloudGames(serverUrl) }
                            games.clear()
                            games.addAll(result)
                            status = if (result.isEmpty()) "El catálogo está vacío. ¡Puedes publicar el primer homebrew!" else "Se han encontrado ${result.size} juegos."
                        } catch (e: Exception) {
                            status = "No se pudo cargar el catálogo: ${e.message ?: "error de conexión"}"
                        }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.size(8.dp))
                Text("Cargar catálogo")
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(status, color = Muted)
                    OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.size(8.dp)); Text("Publicar un homebrew")
                    }
                }
            }
        }
        items(games, key = { it.id }) { game ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(game.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${game.console} · v${game.version} · ${formatBytes(game.size)}", color = Cyan, style = MaterialTheme.typography.bodySmall)
                    if (game.author.isNotBlank()) Text("Por ${game.author}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (game.description.isNotBlank()) Text(game.description, color = Muted)
                    Button(
                        onClick = {
                            runCatching {
                                val fullUrl = if (game.downloadUrl.startsWith("https://")) game.downloadUrl else serverUrl.trimEnd('/') + "/" + game.downloadUrl.trimStart('/')
                                val downloadUri = android.net.Uri.parse(fullUrl)
                                val baseUri = android.net.Uri.parse(serverUrl)
                                require(downloadUri.scheme == "https" && downloadUri.host == baseUri.host && downloadUri.port == baseUri.port && downloadUri.userInfo == null) {
                                    "La descarga debe proceder del mismo servidor HTTPS del catálogo."
                                }
                                val request = DownloadManager.Request(downloadUri)
                                    .setTitle(game.title)
                                    .setDescription("Descargando desde GameHub")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GameHub/${game.title.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                manager.enqueue(request)
                                onNotice("Descarga iniciada: ${game.title}")
                            }.onFailure { status = "No se pudo iniciar la descarga: ${it.message}" }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.size(8.dp)); Text("Descargar")
                    }
                }
            }
        }
    }
}

private fun fetchCloudGames(baseUrl: String): List<CloudGame> {
    val connection = (URL(baseUrl.trimEnd('/') + "/api/games").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(160)}")
        val array = JSONObject(body).optJSONArray("games") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            CloudGame(
                id = item.optString("id"), title = item.optString("title", "Juego sin nombre"),
                console = item.optString("console", "Other"), author = item.optString("author"),
                version = item.optString("version", "1.0"), description = item.optString("description"),
                size = item.optLong("size", 0L), downloadUrl = item.optString("downloadUrl")
            )
        }
    } finally { connection.disconnect() }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0))} MiB"
    bytes >= 1024L -> "${String.format(Locale.ROOT, "%.1f", bytes / 1024.0)} KiB"
    else -> "$bytes B"
}

@Composable
private fun PublishScreen() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("gamehub_preferences", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(preferences.getString("publish_server_url", "") ?: "") }
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
                value = serverUrl, onValueChange = {
                    serverUrl = it.trim()
                    preferences.edit().putString("publish_server_url", serverUrl).apply()
                },
                label = { Text("URL del servidor HTTPS *") },
                placeholder = { Text("https://juegos.tudominio.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("Token de publicación *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation()
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
            val id = runCatching { JSONObject(body).optJSONObject("game")?.optString("id") }.getOrNull()
            "Publicado correctamente en la nube. ID: ${id?.takeIf { it.isNotBlank() } ?: "disponible en el catálogo"}. Comprueba el catálogo público del servidor."
        } else {
            val message = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            "Error HTTP $code: ${message ?: body.take(240)}"
        }
    } finally {
        connection.disconnect()
    }
}

private data class DownloadStatusItem(val id: Long, val title: String, val status: String, val downloaded: Long, val total: Long)

@Composable
private fun DownloadsScreen() {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    val downloads = remember { mutableStateListOf<DownloadStatusItem>() }
    LaunchedEffect(manager) {
        while (true) {
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or
                    DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
            )
            manager.query(query)?.use { cursor ->
                val fresh = mutableListOf<DownloadStatusItem>()
                val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val titleColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val downloadedColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (cursor.moveToNext()) {
                    val statusText = when (cursor.getInt(statusColumn)) {
                        DownloadManager.STATUS_PENDING -> "En cola"
                        DownloadManager.STATUS_RUNNING -> "Descargando"
                        DownloadManager.STATUS_PAUSED -> "Pausada"
                        DownloadManager.STATUS_SUCCESSFUL -> "Completada"
                        DownloadManager.STATUS_FAILED -> "Error de descarga"
                        else -> "Estado desconocido"
                    }
                    fresh.add(DownloadStatusItem(cursor.getLong(idColumn), cursor.getString(titleColumn) ?: "Archivo", statusText, cursor.getLong(downloadedColumn), cursor.getLong(totalColumn)))
                }
                downloads.clear()
                downloads.addAll(fresh)
            }
            delay(1500)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Descargas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (downloads.isEmpty()) {
            EmptyCard("Sin descargas registradas", "Las descargas iniciadas desde la tienda aparecerán aquí y en la carpeta Descargas de Android.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloads, key = { it.id }) { download ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(download.title, fontWeight = FontWeight.SemiBold)
                            Text(download.status, color = Cyan, style = MaterialTheme.typography.bodySmall)
                            if (download.total > 0L) {
                                LinearProgressIndicator(progress = (download.downloaded.toFloat() / download.total.toFloat()).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                                Text("${formatBytes(download.downloaded)} de ${formatBytes(download.total)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow("Interfaz", "Tema oscuro")
                SettingRow("Controles", "Táctiles y teclado físico en CHIP-8")
                SettingRow("Emulación", "CHIP-8 nativo · otros sistemas pendientes")
                SettingRow("Nube", "Servidor autohospedado de GameHub")
                SettingRow("Acerca de", "GameHub 0.4.0 · CHIP-8 + homebrew")
            }
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
