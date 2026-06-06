import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  define: {
    global: 'window',
  },
  plugins: [react()],
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/auth': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass: (req) => {
          if (req.headers.accept && req.headers.accept.includes('text/html')) {
            return '/index.html'
          }
        },
      },
      '/messaging': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/feed': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/rooms': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/rewards': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/notifications': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/ai': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/media': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true,
      },
    }
  }
})
