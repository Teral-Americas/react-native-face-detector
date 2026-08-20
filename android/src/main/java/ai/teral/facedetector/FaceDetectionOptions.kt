package ai.teral.facedetector

import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

/**
 * Opciones de deteccion. Los valores por defecto replican los de Signal: detectar
 * sobre una version reducida encuentra las mismas caras y tarda mucho menos.
 */
class FaceDetectionOptions : Record {
  @Field
  var maxDetectionSize: Int = 1000

  @Field
  var minConfidence: Double = 0.0
}
