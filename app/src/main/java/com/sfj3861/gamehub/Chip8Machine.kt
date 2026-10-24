package com.sfj3861.gamehub

import java.util.Random

/**
 * Small CHIP-8 interpreter. It executes user-provided CHIP-8 bytecode only;
 * it does not load native code or access files/network on behalf of a ROM.
 */
internal class Chip8Machine(program: ByteArray, seed: Long = System.nanoTime()) {
    companion object {
        const val WIDTH = 64
        const val HEIGHT = 32
        const val MAX_PROGRAM_SIZE = 3584
        private val FONT = intArrayOf(
            0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
            0x20, 0x60, 0x20, 0x20, 0x70, // 1
            0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
            0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
            0x90, 0x90, 0xF0, 0x10, 0x10, // 4
            0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
            0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
            0xF0, 0x10, 0x20, 0x40, 0x40, // 7
            0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
            0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
            0xF0, 0x90, 0xF0, 0x90, 0x90, // A
            0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
            0xF0, 0x80, 0x80, 0x80, 0xF0, // C
            0xE0, 0x90, 0x90, 0x90, 0xE0, // D
            0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
            0xF0, 0x80, 0xF0, 0x80, 0x80  // F
        )
    }

    private val memory = IntArray(4096)
    private val v = IntArray(16)
    private val stack = IntArray(16)
    private val keys = BooleanArray(16)
    private val display = BooleanArray(WIDTH * HEIGHT)
    private val random = Random(seed)
    private var i = 0
    private var pc = 0x200
    private var sp = 0
    var delayTimer: Int = 0
        private set
    var soundTimer: Int = 0
        private set
    var halted: Boolean = false
        private set
    var instructionsExecuted: Long = 0
        private set

    init {
        require(program.isNotEmpty()) { "La ROM está vacía." }
        require(program.size <= MAX_PROGRAM_SIZE) {
            "La ROM CHIP-8 supera el tamaño máximo de $MAX_PROGRAM_SIZE bytes."
        }
        FONT.forEachIndexed { index, value -> memory[index] = value }
        program.forEachIndexed { index, value -> memory[0x200 + index] = value.toInt() and 0xFF }
    }

    fun step() {
        if (halted) return
        if (pc !in 0 until memory.size - 1) {
            halted = true
            return
        }
        val opcode = (memory[pc] shl 8) or memory[pc + 1]
        pc += 2
        instructionsExecuted++

        val x = (opcode shr 8) and 0xF
        val y = (opcode shr 4) and 0xF
        val n = opcode and 0xF
        val kk = opcode and 0xFF
        val address = opcode and 0x0FFF

        when (opcode and 0xF000) {
            0x0000 -> when (opcode) {
                0x00E0 -> display.fill(false)
                0x00EE -> {
                    if (sp == 0) halted = true else pc = stack[--sp]
                }
                else -> Unit // 0NNN is a legacy SYS call; ignored by modern interpreters.
            }
            0x1000 -> pc = address
            0x2000 -> {
                if (sp >= stack.size) halted = true
                else {
                    stack[sp++] = pc
                    pc = address
                }
            }
            0x3000 -> if (v[x] == kk) pc += 2
            0x4000 -> if (v[x] != kk) pc += 2
            0x5000 -> if (n == 0 && v[x] == v[y]) pc += 2
            0x6000 -> v[x] = kk
            0x7000 -> v[x] = (v[x] + kk) and 0xFF
            0x8000 -> when (n) {
                0x0 -> v[x] = v[y]
                0x1 -> v[x] = v[x] or v[y]
                0x2 -> v[x] = v[x] and v[y]
                0x3 -> v[x] = v[x] xor v[y]
                0x4 -> {
                    val sum = v[x] + v[y]
                    v[0xF] = if (sum > 0xFF) 1 else 0
                    v[x] = sum and 0xFF
                }
                0x5 -> {
                    v[0xF] = if (v[x] >= v[y]) 1 else 0
                    v[x] = (v[x] - v[y]) and 0xFF
                }
                0x6 -> {
                    v[0xF] = v[x] and 1
                    v[x] = v[x] ushr 1
                }
                0x7 -> {
                    v[0xF] = if (v[y] >= v[x]) 1 else 0
                    v[x] = (v[y] - v[x]) and 0xFF
                }
                0xE -> {
                    v[0xF] = (v[x] shr 7) and 1
                    v[x] = (v[x] shl 1) and 0xFF
                }
                else -> Unit
            }
            0x9000 -> if (n == 0 && v[x] != v[y]) pc += 2
            0xA000 -> i = address
            0xB000 -> pc = (address + v[0]) and 0x0FFF
            0xC000 -> v[x] = random.nextInt(256) and kk
            0xD000 -> drawSprite(v[x], v[y], n)
            0xE000 -> when (kk) {
                0x9E -> if (keys[v[x] and 0xF]) pc += 2
                0xA1 -> if (!keys[v[x] and 0xF]) pc += 2
                else -> Unit
            }
            0xF000 -> when (kk) {
                0x07 -> v[x] = delayTimer
                0x0A -> {
                    val pressed = keys.indexOfFirst { it }
                    if (pressed < 0) pc -= 2 else v[x] = pressed
                }
                0x15 -> delayTimer = v[x]
                0x18 -> soundTimer = v[x]
                0x1E -> i = (i + v[x]) and 0xFFFF
                0x29 -> i = (v[x] and 0xF) * 5
                0x33 -> {
                    val value = v[x]
                    if (i in 0..memory.size - 3) {
                        memory[i] = value / 100
                        memory[i + 1] = (value / 10) % 10
                        memory[i + 2] = value % 10
                    }
                }
                0x55 -> for (register in 0..x) if (i + register in memory.indices) memory[i + register] = v[register]
                0x65 -> for (register in 0..x) if (i + register in memory.indices) v[register] = memory[i + register]
                else -> Unit
            }
        }
    }

    private fun drawSprite(xStart: Int, yStart: Int, rows: Int) {
        v[0xF] = 0
        if (rows == 0) return // SCHIP 16x16 is intentionally not part of the CHIP-8 baseline.
        for (row in 0 until rows) {
            val spriteAddress = i + row
            if (spriteAddress !in memory.indices) break
            val bits = memory[spriteAddress]
            for (column in 0 until 8) {
                if ((bits and (0x80 shr column)) == 0) continue
                val x = (xStart + column) % WIDTH
                val y = (yStart + row) % HEIGHT
                val pixelIndex = y * WIDTH + x
                if (display[pixelIndex]) v[0xF] = 1
                display[pixelIndex] = !display[pixelIndex]
            }
        }
    }

    fun setKey(key: Int, pressed: Boolean) {
        if (key in keys.indices) keys[key] = pressed
    }

    fun tickTimers() {
        if (delayTimer > 0) delayTimer--
        if (soundTimer > 0) soundTimer--
    }

    fun displaySnapshot(): BooleanArray = display.copyOf()
    internal fun register(index: Int): Int = v[index and 0xF]
    internal fun programCounter(): Int = pc
    internal fun pixel(x: Int, y: Int): Boolean = display[y * WIDTH + x]
}
