<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import { api, ApiError } from '../../api'
import { formatDate, timeAgo } from '../../format'

// Vols suivis (ADS-B) : on enregistre le numéro de vol et la date, puis on lance / arrête le suivi à la main.
// Un seul vol peut être suivi à la fois (le serveur refuse le deuxième avec un message explicite).

const flights = ref(null)
const loadError = ref(false)
const actionError = ref('')
const notice = ref('')

const form = reactive({ identifier: '', date: today(), time: '' })
const creating = ref(false)
const busyId = ref(null) // vol dont une action est en cours (bouton on/off, suppression)
const toDelete = ref(null)
const REFRESH_MS = 15000

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

async function load({ silent = false } = {}) {
  if (!silent) loadError.value = false
  try {
    flights.value = await api('/api/admin/flights')
  } catch {
    if (!silent) loadError.value = true // un rafraîchissement automatique raté ne dérange pas : on garde l'affichage
  }
}

function replace(updated) {
  flights.value = flights.value.map((flight) => (flight.id === updated.id ? updated : flight))
}

// --- Enregistrement ---------------------------------------------------------

async function create() {
  actionError.value = ''
  notice.value = ''
  creating.value = true
  try {
    const created = await api('/api/admin/flights', {
      method: 'POST',
      body: { identifier: form.identifier, date: form.date, time: form.time || null },
    })
    notice.value = `Vol ${created.identifier} enregistré. Activez son suivi quand il est temps.`
    form.identifier = ''
    form.time = ''
    await load({ silent: true })
  } catch (e) {
    actionError.value = messageOf(e, "L'enregistrement a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    creating.value = false
  }
}

// --- Bouton on / off ------------------------------------------------------------

async function toggle(flight) {
  actionError.value = ''
  notice.value = ''
  busyId.value = flight.id
  const action = flight.status === 'ACTIVE' ? 'stop' : 'start'
  try {
    replace(await api(`/api/admin/flights/${flight.id}/${action}`, { method: 'POST' }))
  } catch (e) {
    actionError.value = messageOf(e, "L'action a échoué. Vérifiez votre connexion et réessayez.")
    await load({ silent: true }) // on réaffiche l'état réel du serveur
  } finally {
    busyId.value = null
  }
}

// --- Suppression ----------------------------------------------------------------

async function remove() {
  const flight = toDelete.value
  busyId.value = flight.id
  actionError.value = ''
  try {
    await api(`/api/admin/flights/${flight.id}`, { method: 'DELETE' })
    flights.value = flights.value.filter((other) => other.id !== flight.id)
  } catch (e) {
    actionError.value = messageOf(e, 'La suppression a échoué.')
  } finally {
    busyId.value = null
    toDelete.value = null
  }
}

// --- Affichage ------------------------------------------------------------------

const STATUS = { PLANNED: 'Prévu', ACTIVE: 'Suivi en cours', FINISHED: 'Suivi arrêté' }

/** Date (et heure UTC si elle a été saisie) du départ. */
function departure(flight) {
  const iso = flight.scheduledDeparture
  const time = iso.slice(11, 16)
  return time === '00:00' ? formatDate(iso.slice(0, 10)) : `${formatDate(iso.slice(0, 10))} · ${time} UTC`
}

function positions(flight) {
  if (flight.pointCount === 0) return 'Aucune position enregistrée'
  const count = `${flight.pointCount} position${flight.pointCount > 1 ? 's' : ''}`
  return flight.lastPointAt ? `${count} · dernière ${timeAgo(flight.lastPointAt)}` : count
}

function pointsWarning(flight) {
  const n = flight.pointCount
  return n > 0 ? ` ainsi que ses ${n} position${n > 1 ? 's' : ''} enregistrée${n > 1 ? 's' : ''}` : ''
}

// --- Rafraîchissement automatique : tant qu'un suivi est actif, on suit l'arrivée des positions ---

const hasActive = computed(() => (flights.value ?? []).some((flight) => flight.status === 'ACTIVE'))
let timer = null
watch(
  hasActive,
  (active) => {
    if (active && timer === null) timer = setInterval(() => load({ silent: true }), REFRESH_MS)
    else if (!active && timer !== null) {
      clearInterval(timer)
      timer = null
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  if (timer !== null) clearInterval(timer)
})

onMounted(load)
</script>

<template>
  <AdminLayout>
    <h1>Vols</h1>
    <p class="muted">
      Un seul vol peut être suivi à la fois. Pendant le suivi, les positions de l'avion s'affichent en pointillés sur la carte du site.
    </p>

    <form class="card" @submit.prevent="create">
      <h2>Ajouter un vol</h2>
      <label class="field">
        <span>Numéro de vol</span>
        <input
          v-model="form.identifier"
          type="text"
          maxlength="20"
          required
          autocomplete="off"
          autocapitalize="characters"
          placeholder="AFR1234"
        />
      </label>
      <label class="field">
        <span>Date du vol</span>
        <input v-model="form.date" type="date" required />
      </label>
      <label class="field">
        <span>Heure de départ (UTC), facultative</span>
        <input v-model="form.time" type="time" />
      </label>
      <p v-if="notice" class="success" role="status">{{ notice }}</p>
      <button class="btn" type="submit" :disabled="creating">{{ creating ? 'Enregistrement…' : 'Enregistrer le vol' }}</button>
    </form>

    <p v-if="actionError" class="error flights__error" role="alert">{{ actionError }}</p>

    <p v-if="loadError" class="error" role="alert">
      Impossible de charger les vols.
      <button class="btn btn--ghost btn--small" type="button" @click="load()">Réessayer</button>
    </p>
    <p v-else-if="!flights" class="muted">Chargement…</p>
    <p v-else-if="flights.length === 0" class="muted">Aucun vol enregistré pour le moment.</p>

    <ul v-else class="flights">
      <li v-for="flight in flights" :key="flight.id" class="card flight" :class="{ 'flight--active': flight.status === 'ACTIVE' }">
        <div class="flight__head">
          <div class="flight__id">
            <strong class="flight__number">{{ flight.identifier }}</strong>
            <span class="muted">{{ departure(flight) }}</span>
          </div>
          <span class="badge" :class="{ 'badge--active': flight.status === 'ACTIVE' }">{{ STATUS[flight.status] }}</span>
        </div>
        <p class="muted flight__points">{{ positions(flight) }}</p>
        <div class="flight__actions">
          <button
            class="switch"
            type="button"
            role="switch"
            :aria-checked="flight.status === 'ACTIVE'"
            :aria-label="`Suivi du vol ${flight.identifier}`"
            :disabled="busyId !== null"
            @click="toggle(flight)"
          >
            <span class="switch__track" aria-hidden="true"><span class="switch__knob"></span></span>
            <span>{{ flight.status === 'ACTIVE' ? 'Activé' : 'Désactivé' }}</span>
          </button>
          <button
            class="btn btn--ghost btn--small btn--danger-text"
            type="button"
            :disabled="flight.status === 'ACTIVE' || busyId !== null"
            :title="flight.status === 'ACTIVE' ? 'Arrêtez le suivi pour pouvoir supprimer ce vol' : undefined"
            @click="toDelete = flight"
          >
            Supprimer
          </button>
        </div>
      </li>
    </ul>

    <ConfirmDialog
      v-if="toDelete"
      :title="`Supprimer le vol ${toDelete.identifier} ?`"
      confirm-label="Supprimer"
      :busy="busyId !== null"
      @confirm="remove"
      @cancel="toDelete = null"
    >
      <p>
        Le vol {{ toDelete.identifier }} sera supprimé<template v-if="toDelete.pointCount > 0">{{ pointsWarning(toDelete) }}</template>,
        <strong>de façon définitive</strong>. Son tracé disparaîtra de la carte.
      </p>
    </ConfirmDialog>
  </AdminLayout>
</template>
