package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.envfile.AlloyEnvFile
import com.maomaocake.grafanaalloyplugin.envfile.AlloyEnvFileSettings

/**
 * End-to-end tests for the envfile `${…}` feature: parser, completion (inside / prefix /
 * outside placeholders), unknown-var warning, and the show-values toggle.
 */
class AlloyEnvFileTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/envfile"

    private fun configureEnvFile() {
        val vf = myFixture.copyFileToProject("sample.env", "sample.env")
        // myFixture lives on a temp:// VFS, not LocalFileSystem. Use the URL so the resolver
        // can find it via VirtualFileManager.
        AlloyEnvFileSettings.getInstance(project).envFilePath = vf.url
    }

    override fun tearDown() {
        try {
            val s = AlloyEnvFileSettings.getInstance(project)
            s.envFilePath = ""
            s.showValuesInCompletion = false
        } finally {
            super.tearDown()
        }
    }

    // --- Parser ----------------------------------------------------------------

    fun testParserPicksUpKeysAndHandlesEdgeCases() {
        configureEnvFile()
        val entries = AlloyEnvFile.getInstance(project).entries()

        // Basic `KEY=value`.
        assertEquals("localhost", entries["DB_HOST"])
        assertEquals("5432", entries["DB_PORT"])

        // `export KEY=value`.
        assertEquals("sk-abc123", entries["API_KEY"])

        // Quoted values preserve their contents verbatim (trailing comment after the closing
        // quote is ignored).
        assertEquals("very secret value", entries["API_SECRET"])

        // Unquoted inline `#` is treated as a trailing comment and stripped.
        assertEquals("/usr/local/bin", entries["PATH_WITH_HASH"])

        // Quoted values keep their `#` — they're not comments.
        assertEquals("value#with#hash", entries["QUOTED_WITH_HASH"])

        // But unquoted values don't — `#not-a-comment` on `MIMIR_URL` strips at the first `#`.
        assertEquals("https://mimir.example/push", entries["MIMIR_URL"])

        // Malformed / skipped lines should not appear as keys.
        assertFalse("INVALID LINE" in entries)
        assertFalse("" in entries)
    }

    // --- Completion ------------------------------------------------------------

    fun testCompletionInsideEmptyPlaceholderOffersAllKeys() {
        configureEnvFile()
        myFixture.configureByFile("completionInside.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected envfile keys in popup, got ${lookups.take(8)}",
            lookups.containsAll(listOf("DB_HOST", "DB_PORT", "API_KEY")),
        )
    }

    fun testCompletionPrefixFiltersByTypedText() {
        configureEnvFile()
        myFixture.configureByFile("completionPrefix.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        // With a `DB_` prefix, matcher should only surface DB_ keys.
        assertTrue(lookups.any { it == "DB_HOST" })
        assertTrue(lookups.any { it == "DB_PORT" })
        assertFalse(
            "API_KEY should not appear when typing `DB_`, got $lookups",
            lookups.any { it == "API_KEY" },
        )
    }

    fun testCompletionDoesNotFireOutsidePlaceholder() {
        configureEnvFile()
        myFixture.configureByFile("noCompletionOutside.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertFalse(
            "env keys must not appear when the caret is in a plain string (no `\${…}`), got $lookups",
            lookups.any { it == "DB_HOST" || it == "API_KEY" },
        )
    }

    // --- Show-values toggle ----------------------------------------------------

    fun testShowValuesOffProducesNoTailText() {
        configureEnvFile()
        AlloyEnvFileSettings.getInstance(project).showValuesInCompletion = false
        myFixture.configureByFile("completionInside.alloy")
        val elements = myFixture.completeBasic()?.toList() ?: emptyList()
        val dbHost = elements.first { it.lookupString == "DB_HOST" }
        val pres = com.intellij.codeInsight.lookup.LookupElementPresentation().also { dbHost.renderElement(it) }
        assertTrue(
            "tail text should be empty when showValuesInCompletion is off, got '${pres.tailText}'",
            pres.tailText.isNullOrEmpty(),
        )
    }

    fun testShowValuesOnAttachesValueAsTailText() {
        configureEnvFile()
        AlloyEnvFileSettings.getInstance(project).showValuesInCompletion = true
        myFixture.configureByFile("completionInside.alloy")
        val elements = myFixture.completeBasic()?.toList() ?: emptyList()
        val dbHost = elements.first { it.lookupString == "DB_HOST" }
        val pres = com.intellij.codeInsight.lookup.LookupElementPresentation().also { dbHost.renderElement(it) }
        assertNotNull("tail text should include the value", pres.tailText)
        assertTrue(
            "expected `localhost` in tail, got '${pres.tailText}'",
            pres.tailText!!.contains("localhost"),
        )
    }

    // --- Annotator -------------------------------------------------------------

    fun testUnknownVarIsFlaggedKnownVarIsNot() {
        configureEnvFile()
        myFixture.configureByFile("annotatorMix.alloy")
        myFixture.checkHighlighting(/* checkWarnings = */ true, /* checkInfos = */ false, /* checkWeakWarnings = */ false)
    }
}
