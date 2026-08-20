Pod::Spec.new do |s|
  s.name           = 'TeralFaceDetector'
  s.version        = '0.1.0'
  s.summary        = 'Deteccion de caras on-device con Vision.'
  s.description    = 'Devuelve las coordenadas normalizadas de las caras encontradas en una imagen. No modifica la imagen: el censurado se dibuja en JS con Skia.'
  s.author         = 'Teral'
  s.homepage       = 'https://teral.ai'
  s.platforms      = { :ios => '15.1' }
  s.source         = { git: 'https://github.com/teral-ai/react-native-face-detector.git', tag: s.version.to_s }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.frameworks = 'Vision'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
