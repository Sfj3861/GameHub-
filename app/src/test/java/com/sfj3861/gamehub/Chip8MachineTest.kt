package com.sfj3861.gamehub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Chip8MachineTest {
    @Test
    fun executesImmediateLoadsAndAdditionWithCarry() {
        val machine = Chip8Machine(byteArrayOf(
            0x60.toByte(), 0xFA.toByte(), // V0 = 250
            0x61.toByte(), 0x0A,         // V1 = 10
            0x80.toByte(), 0x14          // V0 += V1
        ), seed = 1L)

        repeat(3) { machine.step() }

        assertEquals(4, machine.register(0))
        assertEquals(1, machine.register(0xF))
    }

    @Test
    fun drawingTogglesPixelsAndSetsCollisionFlag() {
        val machine = Chip8Machine(byteArrayOf(
            0xA2.toByte(), 0x0A,          // I = 0x20A
            0x60.toByte(), 0x00,          // x = 0
            0x61.toByte(), 0x00,          // y = 0
            0xD0.toByte(), 0x11,          // draw one row
            0x12, 0x06,                   // jump back to draw
            0xF0.toByte(), 0x00.toByte() // sprite: 11110000
        ), seed = 2L)

        repeat(4) { machine.step() }
        assertTrue(machine.pixel(0, 0))
        assertTrue(machine.pixel(3, 0))
        assertFalse(machine.pixel(4, 0))
        machine.step()
        machine.step()

        assertEquals(1, machine.register(0xF))
        assertFalse(machine.pixel(0, 0))
        assertFalse(machine.pixel(3, 0))
    }

    @Test
    fun waitsForAKeyAndReadsThePressedChip8Key() {
        val machine = Chip8Machine(byteArrayOf(0xF0.toByte(), 0x0A), seed = 3L)

        machine.step()
        assertEquals(0x200, machine.programCounter())

        machine.setKey(0xB, true)
        machine.step()
        assertEquals(0xB, machine.register(0))
        assertEquals(0x202, machine.programCounter())
    }

    @Test
    fun timersCountDownAndDisplayCanBeCleared() {
        val machine = Chip8Machine(byteArrayOf(
            0x60.toByte(), 0x02,
            0xF0.toByte(), 0x15,
            0xF0.toByte(), 0x18,
            0x00, 0xE0.toByte()
        ), seed = 4L)

        machine.step()
        machine.step()
        machine.step()
        assertEquals(2, machine.delayTimer)
        assertEquals(2, machine.soundTimer)

        machine.tickTimers()
        assertEquals(1, machine.delayTimer)
        assertEquals(1, machine.soundTimer)

        machine.step()
        assertFalse(machine.displaySnapshot().any { it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRomLargerThanChip8Memory() {
        Chip8Machine(ByteArray(Chip8Machine.MAX_PROGRAM_SIZE + 1))
    }
}
