import ExpoModulesCore

/** Que parte de la cara se devuelve: la cara entera o la banda de los ojos. */
enum FaceRegion: String, Enumerable {
  case face
  case eyes
}

/**
 Opciones de deteccion. Los valores por defecto replican los que usa Signal:
 detectar sobre un render pequeño es igual de preciso y mucho mas rapido.
 */
struct FaceDetectionOptions: Record {
  @Field
  var region: FaceRegion = .face

  /// Multiplicador de la banda ocular, para ajustarla sin recompilar.
  @Field
  var eyeBandScale: Double = 1.0

  @Field
  var maxDetectionSize: Int = 1000

  @Field
  var minConfidence: Double = 0.0
}
