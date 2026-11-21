package il.shomer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the same shared corpus as the Python tests, so the two engines cannot silently drift apart. */
class ShomerEngineTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).readText()
    private val engine = ShomerEngine(resource("directory.json"))

    @Test
    fun sharedCorpus() {
        for (line in resource("corpus.jsonl").lines().filter { it.isNotBlank() }) {
            val c = JSONObject(line)
            val r = engine.check(c.getString("text"))
            if (c.getString("expected") == "not_applicable") assertFalse(c.getString("id"), r.applicable)
            else assertEquals(c.getString("id"), c.getString("expected"), r.verdict.key)
        }
    }

    @Test
    fun officialDomainAsPrefixOfForeignDomainIsNotOfficial() {
        assertEquals(Verdict.DONT_CLICK, engine.check("דואר ישראל https://israelpost.co.il.evil.com/x").verdict)
    }

    @Test
    fun atSignTrickUsesRealHost() {
        assertEquals("evil.com", ShomerEngine.parseHost("https://israelpost.co.il@evil.com/x").first)
    }

    @Test
    fun unsupportedLanguageIsNotReportedAsClean() {
        val r = engine.check("Ваш банковский счёт будет заблокирован. Срочно позвоните и сообщите пароль")
        assertFalse(r.applicable)
        assertTrue(r.unsupportedLanguage)
    }

    @Test
    fun linkCutShortByAPreviewIsNotGuessedAt() {
        val r = engine.check("תבדקו: https://israelpost.co\u2026")
        assertEquals(Verdict.CANT_VERIFY, r.links[0].verdict)
        assertFalse(r.certain)
    }

    @Test
    fun agentStaysQuietOnOrdinaryChatMessages() {
        for (t in listOf("הלכתי לדואר ישראל היום", "ראית את זה? https://www.ynet.co.il/news/article/abc בנק לאומי מעלה ריבית", "מה קורה? https://youtu.be/abc"))
            assertFalse(t, AgentCheck.shouldAlert(engine.check(t), strict = true))
    }

    @Test
    fun agentSpeaksOnAPushyImpersonationInAChat() {
        val r = engine.check("דואר ישראל: עדכנו פרטי אשראי תוך 24 שעות https://israelpost-fee.top/pay")
        assertTrue(AgentCheck.shouldAlert(r, strict = true))
    }
}
