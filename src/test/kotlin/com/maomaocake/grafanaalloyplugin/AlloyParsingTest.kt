package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.ParsingTestCase
import com.maomaocake.grafanaalloyplugin.parser.AlloyParserDefinition

class AlloyParsingTest : ParsingTestCase("parser", "alloy", AlloyParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun skipSpaces(): Boolean = true
    override fun includeRanges(): Boolean = true

    fun testScrapeRemoteWrite() { doTest(true) }
    fun testExpressions()       { doTest(true) }
    fun testComments()          { doTest(true) }
}
