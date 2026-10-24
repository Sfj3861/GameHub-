package com.sfj3861.gamehub

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBytesTest {
    @Test
    fun formatsBytesBelowOneKibibyte() {
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun formatsKibibytes() {
        assertEquals("1.0 KiB", formatBytes(1024))
    }

    @Test
    fun formatsMebibytes() {
        assertEquals("1.0 MiB", formatBytes(1024L * 1024L))
    }
}
