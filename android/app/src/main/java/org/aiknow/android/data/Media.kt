package org.aiknow.android.data
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Never attach session cookies to image or attribution hosts. */
fun resolvedMediaUrl(raw: String?, base: String): String? {
    if(raw.isNullOrBlank()) return null
    if(raw.startsWith("android.resource://")) return raw
    val origin = base.toHttpUrlOrNull() ?: return null
    var url = origin.resolve(raw) ?: return null
    if(url.host in setOf("localhost","127.0.0.1","::1")) url = url.newBuilder().scheme(origin.scheme).host(origin.host).port(origin.port).build()
    if(url.scheme != "https") return null
    return url.toString()
}

/** Same opt-in preview policy as iOS ImageLoader.requestURL; stored source URLs stay unchanged. */
fun mediaUrl(raw: String?, base: String, thumbnail: Boolean = false, original: Boolean = false): String? {
    val resolved = resolvedMediaUrl(raw, base) ?: return null
    val url = resolved.toHttpUrlOrNull() ?: return resolved
    val origin = base.toHttpUrlOrNull()
    if (url.host == origin?.host && url.port == origin.port &&
        Regex("/media/generated/[a-f0-9]{64}\\.png").matches(url.encodedPath) &&
        url.queryParameter("preview") in listOf("320", "1280")) {
        return url.newBuilder().removeAllQueryParameters("preview").apply {
            if (!original) addQueryParameter("preview", if (thumbnail) "320" else "1280")
        }.build().toString()
    }
    if (!original && url.host == "upload.wikimedia.org" && url.query == null && url.fragment == null &&
        url.username.isEmpty() && Regex("/wikipedia/commons/[a-f0-9]/[a-f0-9]{2}/[^/]+\\.(png|jpg|jpeg|webp)", RegexOption.IGNORE_CASE).matches(url.encodedPath)) {
        val file = url.encodedPath.substringAfterLast('/')
        return url.newBuilder().encodedPath(url.encodedPath.replace("/wikipedia/commons/", "/wikipedia/commons/thumb/") + "/${if (thumbnail) 330 else 1280}px-$file").build().toString()
    }
    return resolved
}
