package com.example.batterybuddy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// AniList's free public GraphQL API: character search with a portrait + the series they are from.
private const val ANILIST = "https://graphql.anilist.co"
private const val QUERY =
    "query (\$q: String) { Page(perPage: 12) { characters(search: \$q, sort: FAVOURITES_DESC) { " +
        "name { full } image { large } media(perPage: 1, sort: POPULARITY_DESC) { nodes { isAdult title { romaji } } } } } }"

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var preview: BuddyView
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var spriteSwitch: Switch
    private lateinit var showSwitch: Switch
    @Volatile private var searchId = 0

    private data class Hit(val name: String, val series: String, val image: String, val adult: Boolean)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Buddy.prefs(this)

        // needed on Android 13+ for the charge-limit and overheat alerts
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(96))
        }
        col.addView(TextView(this).apply { text = "Battery Buddy"; textSize = 26f })

        preview = BuddyView(this).apply {
            image = Buddy.loadImage(this@MainActivity)
            sprite = prefs.getBoolean("sprite", false)
        }
        col.addView(preview, LinearLayout.LayoutParams(dp(120), dp(150)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12); bottomMargin = dp(12)
        })

        col.addView(button("1. Turn on \u201cBattery Buddy\u201d in Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        showSwitch = toggle("Show buddy", "show", true)
        col.addView(showSwitch)

        // ---- character search ----
        col.addView(TextView(this).apply {
            text = "2. Pick your character"; textSize = 18f; setPadding(0, dp(20), 0, 0)
        })
        val box = EditText(this).apply {
            hint = "Any anime character, e.g. Dazai, Johan"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        box.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { search(box); true } else false
        }
        col.addView(box)
        col.addView(button("Search") { search(box) })
        status = TextView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(status)
        col.addView(results)

        spriteSwitch = Switch(this).apply {
            text = "My image is a full-body transparent PNG (draw it as-is, not as a head)"
            isChecked = prefs.getBoolean("sprite", false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("sprite", on).apply()
                preview.sprite = on
            }
        }
        col.addView(spriteSwitch)

        col.addView(TextView(this).apply {
            text = "3. Place her on the battery"; textSize = 18f; setPadding(0, dp(20), 0, 0)
        })
        col.addView(slider("Size", "size", 16, 60, 26, "dp"))
        col.addView(slider("Distance from right edge", "x", 0, 120, 6, "dp"))
        col.addView(slider("Distance from top", "y", -20, 40, 0, "dp"))

        // ---- safety ----
        col.addView(TextView(this).apply {
            text = "4. Safety"; textSize = 18f; setPadding(0, dp(20), 0, 0)
        })
        col.addView(toggle("Tell me when to unplug (protects battery health)", "alert_charge", true))
        col.addView(slider("Charge limit", "limit", 50, 100, 80, "%"))
        col.addView(toggle("Overheat warning at 43 \u00b0C and above", "alert_hot", true))
        col.addView(toggle("Hide search results from adult titles", "hide_adult", true))
        col.addView(TextView(this).apply {
            text = "She can\u2019t be tapped, so she never blocks your screen. She rests at 15% to save power. " +
                "Add the \u201cBattery Buddy\u201d tile to Quick Settings for an instant on/off switch."
            textSize = 13f; setPadding(0, dp(8), 0, 0)
        })

        col.addView(button("Use an image from my gallery") { pickFromGallery() })
        col.addView(button("Back to the default girl") {
            prefs.edit().putBoolean("custom", false).putLong("rev", System.nanoTime()).apply()
            preview.image = null
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        showSwitch.isChecked = prefs.getBoolean("show", true) // the Quick Settings tile may have changed it
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } }

    private fun toggle(label: String, key: String, def: Boolean) = Switch(this).apply {
        text = label
        isChecked = prefs.getBoolean(key, def)
        setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
    }

    private fun slider(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String): LinearLayout {
        val tv = TextView(this)
        val sb = SeekBar(this).apply {
            max = hi - lo
            progress = prefs.getInt(key, def) - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    tv.text = "$label: ${p + lo} $unit"
                    if (fromUser) prefs.edit().putInt(key, p + lo).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        tv.text = "$label: ${sb.progress + lo} $unit"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(tv); addView(sb)
        }
    }

    // ---------------- search ----------------

    private fun search(box: EditText) {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(box.windowToken, 0)
        val q = box.text.toString().trim()
        if (q.isEmpty()) return
        val hideAdult = prefs.getBoolean("hide_adult", true)
        val id = ++searchId
        results.removeAllViews()
        status.text = "Searching\u2026"
        Thread {
            try {
                val hits = anilist(q, hideAdult)
                runOnUiThread {
                    if (id == searchId) status.text = if (hits.isEmpty()) "No character found." else "Tap one to use them:"
                }
                for (h in hits) {
                    if (id != searchId) return@Thread
                    val bmp = download(h.image)
                    runOnUiThread { if (id == searchId) addRow(h, bmp) }
                }
            } catch (e: Exception) {
                runOnUiThread { if (id == searchId) status.text = "Search failed. Check your internet connection." }
            }
        }.start()
    }

    private fun anilist(q: String, hideAdult: Boolean): List<Hit> {
        val body = JSONObject().put("query", QUERY).put("variables", JSONObject().put("q", q)).toString()
        val conn = URL(ANILIST).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(json).getJSONObject("data").getJSONObject("Page").getJSONArray("characters")
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val node = o.optJSONObject("media")?.optJSONArray("nodes")?.optJSONObject(0)
                Hit(
                    o.getJSONObject("name").optString("full"),
                    node?.optJSONObject("title")?.optString("romaji") ?: "",
                    o.getJSONObject("image").optString("large"),
                    node?.optBoolean("isAdult", false) ?: false
                )
            }.filter { !(hideAdult && it.adult) }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String): Bitmap? {
        if (!url.startsWith("https://")) return null // HTTPS only
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun addRow(hit: Hit, bmp: Bitmap?) {
        val thumb = ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val label = TextView(this).apply {
            text = hit.name + "\n" + hit.series
            setPadding(dp(12), 0, 0, 0)
        }
        results.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(thumb, LinearLayout.LayoutParams(dp(48), dp(64)))
            addView(label)
            setOnClickListener { if (bmp != null) choose(bmp, hit.name) }
        })
    }

    private fun choose(bmp: Bitmap, name: String) {
        save(bmp)
        spriteSwitch.isChecked = false
        prefs.edit().putBoolean("sprite", false).apply()
        preview.sprite = false
        preview.image = bmp
        Toast.makeText(this, "$name is on duty", Toast.LENGTH_SHORT).show()
    }

    private fun save(bmp: Bitmap) {
        FileOutputStream(File(filesDir, "buddy.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        prefs.edit().putBoolean("custom", true).putLong("rev", System.nanoTime()).apply()
    }

    // ---------------- gallery ----------------

    private fun pickFromGallery() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = 400f / maxOf(info.size.width, info.size.height)
                if (s < 1f) dec.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            save(bmp)
            preview.image = bmp
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn\u2019t load that image", Toast.LENGTH_SHORT).show()
        }
    }
}
