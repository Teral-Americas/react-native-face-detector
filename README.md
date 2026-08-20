# @teral/react-native-face-detector

Detección de caras **on-device** para React Native / Expo. Devuelve las coordenadas
de las caras y **no toca la imagen**: el censurado lo dibuja tu app, con lo que el
usuario puede mover, borrar o añadir zonas antes de exportar.

- iOS: `Vision` (`VNDetectFaceRectanglesRequest`)
- Android: `android.media.FaceDetector`, parte del sistema

Sin red, sin Google Play Services y sin modelo empaquetado: nada de la imagen sale
del dispositivo.

## Instalación

```bash
npm install @teral/react-native-face-detector
npx pod-install
```

Es código nativo: hace falta recompilar la app (`npx expo run:ios` / `npx expo run:android`).
No funciona en Expo Go.

## Uso

```ts
import { detectFaces, isFaceDetectionAvailable } from '@teral/react-native-face-detector';

const { imageWidth, imageHeight, faces } = await detectFaces(uri, {
  maxDetectionSize: 1000,
  minConfidence: 0.4,
});

// faces: [{ x, y, width, height, confidence }] — normalizado 0..1
const inPixels = faces.map((face) => ({
  left: face.x * imageWidth,
  top: face.y * imageHeight,
  width: face.width * imageWidth,
  height: face.height * imageHeight,
}));
```

`isFaceDetectionAvailable` es `false` cuando el módulo nativo no está en el build;
úsalo para esconder la opción de censurado automático en vez de dejar que falle.

## API

### `detectFaces(uri, options?): Promise<FaceDetectionResult>`

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `uri` | `string` | Ruta absoluta, `file://` o `content://` (Android). iOS admite además `http(s)`. |
| `options.maxDetectionSize` | `number` | Lado mayor al que se reduce la imagen antes de detectar. Por defecto `1000`. |
| `options.minConfidence` | `number` | Descarta las caras por debajo de esta confianza. Por defecto `0`. |

Devuelve `{ imageWidth, imageHeight, faces }`, donde `imageWidth`/`imageHeight` son
los píxeles reales de la imagen **con la rotación EXIF ya aplicada** y cada cara es
un rectángulo normalizado sobre esa misma imagen. Es decir: multiplicas por el
tamaño al que la pintas y ya está, sin lógica de orientación en JS.

## Detalles que importan

- **Detectar en pequeño es igual de preciso y mucho más rápido.** Por eso el valor
  por defecto de `maxDetectionSize` es 1000px; subirlo solo ayuda con caras muy
  pequeñas dentro de fotos muy grandes.
- **Las confianzas no son comparables entre plataformas.** Vision devuelve valores
  altos y homogéneos; `android.media.FaceDetector` reparte confianzas bajas incluso
  en caras claras. Calibra el umbral por plataforma.
- **Da un rectángulo justo a la cara.** Si vas a censurar, agranda un 10–15%: sin
  margen quedan a la vista frente, orejas y mentón, que bastan para reconocer a
  alguien.
- **Android detecta caras frontales y poco más.** Perfiles, caras giradas o mal
  iluminadas se le escapan. Si necesitas precisión, el punto donde se sustituye por
  ML Kit es el cuerpo de `detectFaces` en el Kotlin; la API no cambia.

## Licencia

MIT
