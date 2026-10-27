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
                exampleHr = o.optString("exHr", "")
            )
        }
        return out.sortedBy { it.rank }
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
        direction = enumOr(prefs.getString("direction", null), Direction.DE_HR),
        autoSpeak = prefs.getBoolean("autoSpeak", true),
        slowSpeech = prefs.getBoolean("slowSpeech", false),
        reminderOn = prefs.getBoolean("reminderOn", false),
        reminderHour = prefs.getInt("reminderHour", 19),
        reminderMinute = prefs.getInt("reminderMinute", 0),
        themeMode = enumOr(prefs.getString("themeMode", null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamicColor", false)
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putInt("newPerDay", s.newPerDay)
            .putString("direction", s.direction.name)
            .putBoolean("autoSpeak", s.autoSpeak)
            .putBoolean("slowSpeech", s.slowSpeech)
            .putBoolean("reminderOn", s.reminderOn)
            .putInt("reminderHour", s.reminderHour)
            .putInt("reminderMinute", s.reminderMinute)
            .putString("themeMode", s.themeMode.name)
            .putBoolean("dynamicColor", s.dynamicColor)
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
                    ease = c.optDouble("e", 2.5),
                    due = c.optLong("d", 0),
                    reps = c.optInt("n", 0),
                    lapses = c.optInt("l", 0)
                )
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
                j.put("i", c.interval).put("e", c.ease).put("d", c.due)
                    .put("n", c.reps).put("l", c.lapses)
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
                out[k.toLong()] = DayStat(d.optInt("r"), d.optInt("n"), d.optInt("t"))
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
            o.put(day.toString(), JSONObject().put("r", s.reviews).put("n", s.newCards).put("t", s.tested))
        }
        prefs.edit().putString("days", o.toString()).apply()
    }

    fun resetProgress() {
        prefs.edit().remove("cards").remove("days").apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
