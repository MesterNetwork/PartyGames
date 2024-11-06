package info.mester.network.partygames

import jdk.jfr.Description
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestPartyGames {
    @Test
    @Description("Test that addition works")
    fun testAddition() {
        val lol = 3
        val llol2 = lol + 2
        assertEquals(5, llol2)
    }
//    companion object {
//        private var server: ServerMock? = null
//        private var plugin: Tournament? = null
//
//        @JvmStatic
//        @BeforeAll
//        fun setUp() {
//            server = MockBukkit.mock()
//            plugin = MockBukkit.load(Tournament::class.java)
//        }
//
//        @JvmStatic
//        @AfterAll
//        fun tearDown() {
//            MockBukkit.unmock()
//        }
//    }
}
