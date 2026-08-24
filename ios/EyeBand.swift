import CoreGraphics
import Vision

/**
 Banda ocular derivada de los landmarks de Vision.

 Vision entrega los puntos de cada ojo relativos al bounding box de la cara, asi
 que hay que llevarlos primero a coordenadas de imagen. Con los dos centros se
 tiene todo: el ancho de la banda sale de la distancia entre ojos y su
 inclinacion, del angulo entre ellos.
 */
enum EyeBand {
  /// Ancho de la banda como multiplo de la distancia entre ojos.
  ///
  /// Un ojo mide en torno a 0.35 de esa distancia, asi que cubrir ambos pide
  /// 1.35 y el resto es margen. Con valores mayores la banda se come casi todo
  /// el ancho de la cara y deja de tener sentido frente a taparla entera.
  private static let widthFactor: CGFloat = 1.7
  /// Alto de la banda, en la misma unidad. Un ojo alto mide unos 0.2.
  private static let heightFactor: CGFloat = 0.5

  /**
   Devuelve el rectangulo alineado a los ejes que envuelve la banda inclinada, y
   su angulo. Se devuelve envuelto para que quien no sepa rotar siga tapando los
   ojos enteros.
   */
  static func from(observation: VNFaceObservation, scale: CGFloat = 1) -> (rect: CGRect, angle: CGFloat)? {
    guard
      let landmarks = observation.landmarks,
      let leftEye = landmarks.leftEye,
      let rightEye = landmarks.rightEye,
      let leftCenter = center(of: leftEye, in: observation.boundingBox),
      let rightCenter = center(of: rightEye, in: observation.boundingBox)
    else {
      return nil
    }

    let deltaX = rightCenter.x - leftCenter.x
    let deltaY = rightCenter.y - leftCenter.y
    let distance = (deltaX * deltaX + deltaY * deltaY).squareRoot()

    guard distance > 0 else { return nil }

    let angle = atan2(deltaY, deltaX)
    let midPoint = CGPoint(x: (leftCenter.x + rightCenter.x) / 2, y: (leftCenter.y + rightCenter.y) / 2)

    let bandWidth = distance * widthFactor * scale
    let bandHeight = distance * heightFactor * scale

    // Envolvente de un rectangulo girado: sus lados proyectados sobre cada eje.
    let cosine = abs(cos(angle))
    let sine = abs(sin(angle))
    let boundingWidth = bandWidth * cosine + bandHeight * sine
    let boundingHeight = bandWidth * sine + bandHeight * cosine

    let rect = CGRect(
      x: midPoint.x - boundingWidth / 2,
      y: midPoint.y - boundingHeight / 2,
      width: boundingWidth,
      height: boundingHeight
    )

    return (rect, angle)
  }

  /**
   Centro de una region de landmarks, en coordenadas de imagen con el origen
   arriba a la izquierda. Vision los da relativos al bounding box y con el eje Y
   invertido.
   */
  private static func center(of region: VNFaceLandmarkRegion2D, in boundingBox: CGRect) -> CGPoint? {
    let points = region.normalizedPoints
    guard !points.isEmpty else { return nil }

    var sumX: CGFloat = 0
    var sumY: CGFloat = 0
    for point in points {
      sumX += point.x
      sumY += point.y
    }

    let averageX = sumX / CGFloat(points.count)
    let averageY = sumY / CGFloat(points.count)

    let imageX = boundingBox.origin.x + averageX * boundingBox.width
    let imageY = boundingBox.origin.y + averageY * boundingBox.height

    return CGPoint(x: imageX, y: 1 - imageY)
  }
}
