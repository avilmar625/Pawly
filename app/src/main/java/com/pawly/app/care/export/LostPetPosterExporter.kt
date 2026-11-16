package com.pawly.app.care.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Creates a locally rendered, shareable JPEG poster for a lost pet alert. */
internal object LostPetPosterExporter {
    fun share(context: Context, photoUri: Uri?, petName: String, breed: String, area: String, lastSeen: String, description: String, contact: String) {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF6F2E9.toInt())
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), 290f, 0xFF173E33.toInt(), 0xFF2E725B.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 290f, headerPaint)
        canvas.drawText("SE BUSCA", 64f, 105f, paint(76f, 0xFFFFFFFF.toInt(), true))
        canvas.drawText(petName.trim().take(24).ifBlank { "Mascota perdida" }, 64f, 205f, paint(56f, 0xFFFFFFFF.toInt(), true))
        canvas.drawText("AYÚDANOS A ENCONTRARLA", 66f, 255f, paint(28f, 0xFFFFD99A.toInt(), true))

        val photo = photoUri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream) } }.getOrNull() }
        val photoRect = RectF(64f, 330f, 1016f, 820f)
        canvas.drawRoundRect(photoRect, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0D8C9.toInt() })
        if (photo != null) {
            centerCrop(canvas, photo, photoRect)
            photo.recycle()
        } else {
            val placeholder = paint(46f, 0xFF6A776F.toInt(), true).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("FOTO DE TU MASCOTA", width / 2f, 585f, placeholder)
        }

        var y = 885f
        val bodyPaint = paint(30f, 0xFF303A35.toInt(), false)
        val labelPaint = paint(23f, 0xFF6A776F.toInt(), true)
        fun section(label: String, value: String) {
            if (value.isBlank()) return
            canvas.drawText(label.uppercase(), 68f, y, labelPaint)
            y += 42f
            y = wrapped(canvas, value.trim(), 68f, y, 940f, bodyPaint, 38f) + 22f
        }
        section("Zona", area)
        section("Última vez vista", lastSeen)
        section("Especie o raza", breed)
        section("Señas", description)
        if (contact.isNotBlank()) {
            canvas.drawRoundRect(RectF(56f, y + 4f, 1024f, minOf(y + 126f, 1300f)), 26f, 26f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDECE3.toInt() })
            canvas.drawText("CONTACTO", 82f, y + 45f, labelPaint)
            wrapped(canvas, contact.trim(), 82f, y + 88f, 900f, paint(32f, 0xFF1D4035.toInt(), true), 42f)
        }
        canvas.drawText("Publicado con Pawly · Comparte este cartel", 68f, 1320f, paint(21f, 0xFF69756F.toInt(), false))

        val directory = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        val safeName = petName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "mascota" }
        val output = File(directory, "pawly-se-busca-$safeName-${System.currentTimeMillis()}.jpg")
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Se busca a $petName")
            putExtra(Intent.EXTRA_TEXT, "Ayúdanos a encontrar a $petName. Zona: $area")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir cartel de mascota perdida"))
    }

    private fun paint(size: Float, color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun centerCrop(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val scale = max(destination.width() / bitmap.width, destination.height() / bitmap.height)
        val sourceWidth = destination.width() / scale
        val sourceHeight = destination.height() / scale
        val left = ((bitmap.width - sourceWidth) / 2f).toInt()
        val top = ((bitmap.height - sourceHeight) / 2f).toInt()
        val source = Rect(left, top, (left + sourceWidth).toInt(), (top + sourceHeight).toInt())
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
    }

    private fun wrapped(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float): Float {
        var y = startY
        var line = ""
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }
}
