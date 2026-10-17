<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import { api, ApiError } from '../../api'
import { formatDateTime, timeAgo } from '../../format'

// Suivi d'une montre Garmin (LiveTrack) : on colle le lien « à partager avec ses amis » de l'activité, puis on active le suivi.
// Chaque activité crée un nouveau lien. Une seule session peut être suivie à la fois (le serveur refuse la deuxième).
// Le lien contient un jeton secret : il n'est jamais renvoyé par le serveur, donc jamais affiché ici après l'enregistrement.

const sessions = ref(null)
const loadError = ref(false)
const actionError = ref('')
const notice = ref('')

const form = reactive({ link: '', name: '' })
const creating = ref(false)
const busyId = ref(null)
const toDelete = ref(null)
const REFRESH_MS = 15000

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

async function load({ silent = false } = {}) {
  if (!silent) loadError.value = false
  try {
    sessions.value = await api('/api/admin/livetracks')
  } catch {
    if (!silent) loadError.value = true
  }
}

function replace(updated) {
  sessions.value = sessions.value.map((session) => (session.id === updated.id ? updated : session))
}

async function create() {
  actionError.value = ''
  notice.value = ''
  creating.value = true
  try {
    const created = await api('/api/admin/livetracks', {
      method: 'POST',
      body: { link: form.link, name: form.name || null },
    })
    notice.value = `Session « ${created.label} » enregistrée. Activez son suivi quand l'activité est lancée.`
    form.link = ''
    form.name = ''
    await load({ silent: true })
  } catch (e) {
    actionError.value = messageOf(e, "L'enregistrement a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    creating.value = false
  }
}

async function toggle(session) {
  actionError.value = ''
  notice.value = ''
  busyId.value = session.id
  const action = session.status === 'ACTIVE' ? 'stop' : 'start'
  try {
    replace(await api(`/api/admin/livetracks/${session.id}/${action}`, { method: 'POST' }))
  } catch (e) {
    actionError.value = messageOf(e, "L'action a échoué. Vérifiez votre connexion et réessayez.")
    await load({ silent: true })
  } finally {
    busyId.value = null
  }
}

async function remove() {
  const session = toDelete.value
  busyId.value = session.id
  actionError.value = ''
  try {
    await api(`/api/admin/livetracks/${session.id}`, { method: 'DELETE' })
    sessions.value = sessions.value.filter((other) => other.id !== session.id)
  } catch (e) {
    actionError.value = messageOf(e, 'La suppression a échoué.')
  } finally {
    busyId.value = null
    toDelete.value = null
  }
}

// --- Archivage et export GPX (communs à tous les trajets : /api/admin/trips/:id/...) ---------

const showArchived = ref(false)
const archivedCount = computed(() => (sessions.value ?? []).filter((session) => session.archived).length)
const visible = computed(() => (sessions.value ?? []).filter((session) => showArchived.value || !session.archived))

async function setArchived(session, archived) {
  actionError.value = ''
  notice.value = ''
  busyId.value = session.id
  try {
    await api(`/api/admin/trips/${session.id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' })
    await load({ silent: true })
  } catch (e) {
    actionError.value = messageOf(e, "L'action a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    busyId.value = null
  }
}

/** Le fichier est téléchargé par le navigateur (même origine, session admin). */
function gpxUrl(session) {
  return `/api/admin/trips/${session.id}/gpx`
}

const STATUS = { PLANNED: 'Prévu', ACTIVE: 'Suivi en cours', FINISHED: 'Suivi arrêté' }

function positions(session) {
  if (session.pointCount === 0) return 'Aucune position enregistrée'
  const count = `${session.pointCount} position${session.pointCount > 1 ? 's' : ''}`
  return session.lastPointAt ? `${count} · dernière ${timeAgo(session.lastPointAt)}` : count
}

function pointsWarning(session) {
  const n = session.pointCount
  return n > 0 ? ` ainsi que ses ${n} position${n > 1 ? 's' : ''} enregistrée${n > 1 ? 's' : ''}` : ''
}

const hasActive = computed(() => (sessions.value ?? []).some((session) => session.status === 'ACTIVE'))
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
    <h1>Montre Garmin</h1>
    <p class="muted">
      Sur la montre, démarrez une activité avec LiveTrack, puis copiez le lien de partage (le lien court https://gar.mn/… ou le lien long livetrack.garmin.com) et collez-le ici ; un lien court est converti automatiquement en lien long. Chaque activité a son propre lien. Une seule session peut être suivie à la fois. Pendant le suivi, les positions de la montre s'affichent sur la carte du site. Ce suivi repose sur la page publique de Garmin, sans API officielle : il peut cesser de fonctionner si Garmin la modifie.
    </p>

    <form class="card" @submit.prevent="create">
      <h2>Ajouter une session</h2>
      <label class="field">
        <span>Lien de partage LiveTrack (court ou long)</span>
        <input
          v-model="form.link"
          type="url"
          required
          autocomplete="off"
          autocapitalize="off"
          spellcheck="false"
          placeholder="https://gar.mn/… ou https://livetrack.garmin.com/session/…"
        />
      </label>
      <label class="field">
        <span>Nom (facultatif)</span>
        <input v-model="form.name" type="text" maxlength="100" autocomplete="off" placeholder="Randonnée du col" />
      </label>
      <p v-if="notice" class="success" role="status">{{ notice }}</p>
      <button class="btn" type="submit" :disabled="creating">{{ creating ? 'Enregistrement…' : 'Enregistrer la session' }}</button>
    </form>

    <p v-if="actionError" class="error flights__error" role="alert">{{ actionError }}</p>

    <p v-if="loadError" class="error" role="alert">
      Impossible de charger les sessions.
      <button class="btn btn--ghost btn--small" type="button" @click="load()">Réessayer</button>
    </p>
    <p v-else-if="!sessions" class="muted">Chargement…</p>
    <p v-else-if="sessions.length === 0" class="muted">Aucune session enregistrée pour le moment.</p>

    <label v-else-if="archivedCount > 0" class="flights__filter">
      <input v-model="showArchived" type="checkbox" />
      <span>Afficher les trajets archivés ({{ archivedCount }})</span>
    </label>

    <ul v-if="sessions && sessions.length > 0" class="flights">
      <li v-for="session in visible" :key="session.id" class="card flight flight--garmin" :class="{ 'flight--active': session.status === 'ACTIVE', 'flight--archived': session.archived }">
        <div class="flight__head">
          <div class="flight__id">
            <strong class="flight__number">{{ session.label }}</strong>
            <span class="muted">Ajoutée le {{ formatDateTime(session.registeredAt) }} · réf. {{ session.reference }}</span>
          </div>
          <span class="badge" :class="{ 'badge--active': session.status === 'ACTIVE' }">{{ session.archived ? 'Archivé' : STATUS[session.status] }}</span>
        </div>
        <p class="muted flight__points">{{ positions(session) }}</p>
        <div class="flight__actions">
          <button
            class="switch"
            type="button"
            role="switch"
            :aria-checked="session.status === 'ACTIVE'"
            :aria-label="`Suivi de la session ${session.label}`"
            :disabled="busyId !== null || session.archived"
            @click="toggle(session)"
          >
            <span class="switch__track" aria-hidden="true"><span class="switch__knob"></span></span>
            <span>{{ session.status === 'ACTIVE' ? 'Activé' : 'Désactivé' }}</span>
          </button>
          <div class="flight__buttons">
            <a
              v-if="session.pointCount > 0"
              class="btn btn--ghost btn--small"
              :href="gpxUrl(session)"
              download
            >
              Exporter GPX
            </a>
            <button
              v-if="!session.archived"
              class="btn btn--ghost btn--small"
              type="button"
              :disabled="session.status === 'ACTIVE' || busyId !== null"
              :title="session.status === 'ACTIVE' ? 'Arrêtez le suivi pour pouvoir archiver cette session' : 'Masque cette session de la liste ; son tracé reste sur la carte'"
              @click="setArchived(session, true)"
            >
              Archiver
            </button>
            <button v-else class="btn btn--ghost btn--small" type="button" :disabled="busyId !== null" @click="setArchived(session, false)">
              Restaurer
            </button>
            <button
              class="btn btn--ghost btn--small btn--danger-text"
              type="button"
              :disabled="session.status === 'ACTIVE' || busyId !== null"
              :title="session.status === 'ACTIVE' ? 'Arrêtez le suivi pour pouvoir supprimer cette session' : undefined"
              @click="toDelete = session"
            >
              Supprimer
            </button>
          </div>
        </div>
      </li>
    </ul>

    <ConfirmDialog
      v-if="toDelete"
      :title="`Supprimer la session « ${toDelete.label} » ?`"
      confirm-label="Supprimer"
      :busy="busyId !== null"
      @confirm="remove"
      @cancel="toDelete = null"
    >
      <p>
        La session « {{ toDelete.label }} » sera supprimée<template v-if="toDelete.pointCount > 0">{{ pointsWarning(toDelete) }}</template>,
        <strong>de façon définitive</strong>. Son tracé disparaîtra de la carte.
      </p>
      <p class="muted">Pour le garder sur la carte sans l'afficher dans la liste, archivez-le plutôt.</p>
    </ConfirmDialog>
  </AdminLayout>
</template>
