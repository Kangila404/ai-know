package org.aiknow.android.data
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ApiTest {
    private class MemoryStore:CookieStore {var cookie:String?=null;override fun load()=cookie;override fun save(cookie:String?){this.cookie=cookie}}
    @Test fun csrfAndRotatedSessionStayTogether() = runBlocking {
        MockWebServer().use{s->
            s.enqueue(MockResponse().setHeader("Set-Cookie","JSESSIONID=anonymous; Path=/; HttpOnly").setBody("""{"headerName":"X-CSRF-TOKEN","token":"nonce-csrf"}"""))
            s.enqueue(MockResponse().setBody("""{"nonce":"challenge"}"""))
            s.enqueue(MockResponse().setBody("""{"headerName":"X-CSRF-TOKEN","token":"login-csrf"}"""))
            s.enqueue(MockResponse().setHeader("Set-Cookie","JSESSIONID=rotated; Path=/; HttpOnly").setBody("""{"userId":"u"}"""))
            s.enqueue(MockResponse().setBody("""{"nickname":"테스터"}"""))
            val store=MemoryStore();val api=HttpGateway(s.url("/").toString(),SessionCookies(s.url("/"),store))
            assertEquals("challenge",api.request("api/v1/auth/social/GOOGLE/challenge","POST").text("nonce"))
            api.request("api/v1/auth/social/GOOGLE","POST",payload("idToken" to "test-only-token"))
            api.request("api/v1/users")
            val requests=(1..5).map{s.takeRequest()}
            assertEquals("nonce-csrf",requests[1].getHeader("X-CSRF-TOKEN"))
            assertEquals("JSESSIONID=anonymous",requests[1].getHeader("Cookie"))
            assertEquals("login-csrf",requests[3].getHeader("X-CSRF-TOKEN"))
            assertEquals("JSESSIONID=rotated",requests[4].getHeader("Cookie"))
            assertTrue(store.cookie!!.contains("rotated"))
        }
    }
    @Test fun rejectedMutationIsNeverBlindlyRetried()=runBlocking {
        MockWebServer().use{s->
            s.enqueue(MockResponse().setBody("""{"headerName":"X-CSRF-TOKEN","token":"csrf"}"""))
            s.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"CONFLICT","message":"stale"}"""))
            val api=HttpGateway(s.url("/").toString(),SessionCookies(s.url("/"),MemoryStore()))
            try {api.request("api/v1/admin/users/2","PATCH",payload("version" to 0));fail("must reject") } catch(e:ApiException){assertEquals(409,e.status)}
            assertEquals(2,s.requestCount)
        }
    }
    @Test fun unauthorizedClearsPersistedSession()=runBlocking {
        MockWebServer().use{s->
            s.enqueue(MockResponse().setResponseCode(401))
            val store=MemoryStore().apply{cookie="JSESSIONID=expired; Path=/"}
            val api=HttpGateway(s.url("/").toString(),SessionCookies(s.url("/"),store));var expired=false;api.onUnauthorized={expired=true}
            try{api.request("api/v1/users");fail("must reject")}catch(e:ApiException){assertEquals(401,e.status)}
            assertNull(store.cookie);assertTrue(expired)
        }
    }
    @Test fun cookieNeverEscapesApiOrigin() {
        val origin="https://aiknow.kro.kr/".toHttpUrl();val store=MemoryStore();val jar=SessionCookies(origin,store)
        jar.saveFromResponse(origin,listOf(Cookie.parse(origin,"JSESSIONID=private; Secure; HttpOnly; Path=/")!!))
        assertEquals(1,jar.loadForRequest(origin).size)
        assertTrue(jar.loadForRequest("https://images.example.org/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://aiknow.kro.kr/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://aiknow.kro.kr:8443/".toHttpUrl()).isEmpty())
    }
    @Test fun imageVariantsUseSupportedSizesAndPreserveExternalSources() {
        val base="https://aiknow.kro.kr/";val hash="a".repeat(64)
        assertEquals("${base}media/generated/$hash.png?preview=320",mediaUrl("/media/generated/$hash.png?preview=1280",base,true))
        assertEquals("${base}media/generated/$hash.png",mediaUrl("/media/generated/$hash.png",base))
        assertEquals("${base}media/generated/$hash.png",mediaUrl("/media/generated/$hash.png?preview=1280",base,original=true))
        assertEquals("https://images.example.org/photo.png",mediaUrl("https://images.example.org/photo.png",base,true))
        assertNull(mediaUrl("javascript:alert(1)",base));assertNull(mediaUrl("http://images.example.org/photo.png",base))
    }
    @Test fun commonsThumbnailsNeverRewriteSignedUrlsOrOriginalZoom() {
        val base="https://aiknow.kro.kr/"
        val source="https://upload.wikimedia.org/wikipedia/commons/a/ab/Example%20Image.jpg"
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Example%20Image.jpg/330px-Example%20Image.jpg",mediaUrl(source,base,true))
        assertEquals(source,mediaUrl(source,base,original=true))
        assertEquals("$source?signature=keep",mediaUrl("$source?signature=keep",base,true))
        assertEquals(source.replace(".jpg",".svg"),mediaUrl(source.replace(".jpg",".svg"),base,true))
    }
    @Test fun recoveryUploadKeepsCsrfCookieVersionAndPngTogether()=runBlocking {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setHeader("Set-Cookie","JSESSIONID=admin; Path=/").setBody("""{"headerName":"X-CSRF-TOKEN","token":"upload-csrf"}"""))
            s.enqueue(MockResponse().setBody("""{"id":11,"recovered":true}"""))
            val api=HttpGateway(s.url("/").toString(),SessionCookies(s.url("/"),MemoryStore()))
            val png=listOf(137,80,78,71,13,10,26,10).map{it.toByte()}.toByteArray()+byteArrayOf(0,1,2)
            assertTrue(api.recoverImage(11,3,"출처 확인 후 복구",png).flag("recovered"))
            s.takeRequest();val sent=s.takeRequest()
            assertEquals("/api/v1/admin/generations/11/recover-image",sent.path)
            assertEquals("upload-csrf",sent.getHeader("X-CSRF-TOKEN"));assertEquals("JSESSIONID=admin",sent.getHeader("Cookie"))
            assertTrue(sent.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
            val body=sent.body.readUtf8()
            assertTrue(body.contains("name=\"version\"\r\nContent-Length: 1\r\n\r\n3")||body.contains("\r\n\r\n3\r\n"))
            assertTrue(body.contains("출처 확인 후 복구"));assertTrue(body.contains("filename=\"recovery.png\""));assertTrue(body.contains("Content-Type: image/png"))
        }
    }
    @Test fun emptySuccessAndEncodedSearch()=runBlocking {
        MockWebServer().use{s->
            s.enqueue(MockResponse().setResponseCode(204))
            val api=HttpGateway(s.url("/").toString(),SessionCookies(s.url("/"),MemoryStore()))
            assertEquals(JsonNull,api.request("api/v1/cardNews",query=mapOf("query" to "AI & 이론")))
            assertEquals("AI & 이론",s.takeRequest().requestUrl!!.queryParameter("query"))
        }
    }
}
