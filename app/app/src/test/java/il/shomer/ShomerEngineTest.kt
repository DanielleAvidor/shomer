package il.shomer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
