import ExpoModulesCore
import UIKit
import Vision

/**
 Detecta caras en una imagen y devuelve sus bounding boxes en coordenadas
 normalizadas (0..1) sobre la imagen ya orientada segun su EXIF.

 El modulo no toca la imagen: quien censura es la app, dibujando en Skia con
 esas coordenadas. Asi el usuario puede mover, borrar o añadir manchas antes de
 exportar, y el nativo se limita a lo unico que JS no puede hacer.

 La deteccion corre sobre una version reducida de la imagen (por defecto 1000px
 en el lado mayor): Vision encuentra las mismas caras y tarda una fraccion.
 */
public final class TeralFaceDetectorModule: Module {
  public func definition() -> ModuleDefinition {
    Name("TeralFaceDetector")

    AsyncFunction("detectFaces") { (uri: String, options: FaceDetectionOptions?) -> [String: Any] in
      return try self.detectFaces(uri: uri, options: options ?? FaceDetectionOptions())
    }

    AsyncFunction("detectTattoos") { (uri: String, options: TattooDetectionOptions?) -> [String: Any] in
      return try self.detectTattoos(uri: uri, options: options ?? TattooDetectionOptions())
    }

    Function("isTattooDetectionAvailable") {
      return (try? self.sharedTattooDetector()) != nil
    }
  }

  // MARK: - Tatuajes

  /// El modelo tarda en cargarse; se reutiliza entre llamadas.
  private var tattooDetector: TattooDetector?

  private func sharedTattooDetector() throws -> TattooDetector {
    if let existing = tattooDetector {
      return existing
    }
    let created = try TattooDetector()
    tattooDetector = created
    return created
  }

  private func detectTattoos(uri: String, options: TattooDetectionOptions) throws -> [String: Any] {
    let image = try loadImage(uri: uri)
    let detector = try sharedTattooDetector()

    let detections = try detector.detect(
      image: image,
      minConfidence: options.minConfidence,
      iouThreshold: options.iouThreshold
    )

    return [
      "imageWidth": Double(image.size.width * image.scale),
      "imageHeight": Double(image.size.height * image.scale),
      "tattoos": detections.map { detection in
        let clamped = self.clampToUnitSquare(
          CGRect(x: detection.x, y: detection.y, width: detection.width, height: detection.height)
        )
        return [
          "x": Double(clamped.origin.x),
          "y": Double(clamped.origin.y),
          "width": Double(clamped.width),
          "height": Double(clamped.height),
          "confidence": detection.confidence
        ]
      }
    ]
  }

  // MARK: - Deteccion

  private func detectFaces(uri: String, options: FaceDetectionOptions) throws -> [String: Any] {
    let image = try loadImage(uri: uri)

    // `size` de UIImage ya viene con la orientacion aplicada, asi que las
    // dimensiones que reportamos coinciden con lo que la app va a pintar.
    let imageWidth = image.size.width * image.scale
    let imageHeight = image.size.height * image.scale

    // El render normaliza la orientacion, por lo que Vision recibe siempre una
    // imagen `.up` y no hay que corregir ejes despues.
    guard let cgImage = uprightCGImage(from: image, maxSize: CGFloat(options.maxDetectionSize)) else {
      throw FaceDetectionError.imageRenderFailed(uri)
    }

    // Landmarks en vez de solo rectangulos: la misma pasada da la caja de la
    // cara y los puntos de los ojos, asi que la banda ocular no cuesta nada.
    let request = VNDetectFaceLandmarksRequest()
    let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])

    do {
      try handler.perform([request])
    } catch {
      throw FaceDetectionError.detectionFailed(error.localizedDescription)
    }

    let observations = request.results ?? []
    let faces = observations
      .filter { Double($0.confidence) >= options.minConfidence }
      .compactMap { observation -> [String: Any]? in
        // Vision usa origen abajo-izquierda; la app dibuja con origen arriba-izquierda.
        var faceRect = observation.boundingBox
        faceRect.origin.y = 1 - faceRect.origin.y - faceRect.height

        var rect = faceRect
        var angle: CGFloat = 0

        if options.region == .eyes {
          // Sin landmarks no hay banda que dibujar, y devolver la cara entera en
          // su lugar taparia mucho mas de lo que se pidio.
          guard let band = EyeBand.from(observation: observation, scale: CGFloat(options.eyeBandScale)) else {
            return nil
          }
          rect = band.rect
          angle = band.angle
        }

        let clamped = clampToUnitSquare(rect)

        return [
          "x": Double(clamped.origin.x),
          "y": Double(clamped.origin.y),
          "width": Double(clamped.width),
          "height": Double(clamped.height),
          "confidence": Double(observation.confidence),
          "region": options.region.rawValue,
          "angle": Double(angle)
        ]
      }

    return [
      "imageWidth": Double(imageWidth),
      "imageHeight": Double(imageHeight),
      "faces": faces
    ]
  }

  // MARK: - Carga de la imagen

  private func loadImage(uri: String) throws -> UIImage {
    let url: URL
    if uri.hasPrefix("/") {
      url = URL(fileURLWithPath: uri)
    } else if let parsed = URL(string: uri), parsed.scheme != nil {
      url = parsed
    } else {
      throw FaceDetectionError.unsupportedUri(uri)
    }

    guard let data = try? Data(contentsOf: url), let image = UIImage(data: data) else {
      throw FaceDetectionError.imageLoadFailed(uri)
    }

    return image
  }

  /**
   Devuelve la imagen redibujada con orientacion `.up` y reducida para que su
   lado mayor no pase de `maxSize`. Las imagenes mas pequeñas no se amplian.
   */
  private func uprightCGImage(from image: UIImage, maxSize: CGFloat) -> CGImage? {
    let pixelSize = CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
    let longestSide = max(pixelSize.width, pixelSize.height)
    let scale = longestSide > maxSize && maxSize > 0 ? maxSize / longestSide : 1

    let targetSize = CGSize(width: (pixelSize.width * scale).rounded(), height: (pixelSize.height * scale).rounded())
    guard targetSize.width >= 1, targetSize.height >= 1 else { return nil }

    let format = UIGraphicsImageRendererFormat.default()
    format.scale = 1
    format.opaque = true

    let rendered = UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
      image.draw(in: CGRect(origin: .zero, size: targetSize))
    }

    return rendered.cgImage
  }

  private func clampToUnitSquare(_ rect: CGRect) -> CGRect {
    let minX = max(0, rect.minX)
    let minY = max(0, rect.minY)
    let maxX = min(1, rect.maxX)
    let maxY = min(1, rect.maxY)

    return CGRect(x: minX, y: minY, width: max(0, maxX - minX), height: max(0, maxY - minY))
  }
}

// MARK: - Errores

private enum FaceDetectionError: Error, LocalizedError {
  case unsupportedUri(String)
  case imageLoadFailed(String)
  case imageRenderFailed(String)
  case detectionFailed(String)

  var errorDescription: String? {
    switch self {
    case .unsupportedUri(let uri): return "URI no soportada: \(uri)"
    case .imageLoadFailed(let uri): return "No se pudo cargar la imagen: \(uri)"
    case .imageRenderFailed(let uri): return "No se pudo preparar la imagen para detectar: \(uri)"
    case .detectionFailed(let reason): return "Fallo la deteccion de caras: \(reason)"
    }
  }
}
