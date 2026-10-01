import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// En développement, l'API Spring Boot tourne sur le port 8080 (lancée depuis backend/).
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/media': 'http://localhost:8080', // profil « dev » du backend
    },
  },
})
