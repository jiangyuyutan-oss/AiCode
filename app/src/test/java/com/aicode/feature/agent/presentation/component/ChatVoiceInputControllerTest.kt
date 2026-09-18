package com.aicode.feature.agent.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatVoiceInputControllerTest {

    @Test
    fun emptyBase_prependsNothing() {
        assertEquals("hello", mergeDraft("", "hello"))
    }

    @Test
    fun baseWithTrailingSpace_singleSeparator() {
        assertEquals("base hello", mergeDraft("base ", "hello"))
    }

    @Test
    fun recognizedBlank_returnsTrimmedBase() {
        assertEquals("base", mergeDraft("base  ", "   "))
    }

    @Test
    fun bothBlank_returnsEmpty() {
        assertEquals("", mergeDraft("   ", "  "))
    }

    @Test
    fun trimsRecognizedEdges() {
        assertEquals("base hello", mergeDraft("base", "  hello  "))
    }

    @Test
    fun consecutiveDictation_appendsRepeatedly() {
        val once = mergeDraft("", "first")
        assertEquals("first second", mergeDraft(once, "second"))
    }
}
