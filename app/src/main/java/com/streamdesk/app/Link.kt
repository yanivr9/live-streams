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
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/*
 * "Play on TV": the app on a TV box listens on the home network; the phone app finds it, and sends a channel to play,
 * remote-control commands, or the sign-in details. Every request needs the pairing code shown on the TV.
 */

const val LINK_PORT = 8765
const val LINK_SERVICE_TYPE = "_streamdesk._tcp."

/** A TV running Livio, as found on the network. */
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
    /** A playlist link (M3U) sent from the phone, to add on the TV. */
    fun playlist(url: String, name: String?) {}
    /** "Continue on phone": what the TV is playing (channels and position), and stop playing it. Null if nothing plays. */
    fun takeOver(): Pair<List<Channel>, Int>?
    /** The TV's activity log (passwords already hidden), so it can be read on the phone. */
    fun logs(): String
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
                // The QR code on the TV opens this page on the phone: a button that starts Livio with the TV's details
                val path = parts[1].substringBefore('?')
                if (parts[0] == "GET" && path == "/connect") {
                    val given = queryParam(parts[1], "c")
                    authorize(given)?.let { return respond(c, it.first, it.second) }
                    val host = (headers["host"] ?: c.localAddress.hostAddress.orEmpty()).substringBefore(':')
                    return respondHtml(c, connectPage(host, c.localPort, given))
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
        if (method != "POST" || path !in setOf("/play", "/command", "/login", "/playlist", "/takeover", "/logs")) return 404 to error("not found")
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
            "/logs" -> return 200 to JSONObject().put("text", handler.logs())
            "/takeover" -> {
                val (queue, index) = handler.takeOver() ?: return 409 to error("nothing playing")
                return 200 to JSONObject().put("index", index).put("queue", JSONArray().also { a -> queue.forEach { a.put(it.toJson()) } })
            }
            "/playlist" -> {
                val url = json.optString("url").trim()
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return 400 to error("no link")
                handler.playlist(url, json.optString("name").ifBlank { null })
            }
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

    private fun queryParam(target: String, name: String): String =
        target.substringAfter('?', "").split('&')
            .firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault("") }.orEmpty()

    /**
     * The page the TV's QR code opens on a phone. An Android phone with Livio opens the app; any other phone (an iPhone)
     * types a login or a playlist link here and sends it straight to the TV over the home Wi-Fi.
     */
    private fun connectPage(host: String, port: Int, code: String): String {
        val name = URLEncoder.encode(deviceName, "UTF-8")
        val query = "host=$host&port=$port&code=$code&name=$name"
        val intent = "intent://connect?$query#Intent;scheme=streamdesk;package=com.streamdesk.app;end"
        val spaced = code.chunked(3).joinToString(" ")
        val tv = htmlEscape(deviceName)
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Livio</title>
<style>
*{box-sizing:border-box}body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#0e1113;color:#e3e8ea;padding:28px 18px 40px}
.w{max-width:420px;margin:0 auto}.logo{font:800 30px -apple-system,system-ui,sans-serif;text-align:center}.logo b{color:#ff9f1c}
h2{font-size:20px;text-align:center;margin:6px 0 18px;font-weight:600}
.seg{display:flex;border:1px solid #3a4246;border-radius:999px;overflow:hidden;margin-bottom:16px}
.seg button{flex:1;border:0;background:none;color:#e3e8ea;font-size:16px;padding:11px}.seg button.on{background:#ff9f1c;color:#1a1205;font-weight:700}
label{display:block;font-size:13px;color:#9aa5ab;margin:12px 0 5px}
input{width:100%;font-size:17px;padding:13px 14px;border-radius:12px;border:1px solid #3a4246;background:#171c1f;color:#e3e8ea}
input:focus{outline:2px solid #ff9f1c;border-color:transparent}
.go{display:block;width:100%;margin-top:20px;padding:16px;border:0;border-radius:14px;background:#ff9f1c;color:#1a1205;font-weight:700;font-size:18px}
.go:disabled{opacity:.6}.msg{min-height:24px;margin-top:14px;text-align:center;font-size:15px;line-height:1.4}.ok{color:#7ddc8a}.bad{color:#ff8a7a}
.note{font-size:13px;color:#9aa5ab;line-height:1.5;text-align:center;margin-top:16px}
.app{border-top:1px solid #2a3135;margin-top:26px;padding-top:18px;text-align:center;font-size:14px;color:#9aa5ab}
.app a{display:inline-block;margin-top:10px;padding:11px 22px;border:1px solid #ff9f1c;border-radius:999px;color:#ff9f1c;font-weight:600;text-decoration:none}
.c{font:700 22px monospace;color:#ff9f1c;letter-spacing:2px}.hide{display:none}
</style></head>
<body><div class="w">
<div class="logo">Livi<b>o</b></div>
<h2>Send your channels to $tv</h2>
<div class="seg"><button id="t0" class="on" onclick="mode(0)">Login</button><button id="t1" onclick="mode(1)">Playlist link</button></div>
<form id="f" onsubmit="send(event)" autocomplete="off">
<div id="m0">
<label for="server">Server</label><input id="server" type="url" inputmode="url" placeholder="http://server.com:8080" autocapitalize="off" autocorrect="off" spellcheck="false">
<label for="user">Username</label><input id="user" autocapitalize="off" autocorrect="off" spellcheck="false">
<label for="pass">Password</label><input id="pass" type="password" autocapitalize="off" autocorrect="off" spellcheck="false">
</div>
<div id="m1" class="hide">
<label for="link">Playlist link (M3U)</label><input id="link" type="url" inputmode="url" placeholder="https://…/get.php?…" autocapitalize="off" autocorrect="off" spellcheck="false">
</div>
<label for="name">Name (optional)</label><input id="name" placeholder="My TV">
<button class="go" id="go" type="submit">Send to TV</button>
</form>
<div class="msg" id="msg"></div>
<p class="note">🔒 The details go straight to the TV over your Wi-Fi. They aren't sent anywhere else.</p>
<div class="app" id="app">Have Livio on an Android phone?<br><a href="$intent">Open Livio</a><br><br>Or in Livio: Send login to TV, and type <span class="c">$spaced</span></div>
</div>
<script>
var CODE="$code",m=0;
if(/iPhone|iPad|iPod/.test(navigator.userAgent))document.getElementById("app").className="hide";
function el(i){return document.getElementById(i)}
function mode(n){m=n;el("t0").className=n?"":"on";el("t1").className=n?"on":"";el("m0").className=n?"hide":"";el("m1").className=n?"":"hide";say("","")}
function say(t,c){var e=el("msg");e.textContent=t;e.className="msg "+c}
function fix(u){u=u.trim();if(u&&!/^https?:\/\//i.test(u))u="http://"+u;return u}
function send(ev){
 ev.preventDefault();var name=el("name").value.trim(),path,body;
 if(m==0){var s=fix(el("server").value),u=el("user").value.trim(),p=el("pass").value;
  if(!s||!u||!p){say("Fill in the server, username and password.","bad");return}
  path="/login";body={login:{server:s,username:u,password:p,live:true,movies:false,series:false,categories:"Hebrew"},name:name,enabled:true,updateHours:24};
 }else{var l=fix(el("link").value);
  if(!l){say("Paste the playlist link.","bad");return}
  path="/playlist";body={url:l,name:name};
 }
 el("go").disabled=true;say("Sending…","");
 fetch(path,{method:"POST",headers:{"Content-Type":"application/json","X-Link-Code":CODE},body:JSON.stringify(body)})
 .then(function(r){
  if(r.ok){say("Sent! The TV is loading the channels now.","ok");el("f").reset()}
  else if(r.status==403)say("The code changed. Scan the QR code on the TV again.","bad");
  else if(r.status==429)say("Too many tries. Wait a minute and try again.","bad");
  else if(r.status==404)say("Update Livio on the TV, then try again.","bad");
  else say("The TV couldn't use these details. Check them and try again.","bad");
 }).catch(function(){say("Can't reach the TV. Make sure this phone is on the same Wi-Fi as the TV.","bad")})
 .then(function(){el("go").disabled=false});
}
</script></body></html>"""
    }

    private fun htmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun respondHtml(c: Socket, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = c.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

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
    private const val NOT_REACHED = "Can't reach the TV. Open Livio on the TV, and make sure both are on the same Wi-Fi."

    /** Checks that Livio is listening at the address; returns the TV's name. */
    fun ping(host: String, port: Int = LINK_PORT): TvDevice {
        val json = request("GET", host, port, "/ping", null, "")
        if (json.optString("app") != "streamdesk") throw LinkException(LinkException.Kind.Other, "That address isn't a Livio TV.")
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

    /** Stops the TV and returns what it was playing, so the phone can carry on with it. */
    fun takeOver(device: TvDevice, code: String): Pair<List<Channel>, Int> {
        val json = request("POST", device.host, device.port, "/takeover", code, "{}")
        val arr = json.optJSONArray("queue") ?: throw LinkException(LinkException.Kind.Other, "The TV didn't say what it's playing.")
        val queue = List(arr.length()) { Channel.fromJson(arr.getJSONObject(it)) }
        if (queue.isEmpty()) throw LinkException(LinkException.Kind.Other, "The TV didn't say what it's playing.")
        return queue to json.optInt("index").coerceIn(queue.indices)
    }

    /** The TV's activity log, to read and copy on the phone. */
    fun logs(device: TvDevice, code: String): String =
        request("POST", device.host, device.port, "/logs", code, "{}").optString("text")

    fun sendLogin(device: TvDevice, code: String, request: LoginRequest) {
        val body = JSONObject()
            .put("login", request.login.toJson())
            .put("name", request.name)
            .put("enabled", request.enabled)
            .put("updateHours", request.updateHours)
        request.sport?.let { body.put("sport", JSONObject().put("text", it.text).put("cap", it.cap.name)) }
        request("POST", device.host, device.port, "/login", code, body.toString())
    }

    fun sendPlaylist(device: TvDevice, code: String, url: String, name: String?) {
        request("POST", device.host, device.port, "/playlist", code, JSONObject().put("url", url).put("name", name).toString())
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
                409 -> throw LinkException(LinkException.Kind.Other, "Nothing is playing on the TV right now.")
                429 -> throw LinkException(LinkException.Kind.TooManyTries, "Too many wrong codes. Wait a minute and try again.")
                else -> throw LinkException(LinkException.Kind.Other, "The TV answered with an error (HTTP $status). Update Livio on both devices.")
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** What the phone learns by scanning the TV's QR code: where the TV is and its code. */
data class LinkInvite(val host: String, val port: Int, val code: String, val name: String) {
    companion object {
        /** Reads streamdesk://connect?host=…&port=…&code=…&name=…; null if it isn't a valid invitation for a TV on a home network. */
        fun parse(link: String): LinkInvite? {
            val uri = runCatching { java.net.URI(link.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "streamdesk" || uri.host != "connect") return null
            val q = uri.rawQuery?.split('&')?.associate {
                it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
            } ?: return null
            val host = q["host"].orEmpty()
            val port = q["port"]?.toIntOrNull() ?: LINK_PORT
            val code = q["code"].orEmpty()
            // A link must never point the phone at a server on the internet: only home-network addresses are accepted
            if (!isHomeNetworkAddress(host) || port !in 1..65535 || code.length != 6 || !code.all(Char::isDigit)) return null
            return LinkInvite(host, port, code, q["name"].orEmpty().ifBlank { "your TV" }.take(60))
        }

        fun isHomeNetworkAddress(host: String): Boolean {
            val p = host.split('.')
            if (p.size != 4) return false
            val n = p.map { it.toIntOrNull() ?: return false }
            if (n.any { it !in 0..255 }) return false
            return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254) || n[0] == 127
        }
    }
}

/** QR codes, made with ZXing. */
object Qr {
    fun matrix(text: String): com.google.zxing.common.BitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
        mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 0,
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
        ),
    )
}
