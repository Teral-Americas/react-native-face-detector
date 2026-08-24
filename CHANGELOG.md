# Changelog

## 0.2.0

- Nuevo modo `region: 'eyes'`: censura solo la banda de los ojos en lugar de la
  cara entera. En iOS sale de los landmarks de Vision y se inclina con la cara;
  en Android, del punto medio entre los ojos. No anonimiza — ver el README.
- Nueva `detectTattoos()`: detección de tatuajes con un YOLOX-Nano entrenado a
  partir de cajas de AWS Rekognition. Core ML en iOS, LiteRT en Android.
- `eyeBandScale` para ajustar el tamaño de la banda ocular sin recompilar.

## 0.1.0

- `detectFaces()`: detección de caras on-device con Vision en iOS y
  `android.media.FaceDetector` en Android. Devuelve coordenadas normalizadas y
  no modifica la imagen.
