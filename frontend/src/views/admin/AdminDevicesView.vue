<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import { api, ApiError } from '../../api'
import { formatDateTime } from '../../format'

// Sessions « Téléphone » : les positions de GPSLogger sont regroupées automatiquement par excursion
// (une nouvelle session après 3 h sans position). Ici on les nomme, on les coupe, on les exporte, on les archive.

const sessions = ref(null)
const loadError = ref(false)
const actionError = ref('')
const notice = ref('')
const busyId = ref(null)
const toDelete = ref(null)
const showArchived = ref(false)
const renaming = ref(null) // { id, name }
const splitting = ref(null) // { id, at } : at au format de <input type="datetime-local"> (heure locale)
const REFRESH_MS = 30000

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

async function load({ silent = false } = {}) {
  if (!silent) loadError.value = false
  try {
    sessions.value = await api('/api/admin/device-sessions')
  } catch {
    if (!silent) loadError.value = true
  }
}

async function run(session, action, fallback) {
  actionError.value = ''
  notice.value = ''
  busyId.value = session.id
  try {
    await action()
    await load({ silent: true })
    return true
  } catch (e) {
    actionError.value = messageOf(e, fallback)
    return false
  } finally {
    busyId.value = null
  }
}

// --- Nom --------------------------------------------------------------------------------------

function defaultName(session) {
  return `Téléphone · ${formatDateTime(session.startedAt)}`
}

function displayName(session) {
  return session.name ?? defaultName(session)
}

function startRename(session) {
  splitting.value = null
  renaming.value = { id: session.id, name: session.name ?? '' }
}

async function saveRename(session) {
  const ok = await run(
    session,
    () => api(`/api/admin/device-sessions/${session.id}`, { method: 'PUT', body: { name: renaming.value.name } }),
    'Le changement de nom a échoué.',
  )
  if (ok) renaming.value = null
}

// --- Couper -------------------------------------------------------------------------------------

function pad(n) {
  return String(n).padStart(2, '0')
}

/** Date locale au format attendu par <input type="datetime-local"> : 2026-10-12T14:05. */
function toLocalInput(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function startSplit(session) {
  renaming.value = null
  splitting.value = { id: session.id, at: toLocalInput(new Date(session.endedAt ?? session.startedAt)) }
}

async function saveSplit(session) {
  const at = new Date(splitting.value.at)
  if (Number.isNaN(at.getTime())) {
    actionError.value = 'Choisissez la date et l’heure de la coupure.'
    return
  }
  const ok = await run(
    session,
    () => api(`/api/admin/device-sessions/${session.id}/split`, { method: 'POST', body: { at: at.toISOString() } }),
    'La coupure a échoué.',
  )
  if (ok) {
    splitting.value = null
    notice.value = 'Session coupée : les positions à partir de cette heure sont dans une nouvelle session.'
  }
}

// --- Archivage, export, suppression ---------------------------------------------------------

function setArchived(session, archived) {
  return run(session, () => api(`/api/admin/trips/${session.id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' }), "L'action a échoué.")
}

function gpxUrl(session) {
  return `/api/admin/trips/${session.id}/gpx`
}

async function remove() {
  const session = toDelete.value
  await run(session, () => api(`/api/admin/device-sessions/${session.id}`, { method: 'DELETE' }), 'La suppression a échoué.')
  toDelete.value = null
}

// --- Affichage ------------------------------------------------------------------------------

const archivedCount = computed(() => (sessions.value ?? []).filter((session) => session.archived).length)
const visible = computed(() => (sessions.value ?? []).filter((session) => showArchived.value || !session.archived))

function period(session) {
  if (session.pointCount === 0) return 'Aucune position enregistrée'
  const count = `${session.pointCount} position${session.pointCount > 1 ? 's' : ''}`
  return `${formatDateTime(session.startedAt)} → ${formatDateTime(session.endedAt)} · ${count}`
}

function pointsWarning(session) {
  const n = session.pointCount
  return n > 0 ? ` ainsi que ses ${n} position${n > 1 ? 's' : ''}` : ''
}

let timer = null
onMounted(() => {
  load()
  timer = setInterval(() => load({ silent: true }), REFRESH_MS)
})
onBeforeUnmount(() => {
  if (timer !== null) clearInterval(timer)
})
</script>

<template>
  <AdminLayout>
    <h1>Téléphone</h1>
    <p class="muted">
      Les positions envoyées par GPSLogger sont rangées automatiquement par excursion : une nouvelle session commence après 3 heures sans position.
      Vous pouvez nommer une session, la couper en deux (par exemple pour séparer deux sorties rapprochées), l'exporter en GPX ou l'archiver.
    </p>

    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <p v-if="actionError" class="error flights__error" role="alert">{{ actionError }}</p>

    <p v-if="loadError" class="error" role="alert">
      Impossible de charger les sessions.
      <button class="btn btn--ghost btn--small" type="button" @click="load()">Réessayer</button>
    </p>
    <p v-else-if="!sessions" class="muted">Chargement…</p>
    <p v-else-if="sessions.length === 0" class="muted">Aucune session pour le moment : elles apparaissent dès que GPSLogger envoie une position.</p>

    <label v-else-if="archivedCount > 0" class="flights__filter">
      <input v-model="showArchived" type="checkbox" />
      <span>Afficher les trajets archivés ({{ archivedCount }})</span>
    </label>

    <ul v-if="sessions && sessions.length > 0" class="flights">
      <li v-for="session in visible" :key="session.id" class="card flight" :class="{ 'flight--archived': session.archived }">
        <div class="flight__head">
          <div class="flight__id">
            <strong class="flight__number flight__number--text">{{ displayName(session) }}</strong>
            <span class="muted">{{ period(session) }}</span>
          </div>
          <span v-if="session.archived" class="badge">Archivé</span>
        </div>

        <form v-if="renaming && renaming.id === session.id" class="flight__form" @submit.prevent="saveRename(session)">
          <label class="field">
            <span>Nom de la session (vide : nom par défaut)</span>
            <input v-model="renaming.name" type="text" maxlength="200" autocomplete="off" :placeholder="defaultName(session)" />
          </label>
          <div class="flight__buttons">
            <button class="btn btn--small" type="submit" :disabled="busyId !== null">Enregistrer</button>
            <button class="btn btn--ghost btn--small" type="button" @click="renaming = null">Annuler</button>
          </div>
        </form>

        <form v-else-if="splitting && splitting.id === session.id" class="flight__form" @submit.prevent="saveSplit(session)">
          <label class="field">
            <span>Couper à (votre heure locale) : les positions à partir de ce moment passent dans une nouvelle session</span>
            <input v-model="splitting.at" type="datetime-local" required />
          </label>
          <div class="flight__buttons">
            <button class="btn btn--small" type="submit" :disabled="busyId !== null">Couper ici</button>
            <button class="btn btn--ghost btn--small" type="button" @click="splitting.at = toLocalInput(new Date())">Maintenant</button>
            <button class="btn btn--ghost btn--small" type="button" @click="splitting = null">Annuler</button>
          </div>
        </form>

        <div v-else class="flight__actions">
          <div class="flight__buttons">
            <button class="btn btn--ghost btn--small" type="button" :disabled="busyId !== null" @click="startRename(session)">Renommer</button>
            <button class="btn btn--ghost btn--small" type="button" :disabled="busyId !== null" @click="startSplit(session)">Couper ici</button>
            <a v-if="session.pointCount > 0" class="btn btn--ghost btn--small" :href="gpxUrl(session)" download>Exporter GPX</a>
          </div>
          <div class="flight__buttons">
            <button v-if="!session.archived" class="btn btn--ghost btn--small" type="button" :disabled="busyId !== null" title="Masque la session de la liste ; son tracé reste sur la carte" @click="setArchived(session, true)">
              Archiver
            </button>
            <button v-else class="btn btn--ghost btn--small" type="button" :disabled="busyId !== null" @click="setArchived(session, false)">Restaurer</button>
            <button class="btn btn--ghost btn--small btn--danger-text" type="button" :disabled="busyId !== null" @click="toDelete = session">Supprimer</button>
          </div>
        </div>
      </li>
    </ul>

    <ConfirmDialog
      v-if="toDelete"
      :title="`Supprimer « ${displayName(toDelete)} » ?`"
      confirm-label="Supprimer"
      :busy="busyId !== null"
      @confirm="remove"
      @cancel="toDelete = null"
    >
      <p>
        La session sera supprimée<template v-if="toDelete.pointCount > 0">{{ pointsWarning(toDelete) }}</template>,
        <strong>de façon définitive</strong>. Son tracé disparaîtra de la carte.
      </p>
      <p class="muted">Pour le garder sur la carte sans l'afficher dans la liste, archivez-le plutôt.</p>
    </ConfirmDialog>
  </AdminLayout>
</template>
