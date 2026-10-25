package org.aiknow.android.data
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Never attach session cookies to image or attribution hosts. */
fun mediaUrl(raw: String?, base: String, thumbnail: Boolean = false): String? {
    if(raw.isNullOrBlank()) return null
    if(raw.startsWith("android.resource://")) return raw
    val origin = base.toHttpUrlOrNull() ?: return null
    var url = origin.resolve(raw) ?: return null
    if(url.host in setOf("localhost","127.0.0.1","::1")) url = url.newBuilder().scheme(origin.scheme).host(origin.host).port(origin.port).build()
    if(url.scheme != "https") return null
    if(url.host == origin.host && Regex("/media/generated/[a-f0-9]{64}\\.png").matches(url.encodedPath))
        return url.newBuilder().setQueryParameter("preview",if(thumbnail) "320" else "1280").build().toString()
    return url.toString()
}
