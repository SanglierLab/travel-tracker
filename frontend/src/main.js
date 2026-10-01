import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { loadSession } from './session'
import './style.css'

// Récupère l'état de la session (et le cookie CSRF) sans bloquer l'affichage.
loadSession()

createApp(App).use(router).mount('#app')
