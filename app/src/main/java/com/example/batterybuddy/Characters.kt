package com.example.batterybuddy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One anime character. [rank] is the AniList favourites ranking (0 when it is a search result). */
data class Chara(
    val id: Long,
    val name: String,
    val series: String,
    val small: String,
    val large: String,
    val adult: Boolean = false,
    val rank: Int = 0
)

/** AniList's free public GraphQL API. Sorted by favourites, so page 1..4 is literally the Top 200. */
object Anilist {
    private const val API = "https://graphql.anilist.co"
    private const val QUERY =
        "query (\$page: Int, \$q: String) { Page(page: \$page, perPage: 50) { pageInfo { hasNextPage } " +
            "characters(search: \$q, sort: FAVOURITES_DESC) { id name { full } image { medium large } " +
            "media(perPage: 1, sort: POPULARITY_DESC) { nodes { isAdult title { romaji } } } } } }"

    class Batch(val items: List<Chara>, val hasNext: Boolean)

    /** Blocking: call from Dispatchers.IO. A blank [query] returns the global ranking. */
    fun fetch(query: String?, page: Int): Batch {
        val vars = JSONObject().put("page", page)
        if (!query.isNullOrBlank()) vars.put("q", query)
        val body = JSONObject().put("query", QUERY).put("variables", vars).toString()
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val pageObj = JSONObject(json).getJSONObject("data").getJSONObject("Page")
            val arr = pageObj.getJSONArray("characters")
            val items = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val node = o.optJSONObject("media")?.optJSONArray("nodes")?.optJSONObject(0)
                val img = o.getJSONObject("image")
                Chara(
                    id = o.getLong("id"),
                    name = o.getJSONObject("name").optString("full"),
                    series = node?.optJSONObject("title")?.optString("romaji") ?: "",
                    small = img.optString("medium"),
                    large = img.optString("large"),
                    adult = node?.optBoolean("isAdult", false) ?: false,
                    rank = if (query.isNullOrBlank()) (page - 1) * 50 + i + 1 else 0
                )
            }
            return Batch(items, pageObj.getJSONObject("pageInfo").optBoolean("hasNextPage", false))
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String): Bitmap? {
        if (!url.startsWith("https://")) return null // HTTPS only
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
}

/** A paged list that loads more of itself: the Top 200 (and beyond) or a search. */
class Feed(var query: String?) {
    val items = mutableStateListOf<Chara>()
    var page by mutableIntStateOf(0)
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var gen = 0

    fun reset(q: String?) {
        gen++
        query = q
        items.clear()
        page = 0
        hasMore = true
        loading = false
        error = null
    }

    suspend fun loadNext(hideAdult: Boolean) {
        if (loading || !hasMore) return
        val g = gen
        loading = true
        error = null
        try {
            val r = withContext(Dispatchers.IO) { Anilist.fetch(query, page + 1) }
            if (g == gen) {
                page += 1
                hasMore = r.hasNext
                items.addAll(r.items.filter { !(hideAdult && it.adult) })
            }
        } catch (e: Exception) {
            if (g == gen) error = "Couldn’t reach AniList. Check your internet and try again."
        }
        if (g == gen) loading = false
    }
}

/** "My buddies": every character you have picked is saved on the phone, so it works offline. */
object Saved {
    fun load(p: SharedPreferences): List<Chara> = try {
        val a = JSONArray(p.getString("collection", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Chara(o.getLong("id"), o.getString("name"), o.optString("series"), "", "")
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun add(p: SharedPreferences, c: Chara) {
        val all = listOf(c) + load(p).filter { it.id != c.id } // newest first
        val a = JSONArray()
        all.take(40).forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("series", it.series)) }
        p.edit().putString("collection", a.toString()).apply()
    }

    private fun writePng(f: File, bmp: Bitmap) {
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun activate(ctx: Context, bmp: Bitmap, c: Chara) {
        val p = Buddy.prefs(ctx)
        writePng(File(ctx.filesDir, "buddy.png"), bmp)
        add(p, c)
        p.edit()
            .putBoolean("custom", true)
            .putBoolean("sprite", false)
            .putString("char_name", c.name)
            .putLong("rev", System.nanoTime())
            .apply()
    }

    suspend fun fromWeb(ctx: Context, c: Chara): Boolean = withContext(Dispatchers.IO) {
        val bmp = Anilist.download(c.large) ?: return@withContext false
        try {
            writePng(File(ctx.filesDir, "col_${c.id}.png"), bmp)
            activate(ctx, bmp, c)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fromSaved(ctx: Context, c: Chara): Boolean = withContext(Dispatchers.IO) {
        val bmp = BitmapFactory.decodeFile(File(ctx.filesDir, "col_${c.id}.png").path) ?: return@withContext false
        try {
            activate(ctx, bmp, c)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importPicture(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = 400f / maxOf(info.size.width, info.size.height)
                if (s < 1f) dec.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            writePng(File(ctx.filesDir, "buddy.png"), bmp)
            Buddy.prefs(ctx).edit()
                .putBoolean("custom", true)
                .putString("char_name", "My picture")
                .putLong("rev", System.nanoTime())
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun useBuiltIn(ctx: Context) {
        Buddy.prefs(ctx).edit()
            .putBoolean("custom", false)
            .putString("char_name", "")
            .putLong("rev", System.nanoTime())
            .apply()
    }
}
