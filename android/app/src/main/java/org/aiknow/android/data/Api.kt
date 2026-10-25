package org.aiknow.android.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val json = Json { ignoreUnknownKeys = true }
fun JsonElement.obj() = this as? JsonObject ?: JsonObject(emptyMap())
fun JsonElement.text(key: String, fallback: String = ""): String = (obj()[key] as? JsonPrimitive)?.contentOrNull ?: fallback
fun JsonElement.number(key: String): Long = (obj()[key] as? JsonPrimitive)?.longOrNull ?: 0L
fun JsonElement.flag(key: String): Boolean = (obj()[key] as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonElement.array(key: String): List<JsonElement> = (obj()[key] as? JsonArray)?.toList() ?: emptyList()
fun JsonElement.rows(): List<JsonElement> = (this as? JsonArray)?.toList() ?: array("content")
fun payload(vararg fields: Pair<String, Any?>): JsonObject = buildJsonObject {
    fields.forEach { (key, value) -> put(key, when(value) {
        null -> JsonNull; is JsonElement -> value; is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value); else -> JsonPrimitive(value.toString())
    }) }
}
class ApiException(val status: Int, val code: String = "", detail: String = "") : IOException(
    when {
        status == 401 -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
        status == 409 -> "다른 변경이 반영되었습니다. 다시 조회한 뒤 수정해 주세요."
        status >= 500 -> "서버가 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
        detail.isNotBlank() -> detail.take(400)
        status == 403 -> "이 작업을 수행할 권한이 없습니다."
        status == 404 -> "요청한 콘텐츠를 찾을 수 없습니다."
        else -> "요청을 완료하지 못했습니다 ($status)."
    }
)
interface Gateway {
    suspend fun request(path: String, method: String = "GET", body: JsonElement? = null, query: Map<String,String> = emptyMap()): JsonElement
    fun clearSession()
    var onUnauthorized: () -> Unit
}
interface CookieStore { fun load(): String?; fun save(cookie: String? ) }
class SessionCookies(private val origin: HttpUrl, private val store: CookieStore) : CookieJar {
    private var cookie = store.load()?.let { Cookie.parse(origin, it) }?.takeIf { it.name == "JSESSIONID" && it.matches(origin) }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != origin.host || url.scheme != origin.scheme || url.port != origin.port) return
        cookies.lastOrNull { it.name == "JSESSIONID" && it.matches(origin) }?.let {
            cookie = it.takeIf { c -> c.expiresAt > System.currentTimeMillis() }
            store.save(cookie?.toString())
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.host == origin.host && url.scheme == origin.scheme && url.port == origin.port)
            listOfNotNull(cookie?.takeIf { it.matches(url) && it.expiresAt > System.currentTimeMillis() }) else emptyList()
    @Synchronized fun clear() { cookie = null; store.save(null) }
}
class HttpGateway(baseUrl: String, private val cookies: SessionCookies) : Gateway {
    private val origin = baseUrl.toHttpUrl()
    private val client = OkHttpClient.Builder().cookieJar(cookies).followRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val mutation = Mutex()
    override var onUnauthorized: () -> Unit = {}
    override fun clearSession() = cookies.clear()
    override suspend fun request(path: String, method: String, body: JsonElement?, query: Map<String,String>): JsonElement {
        require(path.startsWith("api/v1/")) { "Only app API paths are allowed" }
        return if(method == "GET") send(path, method, body, query) else mutation.withLock {
            // A fresh token in the same cookie session; no blind retry of an admin mutation.
            val csrf = send("api/v1/auth/csrf", "GET", null, emptyMap())
            send(path, method, body, query, csrf.text("headerName") to csrf.text("token"))
        }
    }
    private suspend fun send(path: String, method: String, body: JsonElement?, query: Map<String,String>, csrf: Pair<String,String>? = null): JsonElement {
        val url = origin.newBuilder().addPathSegments(path).apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").header("Cache-Control", "no-store")
            .apply { csrf?.let { header(it.first,it.second) } }
            .method(method, if(method in listOf("GET","HEAD")) null else (body?.toString() ?: "").toRequestBody("application/json".toMediaType())).build()
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) { if(!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
            })
        }
        return response.use {
            val raw = it.body?.string().orEmpty()
            if(it.code == 401) { clearSession(); onUnauthorized() }
            if(!it.isSuccessful) {
                val detail = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                throw ApiException(it.code, detail?.text("code").orEmpty(), detail?.text("message").orEmpty())
            }
            if(raw.isBlank()) JsonNull else json.parseToJsonElement(raw)
        }
    }
}
