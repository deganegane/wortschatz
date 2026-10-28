package io.github.deganegane.wortschatz

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Reads the bundled frequency list from assets/words.json. */
object WordRepo {
    fun load(ctx: Context): List<Word> {
        val text = ctx.assets.open("words.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(text)
        val out = ArrayList<Word>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += Word(
                rank = o.getInt("r"),
                de = o.getString("de"),
                article = o.optStringOrNull("a"),
                plural = o.optStringOrNull("pl"),
                pos = o.optString("pos", ""),
                forms = o.optStringOrNull("f"),
                hr = o.getString("hr"),
                example = o.optString("ex", ""),
                exampleHr = o.optString("exHr", ""),
                cloze = o.optJSONArray("cl")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                cat = o.optStringOrNull("cat"),
                gap = o.optJSONObject("g")?.let { g ->
                    val opts = g.optJSONArray("o")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                    if (opts.size < 2) null else Gap(g.getString("b"), opts, g.optString("h", ""), g.optString("e", ""))
                }
            )
        }
        return out.sortedBy { it.rank }
    }

    fun loadStories(ctx: Context): List<Story> {
        val text = try {
            ctx.assets.open("stories.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        }
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Story(
                id = o.getString("id"),
                level = o.optInt("level", 1),
                title = o.getString("title"),
                de = o.getString("de"),
                hr = o.optString("hr", ""),
                newWords = o.optJSONArray("new")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            )
        }
    }

    /** Lower-case inflected form → rank, for tapping words in stories (assets/forms.json). */
    fun loadForms(ctx: Context): Map<String, Int> {
        val text = try {
            ctx.assets.open("forms.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return emptyMap()
        }
        val o = JSONObject(text)
        val out = HashMap<String, Int>(o.length() * 2)
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.getInt(k)
        }
        return out
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifBlank { null }
}

/**
 * Everything the learner produces, in SharedPreferences: settings, the per-word cards
 * (only words that left "untested") and per-day activity. Small enough to rewrite on
 * every change.
 */
class Store(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("wortschatz", Context.MODE_PRIVATE)

    // ---- Settings ---------------------------------------------------------

    fun loadSettings(): Settings = Settings(
        newPerDay = prefs.getInt("newPerDay", 10),
        practice = enumOr(prefs.getString("practice", null), Practice.MIXED),
        speakAnswers = prefs.getBoolean("speakAnswers", true),
        retention = Math.round(prefs.getFloat("retention", 0.9f) * 100) / 100.0,
        autoSpeak = prefs.getBoolean("autoSpeak", true),
        slowSpeech = prefs.getBoolean("slowSpeech", false),
        reminderOn = prefs.getBoolean("reminderOn", false),
        reminderHour = prefs.getInt("reminderHour", 19),
        reminderMinute = prefs.getInt("reminderMinute", 0),
        themeMode = enumOr(prefs.getString("themeMode", null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamicColor", false),
        level = prefs.getInt("level", 1).coerceAtLeast(1),
        phrasesOn = prefs.getBoolean("phrasesOn", true)
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putInt("newPerDay", s.newPerDay)
            .putString("practice", s.practice.name)
            .putBoolean("speakAnswers", s.speakAnswers)
            .putFloat("retention", s.retention.toFloat())
            .putBoolean("autoSpeak", s.autoSpeak)
            .putBoolean("slowSpeech", s.slowSpeech)
            .putBoolean("reminderOn", s.reminderOn)
            .putInt("reminderHour", s.reminderHour)
            .putInt("reminderMinute", s.reminderMinute)
            .putString("themeMode", s.themeMode.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putInt("level", s.level)
            .putBoolean("phrasesOn", s.phrasesOn)
            .apply()
    }

    // ---- Cards ------------------------------------------------------------

    fun loadCards(): Map<Int, Card> {
        val raw = prefs.getString("cards", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<Int, Card>(o.length())
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val c = o.getJSONObject(k)
                out[k.toInt()] = Card(
                    status = Status.of(c.optInt("s", 0)),
                    interval = c.optDouble("i", 0.0),
                    stability = c.optDouble("S", 0.0),
                    difficulty = c.optDouble("D", 0.0),
                    due = c.optLong("d", 0),
                    reps = c.optInt("n", 0),
                    lapses = c.optInt("l", 0),
                    lastReview = c.optLong("lr", 0)
                ).let(Srs::migrate)
            }
            out
        } catch (e: JSONException) {
            emptyMap()
        } catch (e: NumberFormatException) {
            emptyMap()
        }
    }

    fun saveCards(cards: Map<Int, Card>) {
        val o = JSONObject()
        for ((rank, c) in cards) {
            if (c.status == Status.UNTESTED) continue
            val j = JSONObject().put("s", c.status.code)
            if (c.status == Status.LEARNING) {
                j.put("i", c.interval).put("S", c.stability).put("D", c.difficulty).put("d", c.due)
                    .put("n", c.reps).put("l", c.lapses).put("lr", c.lastReview)
            }
            o.put(rank.toString(), j)
        }
        prefs.edit().putString("cards", o.toString()).apply()
    }

    // ---- Daily activity ---------------------------------------------------

    fun loadDays(): Map<Long, DayStat> {
        val raw = prefs.getString("days", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<Long, DayStat>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val d = o.getJSONObject(k)
                out[k.toLong()] = DayStat(d.optInt("r"), d.optInt("n"), d.optInt("t"), d.optInt("s"), d.optInt("rd"), d.optInt("g"))
            }
            out
        } catch (e: JSONException) {
            emptyMap()
        } catch (e: NumberFormatException) {
            emptyMap()
        }
    }

    fun saveDays(days: Map<Long, DayStat>) {
        val o = JSONObject()
        // Keep a year of history; older days only matter for nothing.
        val cutoff = today() - 400
        for ((day, s) in days) {
            if (day < cutoff) continue
            o.put(day.toString(), JSONObject().put("r", s.reviews).put("n", s.newCards).put("t", s.tested).put("s", s.shadowed).put("rd", s.read).put("g", s.grammar))
        }
        prefs.edit().putString("days", o.toString()).apply()
    }

    // ---- Stories read -----------------------------------------------------

    fun loadRead(): Set<String> = prefs.getStringSet("read", emptySet())?.toSet() ?: emptySet()

    fun saveRead(ids: Set<String>) {
        prefs.edit().putStringSet("read", ids).apply()
    }

    fun resetProgress() {
        prefs.edit().remove("cards").remove("days").remove("read").apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
