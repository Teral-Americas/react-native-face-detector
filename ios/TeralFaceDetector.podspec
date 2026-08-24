Pod::Spec.new do |s|
  s.name           = 'TeralFaceDetector'
  s.version        = '0.2.0'
  s.summary        = 'Deteccion de caras y tatuajes on-device.'
  s.description    = 'Devuelve las coordenadas normalizadas de las caras encontradas en una imagen. No modifica la imagen: el censurado se dibuja en JS con Skia.'
  s.author         = 'Teral'
  s.homepage       = 'https://teral.ai'
  s.platforms      = { :ios => '15.1' }
  s.source         = { git: 'https://github.com/Teral-Americas/react-native-face-detector.git', tag: s.version.to_s }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.frameworks = 'Vision'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
  # El modelo va ya compilado: CocoaPods copia los recursos tal cual, sin pasarlos
  # por el compilador de Core ML, asi que un .mlpackage llegaria al dispositivo
  # sin poder cargarse. Se regenera con:
  #   xcrun coremlcompiler compile TattooDetector.mlpackage .
  s.resources = "TattooDetector.mlmodelc"
end
