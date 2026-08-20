package ai.teral.facedetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/**
 * Carga la imagen de [uri] reducida a [maxSize] en su lado mayor y con la
 * rotacion EXIF ya aplicada, de modo que las coordenadas que devolvamos
 * coincidan con la imagen que la app pinta en pantalla.
 */
internal object ImageLoader {

  fun load(context: Context, uri: String, maxSize: Int): Bitmap {
    val parsed = parseUri(uri)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream(context, parsed).use { BitmapFactory.decodeStream(it, null, bounds) }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      throw ImageLoadException("No se pudo leer la imagen: $uri")
    }

    val decodeOptions = BitmapFactory.Options().apply {
      inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val decoded = openStream(context, parsed).use { BitmapFactory.decodeStream(it, null, decodeOptions) }
      ?: throw ImageLoadException("No se pudo decodificar la imagen: $uri")

    val rotation = openStream(context, parsed).use { readRotation(it) }
    return applyRotation(decoded, rotation)
  }

  /**
   * Dimensiones reales de la imagen (con la rotacion EXIF aplicada), en pixeles.
   * Es lo que la app necesita para mapear las coordenadas normalizadas al original.
   */
  fun readSize(context: Context, uri: String): Pair<Int, Int> {
    val parsed = parseUri(uri)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream(context, parsed).use { BitmapFactory.decodeStream(it, null, bounds) }

    val rotation = openStream(context, parsed).use { readRotation(it) }
    return if (rotation == 90 || rotation == 270) {
      bounds.outHeight to bounds.outWidth
    } else {
      bounds.outWidth to bounds.outHeight
    }
  }

  private fun parseUri(uri: String): Uri {
    return if (uri.startsWith("/")) Uri.fromFile(File(uri)) else Uri.parse(uri)
  }

  private fun openStream(context: Context, uri: Uri): InputStream {
    return context.contentResolver.openInputStream(uri)
      ?: throw ImageLoadException("No se pudo abrir la imagen: $uri")
  }

  private fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
    if (maxSize <= 0) return 1

    var sampleSize = 1
    while (maxOf(width, height) / (sampleSize * 2) >= maxSize) {
      sampleSize *= 2
    }
    return sampleSize
  }

  private fun readRotation(stream: InputStream): Int {
    val orientation = ExifInterface(stream).getAttributeInt(
      ExifInterface.TAG_ORIENTATION,
      ExifInterface.ORIENTATION_NORMAL
    )

    return when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90
      ExifInterface.ORIENTATION_ROTATE_180 -> 180
      ExifInterface.ORIENTATION_ROTATE_270 -> 270
      else -> 0
    }
  }

  private fun applyRotation(bitmap: Bitmap, rotation: Int): Bitmap {
    if (rotation == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) {
      bitmap.recycle()
    }
    return rotated
  }
}

internal class ImageLoadException(message: String) : RuntimeException(message)
