import ExpoModulesCore

/**
 Opciones de deteccion. Los valores por defecto replican los que usa Signal:
 detectar sobre un render pequeño es igual de preciso y mucho mas rapido.
 */
struct FaceDetectionOptions: Record {
  @Field
  var maxDetectionSize: Int = 1000

  @Field
  var minConfidence: Double = 0.0
}
