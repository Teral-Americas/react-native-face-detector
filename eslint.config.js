// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoModuleConfig = require('expo-module-scripts/eslint.config.base');

module.exports = defineConfig([
  expoModuleConfig,
  {
    ignores: ['build/**'],
  },
]);
