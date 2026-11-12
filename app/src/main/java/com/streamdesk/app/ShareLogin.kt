package com.streamdesk.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.io.File

/**
 * An account passed from one phone to another as a QR code (no link): streamdesk://login?d=<the login>&n=<its name>.
 * The friend's Livio reads it from a picture in the gallery, or the phone's own camera opens it in the app.
 */
object LoginQr {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun encode(login: XtreamLogin, name: String?): String {
        val data = Base64.encodeToString(login.toJson().toString().toByteArray(), FLAGS)
        val n = name?.takeIf { it.isNotBlank() }?.let { "&n=" + Base64.encodeToString(it.toByteArray(), FLAGS) }.orEmpty()
        return "streamdesk://login?d=$data$n"
    }

    /** The login and its name from a QR code's text; null when it isn't a Livio account. */
    fun parse(text: String?): Pair<XtreamLogin, String?>? {
        val uri = runCatching { Uri.parse(text?.trim() ?: return null) }.getOrNull() ?: return null
        if (uri.scheme != "streamdesk" || uri.host != "login") return null
        return runCatching {
            val login = XtreamLogin.fromJson(JSONObject(String(Base64.decode(uri.getQueryParameter("d"), FLAGS))))
            if (login.server.isBlank() || login.username.isBlank() || login.password.isBlank()) return null
            val name = uri.getQueryParameter("n")?.let { String(Base64.decode(it, FLAGS)) }?.takeIf { it.isNotBlank() }
            login to name
        }.getOrNull()
    }

    /** A black-on-white QR code, with a quiet margin and room in the middle for the app's logo. */
    fun bitmap(text: String, size: Int = 720): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 2)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { if (m.get(it % size, it / size)) AndroidColor.BLACK else AndroidColor.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Finds a QR code in a picture (a screenshot or a photo); its text, or null. */
    fun decode(picture: Bitmap): String? {
        val w = picture.width
        val h = picture.height
        val pixels = IntArray(w * h).also { picture.getPixels(it, 0, w, 0, 0, w, h) }
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        return runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels))), hints).text }.getOrNull()
    }

    /** Reads a picture from the gallery, made smaller first so a big photo doesn't run out of memory. */
    fun readPicture(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun card(context: Context, text: String): Bitmap {
        val qr = bitmap(text, 900)
        val logo = runCatching {
            val d = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.splash_icon)!!
            val b = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            c.drawColor(0xFF0E1113.toInt())
            d.setBounds(-60, -60, 280, 280)
            d.draw(c)
            b
        }.getOrNull()
        if (logo != null) {
            val c = android.graphics.Canvas(qr)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
            val l = (900 - 220) / 2f
            c.drawRoundRect(l - 12, l - 12, l + 232, l + 232, 40f, 40f, paint)
            c.drawBitmap(logo, l, l, null)
        }
        return qr
    }

    /** Saves the QR code to the gallery (Pictures/Livio); false if it couldn't. */
    fun save(context: Context, text: String, name: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Livio - ${name.replace(Regex("[^A-Za-z0-9 ._-]"), "")}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Livio")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { card(context, text).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
    }

    /** Opens the share sheet (WhatsApp, …) with the QR code as a picture. */
    fun send(context: Context, text: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Livio account.png")
        file.outputStream().use { card(context, text).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Send the QR code"))
    }
}

/** Your phone: the account as a QR code, to save or send to a friend's phone. */
@Composable
fun ShareLoginDialog(source: PlaylistSource, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val login = source.xtream ?: return
    val text = remember(source) { LoginQr.encode(login, source.name) }
    val qr = remember(text) { LoginQr.bitmap(text).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this account", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "On the other phone: Livio → + Add playlist → QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Box(Modifier.size(232.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp), contentAlignment = Alignment.Center) {
                    Image(qr, contentDescription = "QR code of the account")
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(3.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E1113)), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.splash_icon), contentDescription = null, modifier = Modifier.size(64.dp))
                        }
                    }
                }
                Text("${source.name} · user ${login.username}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(onClick = {
                            onDone(if (LoginQr.save(context, text, source.name)) "Saved to Pictures › Livio." else "Couldn't save the picture.")
                        }) { Text("Save picture") }
                    }
                    Button(
                        onClick = { runCatching { LoginQr.send(context, text) }.onFailure { onDone("Couldn't send the picture.") } },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = FocusInk),
                    ) { Text("Send picture") }
                }
                Text(
                    "🔒 The QR code holds the password. Send it only to the person whose account it is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB4A9),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A1F1C)).padding(10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** The friend's phone, after reading the QR code: shows the account and adds it with one tap. */
@Composable
fun AddSharedLoginDialog(login: XtreamLogin, name: String?, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val host = remember(login) { runCatching { java.net.URI(login.base).let { it.host + if (it.port > 0) ":${it.port}" else "" } }.getOrDefault(login.server) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add this account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull(name?.let { "Name" to it }, "Server" to host, "Username" to login.username, "Password" to "••••••••").forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = FocusInk)) { Text("Add account") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
