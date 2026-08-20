import type {
  FaceDetectionOptions,
  FaceDetectionResult,
} from "./TeralFaceDetector.types";
import TeralFaceDetector from "./TeralFaceDetectorModule";

export type {
  FaceDetectionOptions,
  FaceDetectionResult,
  FaceRect,
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
      "[@teral/react-native-face-detector] el módulo nativo no está disponible. " +
        "Hace falta recompilar la app (npx expo run:ios / run:android); no funciona en Expo Go.",
    );
  }

  return TeralFaceDetector.detectFaces(uri, options);
}

export default TeralFaceDetector;
