# Changelog

## 0.2.1

- iOS: `detectFaces()` pide solo los rectangulos salvo que se le pida
  `region: 'eyes'`. Los landmarks solo hacen falta para la banda ocular, y en
  iOS 26 salen caros: Vision monta su `VNFaceBBoxAligner` en cuanto encuentra
  una cara y le pide a Metal una textura de tamaño -1, con lo que la asercion de
  Metal mata el proceso — `SIGABRT`, sin excepcion que atrapar y sin nada que el
  lado de JavaScript pueda hacer. Android no estaba afectado.

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
