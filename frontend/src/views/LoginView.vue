<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api'
import { login } from '../session'

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

// On n'accepte qu'un chemin interne comme destination (évite les redirections vers un autre site).
function safeRedirect(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/admin'
}

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await login(username.value, password.value)
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      error.value = 'Identifiant ou mot de passe incorrect.'
    } else if (e instanceof ApiError && e.status === 429) {
      error.value = 'Trop de tentatives. Réessayez dans quelques minutes.'
    } else if (e instanceof ApiError && e.status === 403) {
      error.value = 'Session expirée. Rechargez la page puis réessayez.'
    } else {
      error.value = 'Connexion impossible. Vérifiez votre réseau et réessayez.'
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page page--narrow">
    <h1>Connexion</h1>
    <form class="card" @submit.prevent="submit">
      <label class="field">
        <span>Identifiant</span>
        <input v-model.trim="username" type="text" autocomplete="username" autocapitalize="none" required />
      </label>
      <label class="field">
        <span>Mot de passe</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="btn" type="submit" :disabled="busy">Se connecter</button>
    </form>
    <p><RouterLink to="/">Retour au site</RouterLink></p>
  </main>
</template>
