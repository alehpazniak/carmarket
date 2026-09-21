import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    // Allow access through a public tunnel hostname (ngrok, cloudflared, ...)
    allowedHosts: true,
    // Single-origin setup for tunnels: expose only :5173 and proxy backend calls
    proxy: {
      '/auth/oauth2': 'http://localhost:8081',
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8085', ws: true },
    },
  }
})
