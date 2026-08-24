package ai.teral.facedetector

import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import expo.modules.kotlin.types.Enumerable

/** Que parte de la cara se devuelve: la cara entera o la banda de los ojos. */
enum class FaceRegion(val value: String) : Enumerable {
  FACE("face"),
  EYES("eyes")
}

/**
 * Opciones de deteccion. Los valores por defecto replican los de Signal: detectar
 * sobre una version reducida encuentra las mismas caras y tarda mucho menos.
 */
class FaceDetectionOptions : Record {
  @Field
  var region: FaceRegion = FaceRegion.FACE

  /** Multiplicador de la banda ocular, para ajustarla sin recompilar. */
  @Field
  var eyeBandScale: Double = 1.0

  @Field
  var maxDetectionSize: Int = 1000

  @Field
  var minConfidence: Double = 0.0
}
