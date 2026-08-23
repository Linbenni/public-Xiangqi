package com.sojourners.tchess.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EvalBarTest {

    @Test
    fun `红优势越大占比越高且单调`() {
        val minus = EvalBar.redFraction(-800)
        val zero = EvalBar.redFraction(0)
        val plus = EvalBar.redFraction(800)
        assertTrue(minus < 0.5f && zero == 0.5f && plus > 0.5f)
        assertTrue(EvalBar.redFraction(100) < EvalBar.redFraction(300))
    }

    @Test
    fun `占比保留边界余量`() {
        assertTrue(EvalBar.redFraction(100_000) <= 0.951f)
        assertTrue(EvalBar.redFraction(-100_000) >= 0.049f)
        assertEquals(0.98f, EvalBar.mateFraction(true))
        assertEquals(0.02f, EvalBar.mateFraction(false))
    }
}
