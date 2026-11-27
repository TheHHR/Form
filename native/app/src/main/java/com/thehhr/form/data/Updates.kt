package com.thehhr.form.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Latest release on GitHub (public API, no key). Blocking: call on IO. */
object Updates {
    const val REPO = "https://github.com/TheHHR/Form"
    const val DONATE = "https://thehhr.github.io/Form/#donate"

    data class Release(val version: String, val pageUrl: String, val apkUrl: String?)

    fun latest(): Release? = runCatching {
        val conn = URL("https://api.github.com/repos/TheHHR/Form/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return null
            val o = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            val assets = o.optJSONArray("assets")
            val apk = assets?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
            Release(o.getString("tag_name").removePrefix("v"), o.optString("html_url", "$REPO/releases/latest"), apk?.optString("browser_download_url"))
        } finally { conn.disconnect() }
    }.getOrNull()

    /** True when [latest] ("4.0.3") is newer than [current] ("4.0.2" or "4.0.2-beta"). Numeric parts only. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
