import type {
  FaceDetectionOptions,
  FaceDetectionResult,
  TattooDetectionOptions,
  TattooDetectionResult,
} from "./TeralFaceDetector.types";
import TeralFaceDetector from "./TeralFaceDetectorModule";

export type {
  FaceDetectionOptions,
  FaceDetectionResult,
  FaceRect,
  FaceRegion,
  TattooDetectionOptions,
  TattooDetectionResult,
  TattooRect,
} from "./TeralFaceDetector.types";
export type { TeralFaceDetectorModule } from "./TeralFaceDetectorModule";

/**
 * `false` si el módulo nativo no está en este build (Expo Go, o un binario
 * anterior a instalar la librería). Úsalo para esconder la opción de censurado
 * automático en vez de dejar que falle al pulsarla.
 */
export const isFaceDetectionAvailable = TeralFaceDetector != null;

/**
 * Detecta las caras de una imagen local y devuelve sus rectángulos normalizados.
 *
 * No modifica la imagen: dibujar el censurado es cosa de quien llama, lo que
 * permite que el usuario mueva, borre o añada zonas antes de exportar.
 *
 * Con `region: 'eyes'` devuelve solo la banda de los ojos en lugar de la cara
 * entera. Ojo: eso **no anonimiza** — el resto del rostro sigue identificando a
 * la persona. Es la convención de las publicaciones médicas, útil cuando la
 * lesión está en la cara, no una medida de privacidad.
 *
 * @param uri Ruta o URI de la imagen. `file://` y rutas absolutas funcionan en
 * ambas plataformas; Android acepta además `content://`, y iOS admite `http(s)`.
 * @throws Si el módulo nativo no está presente, o si la imagen no se puede leer.
 */
export async function detectFaces(
  uri: string,
  options?: FaceDetectionOptions,
): Promise<FaceDetectionResult> {
  if (!TeralFaceDetector) {
    throw new Error(
      "[@teral-americas/react-native-face-detector] el módulo nativo no está disponible. " +
        "Hace falta recompilar la app (npx expo run:ios / run:android); no funciona en Expo Go.",
    );
  }

  return TeralFaceDetector.detectFaces(uri, options);
}

export default TeralFaceDetector;

/**
 * `false` si este build no trae el modelo de tatuajes.
 */
export const isTattooDetectionAvailable =
  TeralFaceDetector?.isTattooDetectionAvailable() ?? false;

/**
 * Detecta tatuajes con el modelo embebido, sin red.
 *
 * A diferencia de las caras, esto no lo resuelve ninguna API del sistema: es un
 * YOLOX-Nano entrenado con las cajas que produce Rekognition, así que hereda su
 * criterio y también sus fallos. Encuentra el 84% de esas cajas al umbral por
 * defecto, pero Rekognition solo localiza el tatuaje en algo más de la mitad de
 * las fotos donde lo reconoce: la censura automática es una ayuda, no una
 * garantía.
 */
export async function detectTattoos(
  uri: string,
  options?: TattooDetectionOptions,
): Promise<TattooDetectionResult> {
  if (!TeralFaceDetector) {
    throw new Error(
      "[@teral-americas/react-native-face-detector] el módulo nativo no está disponible. " +
        "Hace falta recompilar la app (npx expo run:ios); no funciona en Expo Go.",
    );
  }

  return TeralFaceDetector.detectTattoos(uri, options);
}
