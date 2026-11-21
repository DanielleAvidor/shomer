import il.shomer.ShomerEngine
import java.io.File

fun esc(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

fun main(args: Array<String>) {
    val engine = ShomerEngine(File(args[0]).readText(Charsets.UTF_8))
    val out = StringBuilder()
    for (line in File(args[1]).readLines(Charsets.UTF_8)) {
        if (line.isBlank()) continue
        // each input line is a JSON string literal; reuse the shim parser through a one-key object
        val text = org.json.JSONObject("{\"t\":$line}").getString("t")
        val r = engine.check(text)
        out.append("{\"verdict\":${esc(r.verdict.key)},\"headline\":${esc(r.headline)},\"certain\":${r.certain},\"applicable\":${r.applicable},\"unsupported\":${r.unsupportedLanguage},")
        out.append("\"links\":[${r.links.joinToString(",") { esc(it.verdict.key + "|" + it.host) }}],\"signals\":[${r.signalIds.sorted().joinToString(",") { esc(it) }}],\"orgs\":[${r.claimedOrgs.sorted().joinToString(",") { esc(it) }}]}\n")
    }
    File(args[2]).writeText(out.toString(), Charsets.UTF_8)
}
