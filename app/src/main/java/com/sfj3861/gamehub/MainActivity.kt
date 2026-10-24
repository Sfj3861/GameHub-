package com.sfj3861.gamehub

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF17152F)
private val Purple = Color(0xFF7658F5)
private val Cyan = Color(0xFF55E6F2)
private val Muted = Color(0xFFB8B8D1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Purple,
                secondary = Cyan,
                background = Ink,
                surface = Color(0xFF24213F),
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                GameHubApp(
                    displayName = { uri -> resolveName(uri) }
                )
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
    HOME("Inicio"), LIBRARY("Biblioteca"), STORE("Juegos"), DOWNLOADS("Descargas"), SETTINGS("Ajustes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubApp(displayName: (Uri) -> String) {
    var tab by remember { mutableStateOf(HubTab.HOME) }
    var notice by remember { mutableStateOf("Tu centro de juegos retro") }
    val importedGames = remember { mutableStateListOf<Pair<Uri, String>>() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { }.onSuccess {
                val name = displayName(uri)
                if (importedGames.none { it.first == uri }) importedGames.add(uri to name)
                notice = "Añadido a la biblioteca: $name"
                tab = HubTab.LIBRARY
            }
        }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.linearGradient(listOf(Purple, Cyan)),
                                    RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Gamepad, contentDescription = null, tint = Ink)
                        }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("GameHub", fontWeight = FontWeight.ExtraBold)
                            Text("RETRO • HOME BREW", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink
                )
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            when (tab) {
                HubTab.HOME -> HomeScreen(
                    importedCount = importedGames.size,
                    notice = notice,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onStore = { tab = HubTab.STORE },
                    onLibrary = { tab = HubTab.LIBRARY }
                )
                HubTab.LIBRARY -> LibraryScreen(
                    games = importedGames.map { it.second },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onSelect = { notice = "Archivo seleccionado: $it. El motor de emulación se integrará en una fase posterior." }
                )
                HubTab.STORE -> StoreScreen(onNotice = { notice = it })
                HubTab.DOWNLOADS -> DownloadsScreen()
                HubTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(
    importedCount: Int,
    notice: String,
    onImport: () -> Unit,
    onStore: () -> Unit,
    onLibrary: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(Color(0xFF4932A6), Color(0xFF202C53), Color(0xFF12616A))))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TU PRÓXIMA AVENTURA", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Todo tu mundo
de juegos, en un sitio.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Emulación retro y proyectos homebrew en una sola app.", color = Color(0xFFE2E3F7))
                        Button(onClick = onImport) { Icon(Icons.Default.Gamepad, null); Spacer(Modifier.size(8.dp)); Text("Importar un juego") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Biblioteca", importedCount.toString(), Modifier.weight(1f), onLibrary)
                StatCard("Tienda", "En preparación", Modifier.weight(1f), onStore)
            }
        }
        item { Text(notice, color = Muted, style = MaterialTheme.typography.bodySmall) }
        item {
            Text("Empieza aquí", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Importa un archivo de juego que tengas y aparecerá en tu biblioteca. La reproducción requiere integrar un motor compatible.", color = Muted)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF282442)),
        shape = RoundedCornerShape(18.dp)
    ) {
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
    if (games.isEmpty()) {
        EmptyCard("Todavía no hay juegos", "Importa un archivo compatible para preparar tu biblioteca.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games) { game ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(game) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gamepad, null, tint = Cyan, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(game, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreScreen(onNotice: (String) -> Unit) {
    Text("Juegos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Homebrew seleccionado para descubrir nuevos proyectos.", color = Muted)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("La tienda se está preparando", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("La interfaz está creada, pero aún falta conectar un catálogo real y un servicio de descargas. No se mostrarán juegos falsos ni botones de descarga que no funcionen.", color = Muted)
            Button(onClick = { onNotice("La tienda está preparada para conectar un catálogo remoto en una siguiente actualización.") }) {
                Text("Ver estado de la tienda")
            }
        }
    }
    Text("Próximamente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    EmptyCard("Catálogo homebrew", "Aquí aparecerán fichas con portada, descripción, compatibilidad, versión y descarga verificada.")
}

@Composable
private fun DownloadsScreen() {
    Text("Descargas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    EmptyCard("No hay descargas", "Las descargas se activarán cuando conectemos el catálogo y el sistema de distribución.")
}

@Composable
private fun SettingsScreen() {
    Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282442))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingRow("Interfaz", "Tema oscuro")
            SettingRow("Controles", "La configuración llegará con el motor")
            SettingRow("Emulación", "Motor pendiente de integrar")
            SettingRow("Acerca de", "GameHub 0.1.0 · versión inicial")
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
