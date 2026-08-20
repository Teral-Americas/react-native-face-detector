import { NativeModule, requireOptionalNativeModule } from "expo-modules-core";

import type {
  FaceDetectionOptions,
  FaceDetectionResult,
} from "./TeralFaceDetector.types";

export interface TeralFaceDetectorModule extends NativeModule {
  detectFaces(
    uri: string,
    options?: FaceDetectionOptions,
  ): Promise<FaceDetectionResult>;
}

/**
 * `requireOptional…` en lugar de `requireNativeModule`: quien consuma la
 * librería puede estar en Expo Go o en un build hecho antes de añadirla, y ahí
 * conviene poder avisar en vez de reventar al importar.
 */
export default requireOptionalNativeModule<TeralFaceDetectorModule>(
  "TeralFaceDetector",
);
