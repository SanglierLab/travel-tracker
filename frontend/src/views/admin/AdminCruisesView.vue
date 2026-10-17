<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import { api, ApiError } from '../../api'
import { formatDate, timeAgo } from '../../format'

// Croisières suivies (AIS) : on enregistre le MMSI du navire et la date, puis on lance / arrête le suivi à la main.
// Une seule croisière peut être suivie à la fois (le serveur refuse le deuxième avec un message explicite).

const cruises = ref(null)
const loadError = ref(false)
const actionError = ref('')
const notice = ref('')

const form = reactive({ identifier: '', date: today(), time: '' })
const creating = ref(false)
const busyId = ref(null) // croisière dont une action est en cours (bouton on/off, suppression)
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
    cruises.value = await api('/api/admin/cruises')
  } catch {
    if (!silent) loadError.value = true // un rafraîchissement automatique raté ne dérange pas : on garde l'affichage
  }
}

function replace(updated) {
  cruises.value = cruises.value.map((cruise) => (cruise.id === updated.id ? updated : cruise))
}

// --- Enregistrement ---------------------------------------------------------

async function create() {
  actionError.value = ''
  notice.value = ''
  creating.value = true
  try {
    const created = await api('/api/admin/cruises', {
      method: 'POST',
      body: { identifier: form.identifier, date: form.date, time: form.time || null },
    })
    notice.value = `Croisière ${created.identifier} enregistrée. Activez son suivi quand il est temps.`
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

async function toggle(cruise) {
  actionError.value = ''
  notice.value = ''
  busyId.value = cruise.id
  const action = cruise.status === 'ACTIVE' ? 'stop' : 'start'
  try {
    replace(await api(`/api/admin/cruises/${cruise.id}/${action}`, { method: 'POST' }))
  } catch (e) {
    actionError.value = messageOf(e, "L'action a échoué. Vérifiez votre connexion et réessayez.")
    await load({ silent: true }) // on réaffiche l'état réel du serveur
  } finally {
    busyId.value = null
  }
}

// --- Suppression ----------------------------------------------------------------

async function remove() {
  const cruise = toDelete.value
  busyId.value = cruise.id
  actionError.value = ''
  try {
    await api(`/api/admin/cruises/${cruise.id}`, { method: 'DELETE' })
    cruises.value = cruises.value.filter((other) => other.id !== cruise.id)
  } catch (e) {
    actionError.value = messageOf(e, 'La suppression a échoué.')
  } finally {
    busyId.value = null
    toDelete.value = null
  }
}

// --- Archivage et export GPX (communs à tous les trajets : /api/admin/trips/:id/...) ---------

const showArchived = ref(false)
const archivedCount = computed(() => (cruises.value ?? []).filter((cruise) => cruise.archived).length)
const visible = computed(() => (cruises.value ?? []).filter((cruise) => showArchived.value || !cruise.archived))

async function setArchived(cruise, archived) {
  actionError.value = ''
  notice.value = ''
  busyId.value = cruise.id
  try {
    await api(`/api/admin/trips/${cruise.id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' })
    await load({ silent: true })
  } catch (e) {
    actionError.value = messageOf(e, "L'action a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    busyId.value = null
  }
}

/** Le fichier est téléchargé par le navigateur (même origine, session admin). */
function gpxUrl(cruise) {
  return `/api/admin/trips/${cruise.id}/gpx`
}

// --- Affichage ------------------------------------------------------------------

const STATUS = { PLANNED: 'Prévu', ACTIVE: 'Suivi en cours', FINISHED: 'Suivi arrêté' }

/** Date (et heure UTC si elle a été saisie) du départ. */
function departure(cruise) {
  const iso = cruise.scheduledDeparture
  const time = iso.slice(11, 16)
  return time === '00:00' ? formatDate(iso.slice(0, 10)) : `${formatDate(iso.slice(0, 10))} · ${time} UTC`
}

function positions(cruise) {
  if (cruise.pointCount === 0) return 'Aucune position enregistrée'
  const count = `${cruise.pointCount} position${cruise.pointCount > 1 ? 's' : ''}`
  return cruise.lastPointAt ? `${count} · dernière ${timeAgo(cruise.lastPointAt)}` : count
}

function pointsWarning(cruise) {
  const n = cruise.pointCount
  return n > 0 ? ` ainsi que ses ${n} position${n > 1 ? 's' : ''} enregistrée${n > 1 ? 's' : ''}` : ''
}

// --- Rafraîchissement automatique : tant qu'un suivi est actif, on suit l'arrivée des positions ---

const hasActive = computed(() => (cruises.value ?? []).some((cruise) => cruise.status === 'ACTIVE'))
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
    <h1>Croisières</h1>
    <p class="muted">
      Une seule croisière peut être suivie à la fois (en plus d'un éventuel vol). Pendant le suivi, les positions du navire s'affichent en tirets sur la carte du site. Un navire est identifié par son MMSI, numéro à 9 chiffres visible sur les sites de suivi maritime.
    </p>

    <form class="card" @submit.prevent="create">
      <h2>Ajouter une croisière</h2>
      <label class="field">
        <span>MMSI du navire (9 chiffres, ex. 227123456)</span>
        <input
          v-model="form.identifier"
          type="text"
          maxlength="20"
          required
          autocomplete="off"
          inputmode="numeric"
          placeholder="227123456"
        />
      </label>
      <label class="field">
        <span>Date de départ</span>
        <input v-model="form.date" type="date" required />
      </label>
      <label class="field">
        <span>Heure de départ (UTC), facultative</span>
        <input v-model="form.time" type="time" />
      </label>
      <p v-if="notice" class="success" role="status">{{ notice }}</p>
      <button class="btn" type="submit" :disabled="creating">{{ creating ? 'Enregistrement…' : 'Enregistrer la croisière' }}</button>
    </form>

    <p v-if="actionError" class="error flights__error" role="alert">{{ actionError }}</p>

    <p v-if="loadError" class="error" role="alert">
      Impossible de charger les croisières.
      <button class="btn btn--ghost btn--small" type="button" @click="load()">Réessayer</button>
    </p>
    <p v-else-if="!cruises" class="muted">Chargement…</p>
    <p v-else-if="cruises.length === 0" class="muted">Aucune croisière enregistrée pour le moment.</p>

    <label v-else-if="archivedCount > 0" class="flights__filter">
      <input v-model="showArchived" type="checkbox" />
      <span>Afficher les trajets archivés ({{ archivedCount }})</span>
    </label>

    <ul v-if="cruises && cruises.length > 0" class="flights">
      <li v-for="cruise in visible" :key="cruise.id" class="card flight flight--ais" :class="{ 'flight--active': cruise.status === 'ACTIVE', 'flight--archived': cruise.archived }">
        <div class="flight__head">
          <div class="flight__id">
            <strong class="flight__number">{{ cruise.identifier }}</strong>
            <span class="muted">{{ departure(cruise) }}</span>
          </div>
          <span class="badge" :class="{ 'badge--active': cruise.status === 'ACTIVE' }">{{ cruise.archived ? 'Archivé' : STATUS[cruise.status] }}</span>
        </div>
        <p class="muted flight__points">{{ positions(cruise) }}</p>
        <div class="flight__actions">
          <button
            class="switch"
            type="button"
            role="switch"
            :aria-checked="cruise.status === 'ACTIVE'"
            :aria-label="`Suivi du navire ${cruise.identifier}`"
            :disabled="busyId !== null || cruise.archived"
            @click="toggle(cruise)"
          >
            <span class="switch__track" aria-hidden="true"><span class="switch__knob"></span></span>
            <span>{{ cruise.status === 'ACTIVE' ? 'Activé' : 'Désactivé' }}</span>
          </button>
          <div class="flight__buttons">
            <a
              v-if="cruise.pointCount > 0"
              class="btn btn--ghost btn--small"
              :href="gpxUrl(cruise)"
              download
            >
              Exporter GPX
            </a>
            <button
              v-if="!cruise.archived"
              class="btn btn--ghost btn--small"
              type="button"
              :disabled="cruise.status === 'ACTIVE' || busyId !== null"
              :title="cruise.status === 'ACTIVE' ? 'Arrêtez le suivi pour pouvoir archiver cette croisière' : 'Masque cette croisière de la liste ; son tracé reste sur la carte'"
              @click="setArchived(cruise, true)"
            >
              Archiver
            </button>
            <button v-else class="btn btn--ghost btn--small" type="button" :disabled="busyId !== null" @click="setArchived(cruise, false)">
              Restaurer
            </button>
            <button
              class="btn btn--ghost btn--small btn--danger-text"
              type="button"
              :disabled="cruise.status === 'ACTIVE' || busyId !== null"
              :title="cruise.status === 'ACTIVE' ? 'Arrêtez le suivi pour pouvoir supprimer cette croisière' : undefined"
              @click="toDelete = cruise"
            >
              Supprimer
            </button>
          </div>
        </div>
      </li>
    </ul>

    <ConfirmDialog
      v-if="toDelete"
      :title="`Supprimer la croisière ${toDelete.identifier} ?`"
      confirm-label="Supprimer"
      :busy="busyId !== null"
      @confirm="remove"
      @cancel="toDelete = null"
    >
      <p>
        La croisière {{ toDelete.identifier }} sera supprimée<template v-if="toDelete.pointCount > 0">{{ pointsWarning(toDelete) }}</template>,
        <strong>de façon définitive</strong>. Son tracé disparaîtra de la carte.
      </p>
      <p class="muted">Pour le garder sur la carte sans l'afficher dans la liste, archivez-le plutôt.</p>
    </ConfirmDialog>
  </AdminLayout>
</template>
