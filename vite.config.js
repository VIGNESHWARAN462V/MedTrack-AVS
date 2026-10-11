import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
  ],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            const cleanId = id.replace(/\\/g, '/');
            if (
              cleanId.includes('/react/') ||
              cleanId.includes('/react-dom/') ||
              cleanId.includes('/react-router/') ||
              cleanId.includes('/react-router-dom/') ||
              cleanId.includes('/scheduler/')
            ) {
              return 'vendor-core';
            }
            if (cleanId.includes('leaflet') || cleanId.includes('react-leaflet')) {
              return 'vendor-maps';
            }
            if (cleanId.includes('html5-qrcode') || cleanId.includes('/qrcode/')) {
              return 'vendor-scanner';
            }
          }
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
})
