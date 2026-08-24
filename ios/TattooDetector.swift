import CoreML
import CoreGraphics
import UIKit

/**
 Detector de tatuajes: YOLOX-Nano entrenado con las cajas de Rekognition y
 convertido a Core ML.

 El modelo devuelve la rejilla **sin decodificar**: meter la decodificacion
 dentro generaba una operacion que TFLite no sabe convertir, y tener dos
 decodificadores distintos en iOS y Android es justo lo que acaba divergiendo.
 Asi que la geometria se aplica aqui, igual que en Kotlin.
 */
final class TattooDetector {
  /// Lado del lienzo cuadrado que espera el modelo.
  static let inputSize = 416
  /// Niveles de la piramide de caracteristicas, en el orden en que el modelo concatena.
  private static let strides = [8, 16, 32]
  /// Relleno del lienzo, en gris medio: es el valor con el que se entreno.
  private static let padValue: UInt8 = 114

  private let model: MLModel

  init() throws {
    // Segun como se enlace el pod, el recurso acaba en el bundle del propio
    // modulo o en el de la app.
    let candidates = [Bundle(for: TattooDetector.self), Bundle.main]
    guard let url = candidates.compactMap({
      $0.url(forResource: "TattooDetector", withExtension: "mlmodelc")
    }).first else {
      throw TattooDetectionError.modelMissing
    }

    let configuration = MLModelConfiguration()
    configuration.computeUnits = .all
    self.model = try MLModel(contentsOf: url, configuration: configuration)
  }

  // MARK: - Deteccion

  /// Devuelve las cajas normalizadas (0..1) sobre la imagen original.
  func detect(image: UIImage, minConfidence: Double, iouThreshold: Double) throws -> [Detection] {
    // `size` de UIImage ya viene con la orientacion aplicada.
    let width = image.size.width * image.scale
    let height = image.size.height * image.scale
    guard width > 0, height > 0 else {
      throw TattooDetectionError.invalidImage
    }

    let scale = min(CGFloat(Self.inputSize) / height, CGFloat(Self.inputSize) / width)
    let input = try makeInput(from: image, scale: scale)
    let provider = try MLDictionaryFeatureProvider(dictionary: ["images": MLFeatureValue(multiArray: input)])
    let output = try model.prediction(from: provider)

    guard let raw = output.featureValue(for: "output")?.multiArrayValue else {
      throw TattooDetectionError.unexpectedOutput
    }

    let candidates = decode(raw, minConfidence: minConfidence)
    let kept = nonMaximumSuppression(candidates, iouThreshold: iouThreshold)

    // De pixeles del lienzo a fraccion de la imagen original.
    return kept.map { detection in
      Detection(
        x: detection.x / scale / width,
        y: detection.y / scale / height,
        width: detection.width / scale / width,
        height: detection.height / scale / height,
        confidence: detection.confidence
      )
    }
  }

  // MARK: - Preprocesado

  /**
   Construye el tensor [1, 3, 416, 416] que espera el modelo.

   El entrenamiento uso OpenCV, asi que el orden de canales es **BGR** y los
   valores van de 0 a 255 sin normalizar. La imagen se pega arriba a la
   izquierda sobre un lienzo gris, no centrada.
   */
  private func makeInput(from image: UIImage, scale: CGFloat) throws -> MLMultiArray {
    let side = Self.inputSize
    let scaledWidth = CGFloat(image.size.width * image.scale) * scale
    let scaledHeight = CGFloat(image.size.height * image.scale) * scale

    // Se dibuja con UIKit y no con CoreGraphics a pelo porque `UIImage.draw`
    // aplica la orientacion EXIF por su cuenta: una foto tomada en vertical
    // llegaria girada al modelo si se usara su CGImage directamente.
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = 1
    format.opaque = true

    let canvas = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { context in
      // Gris 114, el mismo relleno con el que se entreno el modelo.
      let gray = CGFloat(Self.padValue) / 255
      context.cgContext.setFillColor(red: gray, green: gray, blue: gray, alpha: 1)
      context.cgContext.fill(CGRect(x: 0, y: 0, width: side, height: side))

      // Anclada arriba a la izquierda, como hace el preprocesado de YOLOX.
      image.draw(in: CGRect(x: 0, y: 0, width: scaledWidth, height: scaledHeight))
    }

    guard let rendered = canvas.cgImage else {
      throw TattooDetectionError.invalidImage
    }

    // Sin canal alfa: con un contexto premultiplicado, el relleno gris se
    // mezclaria con lo dibujado y el modelo recibiria otra imagen.
    let bytesPerRow = side * 4
    let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: side * bytesPerRow)
    defer { buffer.deallocate() }
    buffer.initialize(repeating: Self.padValue, count: side * bytesPerRow)

    guard let context = CGContext(
      data: buffer,
      width: side,
      height: side,
      bitsPerComponent: 8,
      bytesPerRow: bytesPerRow,
      space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else {
      throw TattooDetectionError.invalidImage
    }

    context.draw(rendered, in: CGRect(x: 0, y: 0, width: side, height: side))

    let array = try MLMultiArray(shape: [1, 3, NSNumber(value: side), NSNumber(value: side)], dataType: .float32)
    let pointer = array.dataPointer.bindMemory(to: Float32.self, capacity: array.count)

    let planeSize = side * side
    for row in 0..<side {
      for column in 0..<side {
        let source = row * bytesPerRow + column * 4
        let destination = row * side + column

        // BGR y valores de 0 a 255 sin normalizar: es lo que produce cv2.imread,
        // con lo que se entreno.
        pointer[destination] = Float32(buffer[source + 2])
        pointer[planeSize + destination] = Float32(buffer[source + 1])
        pointer[2 * planeSize + destination] = Float32(buffer[source])
      }
    }

    return array
  }

  // MARK: - Decodificacion

  /**
   Aplica la geometria de YOLOX: cada celda predice un desplazamiento respecto a
   su posicion en la rejilla y un tamaño en logaritmo.
   */
  private func decode(_ raw: MLMultiArray, minConfidence: Double) -> [Detection] {
    let pointer = raw.dataPointer.bindMemory(to: Float32.self, capacity: raw.count)
    let valuesPerBox = 6

    var detections: [Detection] = []
    var index = 0

    for stride in Self.strides {
      let cells = Self.inputSize / stride

      for row in 0..<cells {
        for column in 0..<cells {
          let base = index * valuesPerBox
          index += 1

          // El head ya aplica la sigmoide a estos dos valores en inferencia;
          // volver a aplicarla aqui aplastaria todas las puntuaciones.
          let objectness = Double(pointer[base + 4])
          let classScore = Double(pointer[base + 5])
          let confidence = objectness * classScore

          if confidence < minConfidence {
            continue
          }

          let centerX = (Double(pointer[base]) + Double(column)) * Double(stride)
          let centerY = (Double(pointer[base + 1]) + Double(row)) * Double(stride)
          let boxWidth = exp(Double(pointer[base + 2])) * Double(stride)
          let boxHeight = exp(Double(pointer[base + 3])) * Double(stride)

          detections.append(
            Detection(
              x: CGFloat(centerX - boxWidth / 2),
              y: CGFloat(centerY - boxHeight / 2),
              width: CGFloat(boxWidth),
              height: CGFloat(boxHeight),
              confidence: confidence
            )
          )
        }
      }
    }

    return detections
  }

  // MARK: - NMS

  /**
   Supresion de no maximos, sin distinguir clase: solo hay una.
   */
  private func nonMaximumSuppression(_ detections: [Detection], iouThreshold: Double) -> [Detection] {
    let sorted = detections.sorted { $0.confidence > $1.confidence }
    var kept: [Detection] = []

    for candidate in sorted {
      var overlaps = false
      for existing in kept where intersectionOverUnion(candidate, existing) > iouThreshold {
        overlaps = true
        break
      }
      if !overlaps {
        kept.append(candidate)
      }
    }

    return kept
  }

  private func intersectionOverUnion(_ a: Detection, _ b: Detection) -> Double {
    let left = max(a.x, b.x)
    let top = max(a.y, b.y)
    let right = min(a.x + a.width, b.x + b.width)
    let bottom = min(a.y + a.height, b.y + b.height)

    if right <= left || bottom <= top {
      return 0
    }

    let intersection = Double((right - left) * (bottom - top))
    let areaA = Double(a.width * a.height)
    let areaB = Double(b.width * b.height)
    return intersection / (areaA + areaB - intersection)
  }
}

struct Detection {
  let x: CGFloat
  let y: CGFloat
  let width: CGFloat
  let height: CGFloat
  let confidence: Double
}

enum TattooDetectionError: Error, LocalizedError {
  case modelMissing
  case invalidImage
  case unexpectedOutput

  var errorDescription: String? {
    switch self {
    case .modelMissing: return "El modelo de tatuajes no esta en el bundle"
    case .invalidImage: return "No se pudo preparar la imagen para el modelo"
    case .unexpectedOutput: return "El modelo devolvio una salida inesperada"
    }
  }
}
