/**
 * Que parte de la cara describe el rectangulo devuelto.
 *
 * - `face`: la cara entera. Es lo que protege de verdad la identidad.
 * - `eyes`: solo la banda de los ojos, la convencion de las publicaciones
 *   medicas. Conserva la lesion facial visible, pero **no anonimiza**: la
 *   mandibula, la nariz, la boca y las orejas siguen identificando a la persona,
 *   y los sistemas de reconocimiento facial funcionan con los ojos tapados.
 *   Es una eleccion clinica, no una medida de privacidad.
 */
export type FaceRegion = "face" | "eyes";

/** Rectángulo normalizado (0..1) sobre la imagen ya orientada por su EXIF. */
export interface FaceRect {
  x: number;
  y: number;
  width: number;
  height: number;
  /**
   * 0..1. Los dos detectores no puntúan igual: Vision devuelve valores altos y
   * homogéneos, mientras que la API de Android reparte confianzas bajas incluso
   * en caras claras. Conviene calibrar el umbral por plataforma.
   */
  confidence: number;
  /** Que parte de la cara describe este rectangulo. */
  region: FaceRegion;
  /**
   * Inclinacion de la cara en radianes, positiva en sentido horario.
   *
   * El rectangulo ya viene alineado a los ejes y envuelve la region inclinada,
   * asi que se puede ignorar sin descubrir nada. Solo hace falta si se quiere
   * dibujar la banda girada, que en una cara ladeada tapa bastante menos pixel
   * inutil. Android casi siempre devuelve 0: su API no reporta la pose.
   */
  angle: number;
}

export interface FaceDetectionResult {
  /** Tamaño real de la imagen en píxeles, con la rotación EXIF aplicada. */
  imageWidth: number;
  imageHeight: number;
  faces: FaceRect[];
}

export interface FaceDetectionOptions {
  /**
   * Que censurar. Por defecto `face`, que es lo unico que protege la identidad.
   *
   * @default 'face'
   */
  region?: FaceRegion;
  /**
   * Multiplicador de la banda ocular. Sube por encima de 1 para taparla más
   * generosa; solo aplica con `region: 'eyes'`.
   *
   * @default 1
   */
  eyeBandScale?: number;
  /**
   * Lado mayor al que se reduce la imagen antes de detectar. Detectar sobre una
   * imagen pequeña encuentra las mismas caras y tarda una fracción; subirlo solo
   * ayuda con caras muy pequeñas dentro de fotos muy grandes.
   *
   * @default 1000
   */
  maxDetectionSize?: number;
  /**
   * Descarta las caras por debajo de esta confianza.
   *
   * @default 0
   */
  minConfidence?: number;
}

/** Rectángulo de un tatuaje, normalizado (0..1) sobre la imagen ya orientada. */
export interface TattooRect {
  x: number;
  y: number;
  width: number;
  height: number;
  confidence: number;
}

export interface TattooDetectionResult {
  imageWidth: number;
  imageHeight: number;
  tattoos: TattooRect[];
}

export interface TattooDetectionOptions {
  /**
   * Descarta las cajas por debajo de esta confianza.
   *
   * El valor por defecto es bajo a propósito: para censurar, difuminar de más
   * cuesta píxeles y dejarse un tatuaje cuesta la privacidad de un paciente. A
   * 0.05 el modelo encuentra el 84% de las cajas; a 0.5, el 70%.
   *
   * @default 0.05
   */
  minConfidence?: number;
  /**
   * Solape a partir del cual dos cajas se consideran la misma.
   *
   * @default 0.45
   */
  iouThreshold?: number;
}
