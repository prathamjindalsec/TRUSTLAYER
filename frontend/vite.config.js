import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * The dev proxy keeps browser requests same-origin from the frontend's perspective. It avoids
 * turning on a broad credentialed CORS policy in the Spring application during local work.
 */
export default defineConfig(({ mode }) => {
  // Vite exposes .env values to browser code through import.meta.env, but config evaluation uses
  // loadEnv. Reading process.env here made a copied .env silently fall back to port 8080.
  const env = loadEnv(mode, process.cwd(), '');
  const backendTarget = env.VITE_BACKEND_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
        },
        '/actuator': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
      css: true,
    },
  };
});
