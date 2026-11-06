package com.pawly.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pawly.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Uploads user-selected images directly to Cloudinary using an unsigned preset.
 * No Cloudinary API secret is ever stored in the Android application.
 */
internal class CloudinaryImageUploader(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun upload(uri: Uri, folder: String, ownerUid: String): String = withContext(Dispatchers.IO) {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
        require(cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
            "Configura CLOUDINARY_CLOUD_NAME y CLOUDINARY_UPLOAD_PRESET en local.properties para subir fotografías."
        }
        require(cloudName.matches(Regex("[A-Za-z0-9_-]+"))) { "El Cloud name de Cloudinary no es válido." }
        require(folder in setOf("profile-photos", "dog-photos", "pet-photos", "post-photos", "message-photos")) {
            "La carpeta de imágenes solicitada no está permitida."
        }

        val imageBytes = compressJpeg(uri)
        require(imageBytes.size <= 8 * 1024 * 1024) { "La fotografía supera el límite de 8 MB después de comprimirla." }
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart("folder", "pawly/$folder/$ownerUid")
            .addFormDataPart("file", "pawly-image.jpg", imageBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(message.ifBlank { "Cloudinary rechazó la fotografía (HTTP ${response.code})." })
            }
            JSONObject(responseText).optString("secure_url").takeIf { it.startsWith("https://") }
                ?: error("Cloudinary no ha devuelto una URL segura para la imagen.")
        }
    }

    private fun compressJpeg(uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: error("No se ha podido leer la fotografía seleccionada.")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo seleccionado no es una imagen válida." }

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 2048) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("No se ha podido procesar la fotografía.")

        val longestSide = max(bitmap.width, bitmap.height)
        val scale = minOf(1f, 1600f / longestSide.toFloat())
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        return try {
            ByteArrayOutputStream().use { output ->
                check(resized.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    "No se ha podido comprimir la fotografía."
                }
                output.toByteArray()
            }
        } finally {
            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()
        }
    }
}
