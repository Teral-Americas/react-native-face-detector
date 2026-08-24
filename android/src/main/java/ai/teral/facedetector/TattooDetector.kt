package ai.teral.facedetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Lado del lienzo cuadrado que espera el modelo. */
private const val INPUT_SIZE = 416

/** Niveles de la piramide, en el orden en que el modelo concatena sus salidas. */
private val STRIDES = intArrayOf(8, 16, 32)

/** Relleno del lienzo, en gris medio: es el valor con el que se entreno. */
private const val PAD_VALUE = 114

/** Cajas que emite el modelo: 52x52 + 26x26 + 13x13. */
private const val NUM_BOXES = 3549

/** cx, cy, w, h, objectness y puntuacion de la unica clase. */
private const val VALUES_PER_BOX = 6

private const val MODEL_ASSET = "tattoo_detector.tflite"

/**
 * Detector de tatuajes: YOLOX-Nano entrenado con las cajas de Rekognition y
 * convertido a TFLite.
 *
 * El modelo devuelve la rejilla **sin decodificar**. La geometria se aplica aqui,
 * con la misma aritmetica que en Swift: tener dos decodificadores distintos por
 * plataforma es justo lo que acaba divergiendo sin que nadie lo note.
 */
internal class TattooDetector(context: Context) : Closeable {

  private val interpreter: Interpreter

  init {
    val descriptor = context.assets.openFd(MODEL_ASSET)
    val model = descriptor.use {
      it.createInputStream().channel.use { channel ->
        channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
      }
    }

    interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 4 })
  }

  override fun close() {
    interpreter.close()
  }

  /** Devuelve las cajas normalizadas (0..1) sobre la imagen recibida. */
  fun detect(source: Bitmap, minConfidence: Float, iouThreshold: Float): List<Detection> {
    val scale = minOf(
      INPUT_SIZE.toFloat() / source.height,
      INPUT_SIZE.toFloat() / source.width
    )

    val input = prepareInput(source, scale)
    val output = Array(1) { Array(NUM_BOXES) { FloatArray(VALUES_PER_BOX) } }
    interpreter.run(input, output)

    val candidates = decode(output[0], minConfidence)
    val kept = nonMaximumSuppression(candidates, iouThreshold)

    // De pixeles del lienzo a fraccion de la imagen original.
    return kept.map { detection ->
      Detection(
        x = detection.x / scale / source.width,
        y = detection.y / scale / source.height,
        width = detection.width / scale / source.width,
        height = detection.height / scale / source.height,
        confidence = detection.confidence
      )
    }
  }

  /**
   * Construye el tensor [1, 3, 416, 416].
   *
   * El entrenamiento uso OpenCV, asi que los canales van en orden **BGR** y los
   * valores de 0 a 255 sin normalizar. La imagen se pega arriba a la izquierda
   * sobre un lienzo gris, no centrada.
   */
  private fun prepareInput(source: Bitmap, scale: Float): ByteBuffer {
    val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)

    val canvasBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBitmap)
    canvas.drawColor(Color.rgb(PAD_VALUE, PAD_VALUE, PAD_VALUE))
    canvas.drawBitmap(
      source,
      Rect(0, 0, source.width, source.height),
      RectF(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat()),
      null
    )

    val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    canvasBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
    canvasBitmap.recycle()

    val buffer = ByteBuffer.allocateDirect(4 * 3 * INPUT_SIZE * INPUT_SIZE).apply {
      order(ByteOrder.nativeOrder())
    }

    // El tensor es NCHW: primero el plano azul entero, luego el verde y el rojo.
    for (channel in 0 until 3) {
      for (pixel in pixels) {
        val value = when (channel) {
          0 -> pixel and 0xFF          // azul
          1 -> (pixel shr 8) and 0xFF  // verde
          else -> (pixel shr 16) and 0xFF
        }
        buffer.putFloat(value.toFloat())
      }
    }

    buffer.rewind()
    return buffer
  }

  /**
   * Aplica la geometria de YOLOX: cada celda predice un desplazamiento respecto a
   * su posicion en la rejilla y un tamaño en logaritmo.
   */
  private fun decode(output: Array<FloatArray>, minConfidence: Float): List<Detection> {
    val detections = mutableListOf<Detection>()
    var index = 0

    for (stride in STRIDES) {
      val cells = INPUT_SIZE / stride

      for (row in 0 until cells) {
        for (column in 0 until cells) {
          val box = output[index]
          index++

          // El head ya aplica la sigmoide a estos dos valores en inferencia.
          val confidence = box[4] * box[5]
          if (confidence < minConfidence) {
            continue
          }

          val centerX = (box[0] + column) * stride
          val centerY = (box[1] + row) * stride
          val boxWidth = Math.exp(box[2].toDouble()).toFloat() * stride
          val boxHeight = Math.exp(box[3].toDouble()).toFloat() * stride

          detections.add(
            Detection(
              x = centerX - boxWidth / 2,
              y = centerY - boxHeight / 2,
              width = boxWidth,
              height = boxHeight,
              confidence = confidence
            )
          )
        }
      }
    }

    return detections
  }

  /** Supresion de no maximos, sin distinguir clase: solo hay una. */
  private fun nonMaximumSuppression(
    detections: List<Detection>,
    iouThreshold: Float
  ): List<Detection> {
    val sorted = detections.sortedByDescending { it.confidence }
    val kept = mutableListOf<Detection>()

    for (candidate in sorted) {
      if (kept.none { intersectionOverUnion(candidate, it) > iouThreshold }) {
        kept.add(candidate)
      }
    }

    return kept
  }

  private fun intersectionOverUnion(a: Detection, b: Detection): Float {
    val left = maxOf(a.x, b.x)
    val top = maxOf(a.y, b.y)
    val right = minOf(a.x + a.width, b.x + b.width)
    val bottom = minOf(a.y + a.height, b.y + b.height)

    if (right <= left || bottom <= top) {
      return 0f
    }

    val intersection = (right - left) * (bottom - top)
    return intersection / (a.width * a.height + b.width * b.height - intersection)
  }
}

internal data class Detection(
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val confidence: Float
)
