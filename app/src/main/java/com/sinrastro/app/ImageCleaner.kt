package com.sinrastro.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.util.Locale
import kotlin.math.ceil

data class CleanResult(
    val uri: Uri,
    val resizedForMemory: Boolean
)

/**
 * Creates a new bitmap file rather than copying the source file. Bitmap.compress writes
 * only image pixels, so EXIF, GPS, camera/device details, comments and embedded metadata
 * from the original file are not carried into the copy.
 */
object ImageCleaner {
    private const val OUTPUT_FOLDER = "SinRastro"

    fun clean(context: Context, source: Uri): CleanResult {
        val resolver = context.contentResolver
        val sourceMime = resolver.getType(source)?.lowercase(Locale.ROOT).orEmpty()
        val isPng = sourceMime == "image/png"
        val isWebp = sourceMime == "image/webp"
        val outputMime = when {
            isPng -> "image/png"
            isWebp -> "image/webp"
            else -> "image/jpeg"
        }
        val extension = when (outputMime) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val outputName = availableName(context, extension)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(source)
            ?: throw IllegalArgumentException("No se pudo leer la imagen.")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("El archivo no es una imagen compatible.")
        }

        val sampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val orientation = readOrientation(context, source)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalArgumentException("No se pudo decodificar la imagen.")
        val outputBitmap = orient(decoded, orientation)
        if (outputBitmap !== decoded) {
            decoded.recycle()
        }

        val relativePath = "${Environment.DIRECTORY_PICTURES}/$OUTPUT_FOLDER"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Images.Media.MIME_TYPE, outputMime)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        var destination: Uri? = null
        try {
            destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear el archivo de salida.")
            val format = when {
                isPng -> Bitmap.CompressFormat.PNG
                isWebp && Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSLESS
                isWebp -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            resolver.openOutputStream(destination, "w")?.use { output ->
                if (!outputBitmap.compress(format, 96, output)) {
                    throw IllegalStateException("No se pudo guardar la imagen.")
                }
                output.flush()
            } ?: throw IllegalStateException("No se pudo abrir el archivo de salida.")
            resolver.update(
                destination,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            return CleanResult(destination, sampleSize > 1)
        } catch (error: Exception) {
            destination?.let { runCatching { resolver.delete(it, null, null) } }
            throw error
        } finally {
            if (!outputBitmap.isRecycled) outputBitmap.recycle()
        }
    }

    fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "Imagen"
    }

    private fun readOrientation(context: Context, uri: Uri): Int =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun sampleSizeFor(width: Int, height: Int): Int {
        // Keep a pair of large decoded/rotated bitmaps comfortably below the app heap.
        val pixelBudget = minOf(18_000_000L, Runtime.getRuntime().maxMemory() / 16L)
            .coerceAtLeast(3_000_000L)
        var sample = 1
        while (ceil(width.toDouble() / sample) * ceil(height.toDouble() / sample) > pixelBudget) {
            sample *= 2
        }
        return sample
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun availableName(context: Context, extension: String): String {
        // Do not carry a potentially identifying date, location or device hint from the source name.
        val cleanedBase = "foto_limpia"
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$OUTPUT_FOLDER/"
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        fun exists(candidate: String): Boolean = runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(candidate, relativePath),
                null
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)

        var candidate = "${cleanedBase}_limpia$extension"
        var suffix = 2
        while (exists(candidate)) {
            candidate = "${cleanedBase}_limpia ($suffix)$extension"
            suffix++
        }
        return candidate
    }
}
