package com.maomaocake.grafanaalloyplugin

import com.maomaocake.grafanaalloyplugin.validator.AlloyValidatorOutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlloyValidatorOutputParserTest {

    @Test
    fun parsesLocatedDiagnostic() {
        val stderr = """
            Error: /tmp/cfg/file.alloy:12:4: unrecognized attribute name "scrap_interval"

            11 |   forward_to = [...]
            12 |   scrap_interval = "30s"
               |   ^^^^^^^^^^^^^^^^^^^^^^
            13 | }
        """.trimIndent()

        val out = AlloyValidatorOutputParser.parse(stderr)
        assertEquals(1, out.size)
        val d = out[0]
        assertEquals("/tmp/cfg/file.alloy", d.path)
        assertEquals(12, d.line)
        assertEquals(4, d.column)
        assertTrue(d.message.contains("scrap_interval"))
    }

    @Test
    fun skipsValidationFailedSummary() {
        val stderr = """
            Error: /tmp/cfg/file.alloy:1:1: bad
            Error: validation failed
        """.trimIndent()

        val out = AlloyValidatorOutputParser.parse(stderr)
        assertEquals(1, out.size)
        assertEquals("/tmp/cfg/file.alloy", out[0].path)
    }

    @Test
    fun fallsBackToUnlocatedWhenNothingMatches() {
        val out = AlloyValidatorOutputParser.parse("Error: something exploded\n")
        assertEquals(1, out.size)
        assertNull(out[0].path)
        assertEquals("something exploded", out[0].message)
    }

    @Test
    fun blankStderrReturnsEmpty() {
        assertTrue(AlloyValidatorOutputParser.parse("").isEmpty())
        assertTrue(AlloyValidatorOutputParser.parse("   \n  \n").isEmpty())
    }
}
