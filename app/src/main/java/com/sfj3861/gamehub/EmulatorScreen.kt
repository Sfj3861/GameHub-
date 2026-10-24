package com.sfj3861.gamehub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val ScreenBackground = Color(0xFF080D0A)
private val PixelOn = Color(0xFF9CF56A)
private val PixelOff = Color(0xFF111D13)

@Composable
internal fun EmulatorScreen(
    machine: Chip8Machine,
    romName: String,
    onExit: () -> Unit
) {
    var paused by remember(machine) { mutableStateOf(false) }
    var frameVersion by remember(machine) { mutableIntStateOf(0) }
    val pixels = remember(machine, frameVersion) { machine.displaySnapshot() }

    LaunchedEffect(machine, paused) {
        while (true) {
            if (!paused && !machine.halted) {
                repeat(8) { machine.step() }
                machine.tickTimers()
                frameVersion++
            }
            delay(16)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver a la biblioteca")
            }
            Column(Modifier.weight(1f)) {
                Text(romName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    when {
                        machine.halted -> "Ejecución detenida · ROM finalizada o instrucción inválida"
                        paused -> "Pausado"
                        else -> "CHIP-8 · ${machine.instructionsExecuted} instrucciones"
                    },
                    color = Color(0xFFB8B8D1),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = { paused = !paused }, enabled = !machine.halted) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (paused) "Reanudar" else "Pausar"
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ScreenBackground)
        ) {
            drawRect(ScreenBackground)
            val pixelWidth = size.width / Chip8Machine.WIDTH
            val pixelHeight = size.height / Chip8Machine.HEIGHT
            pixels.forEachIndexed { index, on ->
                if (on) {
                    val x = index % Chip8Machine.WIDTH
                    val y = index / Chip8Machine.WIDTH
                    drawRect(
                        color = PixelOn,
                        topLeft = Offset(x * pixelWidth, y * pixelHeight),
                        size = Size(pixelWidth + 0.35f, pixelHeight + 0.35f)
                    )
                }
            }
        }

        Text(
            "CONTROLES TÁCTILES · TECLADO CHIP-8",
            color = Color(0xFFB8B8D1),
            style = MaterialTheme.typography.labelSmall
        )
        val controlRows = listOf(
            listOf(0x1, 0x2, 0x3, 0xC),
            listOf(0x4, 0x5, 0x6, 0xD),
            listOf(0x7, 0x8, 0x9, 0xE),
            listOf(0xA, 0x0, 0xB, 0xF)
        )
        controlRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF282442))
                            .pointerInput(machine, key, paused) {
                                detectTapGestures(
                                    onPress = {
                                        if (!paused && !machine.halted) {
                                            machine.setKey(key, true)
                                            tryAwaitRelease()
                                            machine.setKey(key, false)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            key.toString(16).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        Text(
            "Admite ROMs CHIP-8 (.ch8, .c8, .chip8) de hasta 3.584 bytes. Audio y extensiones Super-CHIP todavía no están implementados.",
            color = Color(0xFFB8B8D1),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
