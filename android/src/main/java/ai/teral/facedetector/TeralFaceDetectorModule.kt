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
 * Ancho de la banda ocular como multiplo de la distancia entre ojos.
 *
 * Un ojo mide en torno a 0.35 de esa distancia, asi que cubrir ambos pide 1.35 y
 * el resto es margen. Con valores mayores la banda se come casi todo el ancho de
 * la cara y deja de tener sentido frente a taparla entera.
 */
private const val EYE_BAND_WIDTH_FACTOR = 1.7f

/** Alto de la banda ocular, en la misma unidad. Un ojo alto mide unos 0.2. */
private const val EYE_BAND_HEIGHT_FACTOR = 0.5f

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

    AsyncFunction("detectTattoos") { uri: String, options: TattooDetectionOptions? ->
      detectTattoos(uri, options ?: TattooDetectionOptions())
    }

    Function("isTattooDetectionAvailable") {
      runCatching { sharedTattooDetector() }.isSuccess
    }

    OnDestroy {
      tattooDetector?.close()
      tattooDetector = null
    }
  }

  // MARK: - Tatuajes

  /** Cargar el modelo cuesta; se reutiliza entre llamadas. */
  private var tattooDetector: TattooDetector? = null

  private fun sharedTattooDetector(): TattooDetector {
    return tattooDetector ?: TattooDetector(context).also { tattooDetector = it }
  }

  private fun detectTattoos(uri: String, options: TattooDetectionOptions): Map<String, Any> {
    val (imageWidth, imageHeight) = try {
      ImageLoader.readSize(context, uri)
    } catch (error: ImageLoadException) {
      throw TattooDetectionFailedException(error.message ?: "No se pudo leer la imagen")
    }

    val bitmap = try {
      ImageLoader.load(context, uri, options.maxDetectionSize)
    } catch (error: ImageLoadException) {
      throw TattooDetectionFailedException(error.message ?: "No se pudo cargar la imagen")
    }

    return try {
      val detections = sharedTattooDetector().detect(
        bitmap,
        options.minConfidence.toFloat(),
        options.iouThreshold.toFloat()
      )

      mapOf(
        "imageWidth" to imageWidth,
        "imageHeight" to imageHeight,
        "tattoos" to detections.map { detection ->
          val left = detection.x.coerceIn(0f, 1f)
          val top = detection.y.coerceIn(0f, 1f)
          mapOf(
            "x" to left,
            "y" to top,
            "width" to ((detection.x + detection.width).coerceIn(0f, 1f) - left).coerceAtLeast(0f),
            "height" to ((detection.y + detection.height).coerceIn(0f, 1f) - top).coerceAtLeast(0f),
            "confidence" to detection.confidence
          )
        }
      )
    } finally {
      bitmap.recycle()
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
        .map {
          toNormalizedRect(
            it,
            detectable.width.toFloat(),
            detectable.height.toFloat(),
            options.region,
            options.eyeBandScale.toFloat()
          )
        }

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
   * La API nativa no da un rectangulo de cara, sino el punto medio entre los ojos
   * y la distancia entre ellos. La banda ocular sale directamente de ahi; la cara
   * entera hay que construirla, con los factores que usa Signal (1.4 de ancho y
   * 0.4 de desplazamiento hacia abajo) para encuadrarla completa.
   */
  private fun toNormalizedRect(
    face: FaceDetector.Face,
    width: Float,
    height: Float,
    region: FaceRegion,
    eyeBandScale: Float
  ): Map<String, Any> {
    val midPoint = PointF().also { face.getMidPoint(it) }
    val eyesDistance = face.eyesDistance()

    val left: Float
    val top: Float
    val right: Float
    val bottom: Float

    if (region == FaceRegion.EYES) {
      val halfBandWidth = eyesDistance * EYE_BAND_WIDTH_FACTOR * eyeBandScale / 2f
      val halfBandHeight = eyesDistance * EYE_BAND_HEIGHT_FACTOR * eyeBandScale / 2f

      left = (midPoint.x - halfBandWidth) / width
      top = (midPoint.y - halfBandHeight) / height
      right = (midPoint.x + halfBandWidth) / width
      bottom = (midPoint.y + halfBandHeight) / height
    } else {
      val halfWidth = eyesDistance * 1.4f
      val yOffset = eyesDistance * 0.4f

      left = (midPoint.x - halfWidth) / width
      top = (midPoint.y - halfWidth + yOffset) / height
      right = (midPoint.x + halfWidth) / width
      bottom = (midPoint.y + halfWidth + yOffset) / height
    }

    val clampedLeft = left.coerceIn(0f, 1f)
    val clampedTop = top.coerceIn(0f, 1f)

    return mapOf(
      "x" to clampedLeft,
      "y" to clampedTop,
      "width" to (right.coerceIn(0f, 1f) - clampedLeft).coerceAtLeast(0f),
      "height" to (bottom.coerceIn(0f, 1f) - clampedTop).coerceAtLeast(0f),
      "confidence" to face.confidence(),
      "region" to region.value,
      // La API de deteccion de Android no reporta la pose de la cara, asi que la
      // banda va siempre horizontal. En iOS, donde hay landmarks, si se inclina.
      "angle" to 0f
    )
  }
}

internal class MissingContextException :
  CodedException("El contexto de React no esta disponible")

internal class FaceDetectionFailedException(reason: String) :
  CodedException("Fallo la deteccion de caras: $reason")

internal class TattooDetectionFailedException(reason: String) :
  CodedException("Fallo la deteccion de tatuajes: $reason")
