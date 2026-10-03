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
      <div class="tile tile--soon" aria-disabled="true">
        <span class="tile__icon" aria-hidden="true">✈️</span>Trajets<small>Bientôt</small>
      </div>
      <div class="tile tile--soon" aria-disabled="true">
        <span class="tile__icon" aria-hidden="true">📍</span>Points<small>Bientôt</small>
      </div>
      <div class="tile tile--soon" aria-disabled="true">
        <span class="tile__icon" aria-hidden="true">⚙️</span>Config<small>Bientôt</small>
      </div>
      <RouterLink class="tile" to="/">
        <span class="tile__icon" aria-hidden="true">🌍</span>Voir le site
      </RouterLink>
      <button class="tile" type="button" @click="onLogout">
        <span class="tile__icon" aria-hidden="true">🚪</span>Déconnexion
      </button>
    </nav>
  </main>
</template>
