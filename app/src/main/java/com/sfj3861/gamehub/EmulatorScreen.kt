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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val ScreenBackground = Color(0xFF080D0A)
private val PixelOn = Color(0xFF9CF56A)

@Composable
internal fun EmulatorScreen(
    machine: Chip8Machine,
    romName: String,
    onExit: () -> Unit
) {
    var paused by remember(machine) { mutableStateOf(false) }
    val focusRequester = remember(machine) { FocusRequester() }
    var frameVersion by remember(machine) { mutableIntStateOf(0) }
    val pixels = remember(machine, frameVersion) { machine.displaySnapshot() }

    LaunchedEffect(machine) {
        focusRequester.requestFocus()
    }

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
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val chipKey = when (event.key) {
                    Key.One -> 0x1
                    Key.Two -> 0x2
                    Key.Three -> 0x3
                    Key.Four -> 0xC
                    Key.Q -> 0x4
                    Key.W -> 0x5
                    Key.E -> 0x6
                    Key.R -> 0xD
                    Key.A -> 0x7
                    Key.S -> 0x8
                    Key.D -> 0x9
                    Key.F -> 0xE
                    Key.Z -> 0xA
                    Key.X -> 0x0
                    Key.C -> 0xB
                    Key.V -> 0xF
                    else -> null
                }
                if (chipKey == null) false else {
                    machine.setKey(chipKey, event.type == KeyEventType.KeyDown)
                    true
                }
            },
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
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                machine.setKey(key, false)
                                            }
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
