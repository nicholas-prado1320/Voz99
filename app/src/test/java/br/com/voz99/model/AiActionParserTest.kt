package br.com.voz99.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AiActionParserTest {
    @Test fun `rejects unknown actions`() {
        assertEquals(AiAction.NONE, AiActionParser.parse("BOOK_RIDE"))
        assertEquals(AiAction.NONE, AiActionParser.parse(null))
    }
    @Test fun `recognizes safe actions`() {
        assertEquals(AiAction.SHOW_PIN, AiActionParser.parse("show_pin"))
        assertEquals(AiAction.OPEN_99, AiActionParser.parse(" OPEN_99 "))
    }
}
