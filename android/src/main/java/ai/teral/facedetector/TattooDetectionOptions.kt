package ai.teral.facedetector

import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

/**
 * Opciones del detector de tatuajes.
 *
 * El umbral por defecto es bajo a proposito: para censurar, difuminar de mas
 * cuesta pixeles y dejarse un tatuaje cuesta la privacidad de un paciente. A 0.05
 * el modelo encuentra el 84% de las cajas, frente al 70% con el umbral que
 * maximiza el equilibrio entre aciertos y falsos positivos.
 */
class TattooDetectionOptions : Record {
  @Field
  var minConfidence: Double = 0.05

  @Field
  var iouThreshold: Double = 0.45

  /** Lado mayor al que se reduce la imagen antes de detectar. */
  @Field
  var maxDetectionSize: Int = 1280
}
