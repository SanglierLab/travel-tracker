<script setup>
import { useRouter } from 'vue-router'
import { logout, session } from '../../session'

const router = useRouter()

async function onLogout() {
  await logout()
  router.push('/')
}
</script>

<template>
  <main class="page">
    <h1>Administration</h1>
    <p class="muted">Connecté : {{ session.username }}</p>

    <nav class="tiles" aria-label="Menu d'administration">
      <RouterLink class="tile" to="/admin/galeries">
        <span class="tile__icon" aria-hidden="true">🖼️</span>Galeries
      </RouterLink>
      <RouterLink class="tile" to="/admin/vols">
        <span class="tile__icon" aria-hidden="true">✈️</span>Vols
      </RouterLink>
      <RouterLink class="tile" to="/admin/croisieres">
        <span class="tile__icon" aria-hidden="true">🚢</span>Croisières
      </RouterLink>
      <RouterLink class="tile" to="/admin/livetrack">
        <span class="tile__icon" aria-hidden="true">⌚</span>Montre Garmin
      </RouterLink>
      <RouterLink class="tile" to="/admin/telephone">
        <span class="tile__icon" aria-hidden="true">📱</span>Téléphone
      </RouterLink>
      <RouterLink class="tile" to="/admin/etapes">
        <span class="tile__icon" aria-hidden="true">📍</span>Étapes
      </RouterLink>
      <RouterLink class="tile" to="/admin/config">
        <span class="tile__icon" aria-hidden="true">⚙️</span>Config
      </RouterLink>
      <RouterLink class="tile" to="/">
        <span class="tile__icon" aria-hidden="true">🌍</span>Voir le site
      </RouterLink>
      <button class="tile" type="button" @click="onLogout">
        <span class="tile__icon" aria-hidden="true">🚪</span>Déconnexion
      </button>
    </nav>
  </main>
</template>
