/** Rectángulo de una cara, normalizado (0..1) sobre la imagen ya orientada por su EXIF. */
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
}

export interface FaceDetectionResult {
  /** Tamaño real de la imagen en píxeles, con la rotación EXIF aplicada. */
  imageWidth: number;
  imageHeight: number;
  faces: FaceRect[];
}

export interface FaceDetectionOptions {
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
