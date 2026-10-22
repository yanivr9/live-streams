package com.streamdesk.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/*
 * "Play on TV": the app on a TV box listens on the home network; the phone app finds it, and sends a channel to play,
 * remote-control commands, or the sign-in details. Every request needs the pairing code shown on the TV.
 */

const val LINK_PORT = 8765
const val LINK_SERVICE_TYPE = "_streamdesk._tcp."

/** A TV running Stream Desk, as found on the network. */
data class TvDevice(val name: String, val host: String, val port: Int = LINK_PORT)

/** Sign-in details sent from the phone. */
data class LoginRequest(
    val login: XtreamLogin,
    val name: String?,
    val enabled: Boolean,
    val updateHours: Int,
    val sport: SportSettings?,
)

interface LinkHandler {
    fun play(queue: List<Channel>, index: Int)
    fun command(action: String)
    fun login(request: LoginRequest)
}

/** Why a call to the TV failed, in words the user can act on. */
class LinkException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { WrongCode, TooManyTries, Unreachable, Other }
}

/** The receiving side: a small HTTP server on the TV. */
class LinkServer(
    private val deviceName: String,
    private val version: String,
    private val code: () -> String,
    private val handler: LinkHandler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var socket: ServerSocket? = null
    private var pool: ExecutorService? = null

    // Guessing the code: 5 wrong tries lock everyone out for a minute
    private var failures = 0
    private var windowStart = 0L
    private var blockedUntil = 0L

    /** Starts listening; returns the port it got (the wanted one, or any free one if that is taken). */
    @Synchronized
    fun start(port: Int = LINK_PORT): Int {
        stop()
        val server = try { ServerSocket(port) } catch (e: IOException) { ServerSocket(0) }
        val executor = Executors.newCachedThreadPool()
        socket = server
        pool = executor
        executor.execute {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (e: IOException) { break }
                executor.execute { serve(client) }
            }
        }
        return server.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        pool?.shutdownNow()
        socket = null
        pool = null
    }

    private fun serve(client: Socket) {
        client.use { c ->
            try {
                c.soTimeout = 5_000
                // Only devices on the home network (or this device) may talk to the TV
                val address = c.inetAddress
                if (!(address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress)) return
                val input = c.getInputStream().buffered()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':', "").trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length < 0 || length > MAX_BODY) return respond(c, 413, error("too big"))
                val body = if (length > 0) String(readBytes(input, length), Charsets.UTF_8) else ""
                val (status, json) = route(parts[0], parts[1].substringBefore('?'), headers["x-link-code"].orEmpty(), body)
                respond(c, status, json)
            } catch (e: Exception) {
                runCatching { respond(c, 400, error("bad request")) }
            }
        }
    }

    private fun route(method: String, path: String, given: String, body: String): Pair<Int, JSONObject> {
        if (method == "GET" && path == "/ping") {
            return 200 to JSONObject().put("app", "streamdesk").put("name", deviceName).put("version", version)
        }
        if (method != "POST" || path !in setOf("/play", "/command", "/login")) return 404 to error("not found")
        authorize(given)?.let { return it }
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return 400 to error("bad request")
        when (path) {
            "/play" -> {
                val arr = json.optJSONArray("queue") ?: return 400 to error("no channels")
                val queue = List(arr.length()) { Channel.fromJson(arr.getJSONObject(it)) }
                if (queue.isEmpty()) return 400 to error("no channels")
                handler.play(queue, json.optInt("index").coerceIn(queue.indices))
            }
            "/command" -> handler.command(json.optString("action"))
            "/login" -> {
                val login = json.optJSONObject("login")?.let(XtreamLogin::fromJson) ?: return 400 to error("no login")
                val sport = json.optJSONObject("sport")?.let {
                    SportSettings(it.optString("text"), runCatching { QualityCap.valueOf(it.optString("cap")) }.getOrDefault(QualityCap.Best))
                }
                handler.login(LoginRequest(login, json.optString("name").ifBlank { null }, json.optBoolean("enabled", true), json.optInt("updateHours", 24), sport))
            }
        }
        return 200 to JSONObject().put("ok", true)
    }

    /** Null if the code is right; otherwise the answer to send back. */
    @Synchronized
    private fun authorize(given: String): Pair<Int, JSONObject>? {
        val t = now()
        if (t < blockedUntil) return 429 to error("too many tries")
        val expected = code()
        if (MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())) {
            failures = 0
            return null
        }
        if (t - windowStart > 60_000) { windowStart = t; failures = 0 }
        if (++failures >= MAX_FAILURES) blockedUntil = t + 60_000
        return 403 to error("wrong code")
    }

    private fun error(message: String) = JSONObject().put("error", message)

    private fun respond(c: Socket, status: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 413 -> "Payload Too Large"; 429 -> "Too Many Requests"; else -> "Error" }
        val out = c.getOutputStream()
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val out = ByteArrayOutputStream(length)
        val buf = ByteArray(8192)
        var left = length
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n < 0) throw IOException("closed early")
            out.write(buf, 0, n)
            left -= n
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BODY = 512 * 1024
        const val MAX_FAILURES = 5
    }
}

/** The sending side, used by the phone. All calls block: run them off the main thread. */
object LinkClient {
    private const val NOT_REACHED = "Can't reach the TV. Open Stream Desk on the TV, and make sure both are on the same Wi-Fi."

    /** Checks that Stream Desk is listening at the address; returns the TV's name. */
    fun ping(host: String, port: Int = LINK_PORT): TvDevice {
        val json = request("GET", host, port, "/ping", null, "")
        if (json.optString("app") != "streamdesk") throw LinkException(LinkException.Kind.Other, "That address isn't a Stream Desk TV.")
        return TvDevice(json.optString("name").ifBlank { host }, host, port)
    }

    fun play(device: TvDevice, code: String, queue: List<Channel>, index: Int) {
        val body = JSONObject().put("index", index).put("queue", JSONArray().also { a -> queue.forEach { a.put(it.toJson()) } })
        request("POST", device.host, device.port, "/play", code, body.toString())
    }

    /** "next", "prev" or "stop". */
    fun command(device: TvDevice, code: String, action: String) {
        request("POST", device.host, device.port, "/command", code, JSONObject().put("action", action).toString())
    }

    fun sendLogin(device: TvDevice, code: String, request: LoginRequest) {
        val body = JSONObject()
            .put("login", request.login.toJson())
            .put("name", request.name)
            .put("enabled", request.enabled)
            .put("updateHours", request.updateHours)
        request.sport?.let { body.put("sport", JSONObject().put("text", it.text).put("cap", it.cap.name)) }
        request("POST", device.host, device.port, "/login", code, body.toString())
    }

    private fun request(method: String, host: String, port: Int, path: String, code: String?, body: String): JSONObject {
        val conn = try {
            URL("http://$host:$port$path").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw LinkException(LinkException.Kind.Other, "That address isn't valid.")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 4_000
            conn.readTimeout = 8_000
            code?.let { conn.setRequestProperty("X-Link-Code", it.trim()) }
            if (method == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = try { conn.responseCode } catch (e: IOException) {
                AppLog.e("link", "$method $path to $host failed", e)
                throw LinkException(LinkException.Kind.Unreachable, NOT_REACHED)
            }
            AppLog.i("link", "$method $path → HTTP $status")
            when (status) {
                200 -> return runCatching { JSONObject(conn.inputStream.bufferedReader().readText()) }.getOrDefault(JSONObject())
                403 -> throw LinkException(LinkException.Kind.WrongCode, "Wrong code. Check the code shown on the TV under ⋮ → Link a phone.")
                429 -> throw LinkException(LinkException.Kind.TooManyTries, "Too many wrong codes. Wait a minute and try again.")
                else -> throw LinkException(LinkException.Kind.Other, "The TV answered with an error (HTTP $status). Update Stream Desk on both devices.")
            }
        } finally {
            conn.disconnect()
        }
    }
}
