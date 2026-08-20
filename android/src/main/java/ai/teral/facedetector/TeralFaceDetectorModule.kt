package ai.teral.facedetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.media.FaceDetector
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/** Tope de la API nativa: pedir mas no aporta y reserva memoria de mas. */
private const val MAX_FACES = 20

/**
 * Detecta caras y devuelve sus bounding boxes en coordenadas normalizadas (0..1)
 * sobre la imagen ya orientada segun su EXIF.
 *
 * El modulo no modifica la imagen: quien censura es la app, dibujando en Skia con
 * esas coordenadas. Asi el usuario puede mover, borrar o añadir manchas antes de
 * exportar, y el nativo se limita a lo unico que JS no puede hacer.
 *
 * Usa `android.media.FaceDetector`, que es parte del sistema: funciona sin red,
 * sin Google Play Services y sin modelo empaquetado. A cambio detecta caras
 * frontales y poco mas; si hace falta precisión, este es el punto donde se
 * sustituye por ML Kit sin tocar la API.
 */
class TeralFaceDetectorModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw MissingContextException()

  override fun definition() = ModuleDefinition {
    Name("TeralFaceDetector")

    AsyncFunction("detectFaces") { uri: String, options: FaceDetectionOptions? ->
      detectFaces(uri, options ?: FaceDetectionOptions())
    }
  }

  private fun detectFaces(uri: String, options: FaceDetectionOptions): Map<String, Any> {
    val (imageWidth, imageHeight) = try {
      ImageLoader.readSize(context, uri)
    } catch (error: ImageLoadException) {
      throw FaceDetectionFailedException(error.message ?: "No se pudo leer la imagen")
    }

    val source = try {
      ImageLoader.load(context, uri, options.maxDetectionSize)
    } catch (error: ImageLoadException) {
      throw FaceDetectionFailedException(error.message ?: "No se pudo cargar la imagen")
    }

    val detectable = toDetectableBitmap(source)

    return try {
      val detector = FaceDetector(detectable.width, detectable.height, MAX_FACES)
      val found = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
      val count = detector.findFaces(detectable, found)

      val faces = (0 until count)
        .mapNotNull { found[it] }
        .filter { it.confidence() >= options.minConfidence }
        .map { toNormalizedRect(it, detectable.width.toFloat(), detectable.height.toFloat()) }

      mapOf(
        "imageWidth" to imageWidth,
        "imageHeight" to imageHeight,
        "faces" to faces
      )
    } finally {
      if (detectable != source) {
        detectable.recycle()
      }
      source.recycle()
    }
  }

  /**
   * `android.media.FaceDetector` solo acepta bitmaps RGB_565 de ancho par; con
   * cualquier otro formato devuelve cero caras sin avisar.
   */
  private fun toDetectableBitmap(source: Bitmap): Bitmap {
    val evenWidth = source.width and 0x1.inv()
    if (source.config == Bitmap.Config.RGB_565 && source.width == evenWidth) {
      return source
    }

    if (evenWidth <= 0 || source.height <= 0) {
      throw FaceDetectionFailedException("La imagen es demasiado pequeña para detectar caras")
    }

    val converted = Bitmap.createBitmap(evenWidth, source.height, Bitmap.Config.RGB_565)
    Canvas(converted).drawBitmap(source, 0f, 0f, null)
    return converted
  }

  /**
   * La API nativa no da un rectangulo, sino el punto medio entre los ojos y la
   * distancia entre ellos. Los factores (1.4 de ancho, 0.4 de desplazamiento
   * hacia abajo) son los que usa Signal: encuadran la cara entera, no solo los ojos.
   */
  private fun toNormalizedRect(face: FaceDetector.Face, width: Float, height: Float): Map<String, Any> {
    val midPoint = PointF().also { face.getMidPoint(it) }
    val halfWidth = face.eyesDistance() * 1.4f
    val yOffset = face.eyesDistance() * 0.4f

    val left = (midPoint.x - halfWidth) / width
    val top = (midPoint.y - halfWidth + yOffset) / height
    val right = (midPoint.x + halfWidth) / width
    val bottom = (midPoint.y + halfWidth + yOffset) / height

    val clampedLeft = left.coerceIn(0f, 1f)
    val clampedTop = top.coerceIn(0f, 1f)

    return mapOf(
      "x" to clampedLeft,
      "y" to clampedTop,
      "width" to (right.coerceIn(0f, 1f) - clampedLeft).coerceAtLeast(0f),
      "height" to (bottom.coerceIn(0f, 1f) - clampedTop).coerceAtLeast(0f),
      "confidence" to face.confidence()
    )
  }
}

internal class MissingContextException :
  CodedException("El contexto de React no esta disponible")

internal class FaceDetectionFailedException(reason: String) :
  CodedException("Fallo la deteccion de caras: $reason")
