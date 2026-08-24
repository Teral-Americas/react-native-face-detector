# @teral-americas/react-native-face-detector

Detección **on-device** de caras y tatuajes para React Native / Expo. Devuelve
coordenadas y **no toca la imagen**: el censurado lo dibuja tu app, con lo que el
usuario puede mover, borrar o añadir zonas antes de exportar.

Nada de la imagen sale del dispositivo: sin red, sin Google Play Services.

| | iOS | Android |
| --- | --- | --- |
| Caras | Vision | `android.media.FaceDetector` |
| Tatuajes | Core ML | LiteRT (TFLite) |

## Instalación

```bash
npm install @teral-americas/react-native-face-detector
npx pod-install
```

Es código nativo: hay que recompilar (`npx expo run:ios` / `npx expo run:android`).
No funciona en Expo Go.

## Caras

```ts
import { detectFaces, isFaceDetectionAvailable } from '@teral-americas/react-native-face-detector';

const { imageWidth, imageHeight, faces } = await detectFaces(uri, {
  region: 'face',        // o 'eyes'
  maxDetectionSize: 1000,
  minConfidence: 0.4,
});
// faces: [{ x, y, width, height, confidence, region, angle }] — normalizado 0..1
```

### `region: 'eyes'`

Devuelve solo la banda de los ojos: la convención de las publicaciones médicas,
útil cuando la lesión está en la cara. **No anonimiza** — mandíbula, nariz, boca
y orejas siguen identificando a la persona, y el reconocimiento facial funciona
con los ojos tapados. Es una decisión clínica, no una medida de privacidad.

En iOS la banda sale de los landmarks de Vision, así que se inclina con la cara;
`angle` trae esa inclinación en radianes. En Android va siempre horizontal
porque su API no reporta la pose. El rectángulo devuelto está alineado a los ejes
y **envuelve** la banda inclinada, de modo que ignorar `angle` no destapa nada.
Ajusta su tamaño con `eyeBandScale`.

## Tatuajes

```ts
import { detectTattoos, isTattooDetectionAvailable } from '@teral-americas/react-native-face-detector';

const { tattoos } = await detectTattoos(uri, { minConfidence: 0.05 });
```

A diferencia de las caras, esto no lo resuelve ninguna API del sistema: es un
**YOLOX-Nano** (Apache 2.0) entrenado con cajas generadas por AWS Rekognition, así
que hereda su criterio y también sus fallos.

- Encuentra el **84%** de las cajas al umbral por defecto (0.05).
- Rekognition solo localiza el tatuaje en algo más de la mitad de las fotos donde
  lo reconoce, así que sobre tatuajes reales el techo queda bastante por debajo.
- Va peor con tatuajes pequeños (recall 0.34) que con grandes (0.67).

**La censura automática de tatuajes es una ayuda, no una garantía.** La interfaz
no debería prometer que los ha tapado todos.

El umbral por defecto es bajo a propósito: difuminar de más cuesta píxeles,
dejarse un tatuaje cuesta la privacidad de un paciente.

## Detalles que importan

- **Las coordenadas son normalizadas sobre la imagen ya orientada por su EXIF.**
  Multiplicas por el tamaño al que la pintas y ya está, sin lógica de rotación.
- **Detectar en pequeño es igual de preciso y mucho más rápido**: de ahí el
  `maxDetectionSize` de 1000 por defecto en caras.
- **Las confianzas no son comparables entre plataformas.** Vision devuelve
  valores altos y homogéneos; `android.media.FaceDetector` reparte confianzas
  bajas incluso en caras claras. Calibra el umbral por plataforma.
- **El rectángulo va justo al objeto.** Si vas a censurar, agrándalo un 10-15%:
  sin margen quedan a la vista el contorno del tatuaje o la frente y el mentón,
  que bastan para identificar.
- **Android detecta caras frontales y poco más.** Perfiles y caras mal iluminadas
  se le escapan. El punto donde se sustituye por ML Kit es el cuerpo de
  `detectFaces` en el Kotlin; la API no cambia.

## Tamaño del build

- iOS: **+3,6 MB** (el modelo; Vision y Core ML son del sistema).
- Android: **+3,7 MB** de modelo y 2-4 MB del runtime de LiteRT.

## Modelo de tatuajes

El decodificador vive **fuera** del modelo: dentro generaba una operación que
TFLite no sabe convertir, y con la rejilla cruda iOS y Android aplican la misma
aritmética en vez de tener dos implementaciones que puedan divergir.

El `.mlmodelc` de iOS va compilado en el repo porque CocoaPods copia los recursos
tal cual, sin pasarlos por el compilador de Core ML. Se regenera con:

```bash
xcrun coremlcompiler compile TattooDetector.mlpackage .
```

## Licencia

MIT. El modelo de tatuajes se entrenó con YOLOX (Apache 2.0) sobre imágenes de
Wikimedia Commons con licencia libre.
