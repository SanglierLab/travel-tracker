import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api'
import { loadSession, session } from './session'
import './style.css'

// Session expirée en plein travail : retour à la connexion, puis retour sur la page en cours.
onUnauthorized(() => {
  session.authenticated = false
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})

// Récupère l'état de la session (et le cookie CSRF) sans bloquer l'affichage.
loadSession()

createApp(App).use(router).mount('#app')
