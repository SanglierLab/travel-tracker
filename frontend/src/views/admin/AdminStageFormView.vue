<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import LocationPicker from '../../components/LocationPicker.vue'
import StageIcon from '../../components/StageIcon.vue'
import { api, ApiError } from '../../api'
import { KIND_ORDER, STAGE_KINDS, isTrip } from '../../stageKinds'

const route = useRoute()
const router = useRouter()

// null = création ; sinon identifiant de l'étape modifiée.
const id = computed(() => (route.params.id ? Number(route.params.id) : null))

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const form = reactive({ name: '', kind: 'PLACE', startDate: today(), endDate: today(), start: null, end: null })
const stage = ref(null) // étape chargée (édition) : indique notamment si un tracé GPX est enregistré
const center = ref(null) // création : on centre les cartes près de la dernière étape
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const confirmDelete = ref(false)
const error = ref('')
const saved = ref(false)

const trip = computed(() => isTrip(form.kind))
const endCenter = computed(() => (form.start ? [form.start.lat, form.start.lon] : center.value))

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

// Vrai pendant le remplissage du formulaire par un chargement : la synchronisation des dates ne doit pas s'en mêler.
let loadingForm = false

async function fill(values) {
  loadingForm = true
  Object.assign(form, values)
  await nextTick() // les observateurs ont vu ce remplissage
  loadingForm = false
}

async function load() {
  error.value = ''
  saved.value = false
  stage.value = null
  if (id.value === null) {
    await fill({ name: '', kind: 'PLACE', startDate: today(), endDate: today(), start: null, end: null })
    try {
      const list = await api('/api/admin/stages')
      const last = list[list.length - 1]
      if (last) center.value = [last.endLatitude ?? last.startLatitude, last.endLongitude ?? last.startLongitude]
    } catch {
      /* le centrage est un confort : on ignore l'échec */
    }
    return
  }
  loading.value = true
  try {
    const loaded = await api(`/api/admin/stages/${id.value}`)
    stage.value = loaded
    await fill({
      name: loaded.name,
      kind: loaded.kind,
      startDate: loaded.startDate,
      endDate: loaded.endDate,
      start: { lat: Number(loaded.startLatitude), lon: Number(loaded.startLongitude) },
      end: loaded.endLatitude == null ? null : { lat: Number(loaded.endLatitude), lon: Number(loaded.endLongitude) },
    })
  } catch (e) {
    error.value = e instanceof ApiError && e.status === 404 ? 'Cette étape n’existe pas (ou plus).' : 'Impossible de charger l’étape.'
  } finally {
    loading.value = false
  }
}
// Après la création, on arrive sur /admin/etapes/:id avec le même composant : on recharge.
watch(id, load, { immediate: true })

// La date de fin suit la date de début tant qu'on n'y a pas touché (un trajet d'une journée, un séjour d'une nuit) :
// elle bouge si elle était égale à l'ancienne date de début, ou si elle devenait antérieure au début.
watch(
  () => form.startDate,
  (start, previous) => {
    if (loadingForm) return
    if (form.endDate === previous || form.endDate < start) form.endDate = start
  },
)

async function save() {
  error.value = ''
  saved.value = false
  if (!form.start) {
    error.value = trip.value
      ? 'Placez la position de départ sur la carte, ou utilisez « Me positionner ».'
      : 'Placez l’étape sur la carte, ou utilisez « Me positionner ».'
    return
  }
  if (trip.value && !form.end) {
    error.value = 'Placez aussi la position d’arrivée du trajet sur la carte.'
    return
  }
  const body = {
    name: form.name,
    kind: form.kind,
    startDate: form.startDate,
    endDate: form.endDate,
    startLatitude: form.start.lat,
    startLongitude: form.start.lon,
    endLatitude: trip.value ? form.end.lat : null,
    endLongitude: trip.value ? form.end.lon : null,
  }
  saving.value = true
  try {
    if (id.value === null) {
      const created = await api('/api/admin/stages', { method: 'POST', body })
      router.replace({ name: 'admin-stage', params: { id: created.id } })
    } else {
      stage.value = await api(`/api/admin/stages/${id.value}`, { method: 'PUT', body })
      if (!trip.value) form.end = null
      saved.value = true
    }
  } catch (e) {
    error.value = messageOf(e, "L'enregistrement a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    saving.value = false
  }
}

async function remove() {
  deleting.value = true
  try {
    await api(`/api/admin/stages/${id.value}`, { method: 'DELETE' })
    router.replace({ name: 'admin-stages' })
  } catch (e) {
    confirmDelete.value = false
    error.value = messageOf(e, 'La suppression a échoué.')
  } finally {
    deleting.value = false
  }
}

// Passer un trajet en lieu fixe efface sa position d'arrivée et son tracé : on le dit avant d'enregistrer.
const losesTripData = computed(() => id.value !== null && stage.value && isTrip(stage.value.kind) && !trip.value)
</script>

<template>
  <AdminLayout>
    <p><RouterLink :to="{ name: 'admin-stages' }">‹ Étapes</RouterLink></p>
    <h1>{{ id === null ? 'Nouvelle étape' : 'Modifier l’étape' }}</h1>

    <p v-if="loading" class="muted">Chargement…</p>
    <p v-else-if="id !== null && !stage" class="error" role="alert">{{ error }}</p>

    <template v-else>
      <form class="card" @submit.prevent="save">
        <div class="field">
          <span>Type d'étape</span>
          <div class="kinds" role="group" aria-label="Type d'étape">
            <button
              v-for="kind in KIND_ORDER"
              :key="kind"
              type="button"
              class="kinds__option"
              :aria-pressed="form.kind === kind"
              @click="form.kind = kind"
            >
              <StageIcon :kind="kind" />
              <span>{{ STAGE_KINDS[kind].label }}</span>
            </button>
          </div>
        </div>

        <label class="field">
          <span>Nom</span>
          <input v-model="form.name" type="text" maxlength="200" required :placeholder="trip ? 'Train Tokyo → Shimoda' : 'Tokyo'" />
        </label>
        <label class="field">
          <span>{{ trip ? 'Date de départ' : 'Arrivée le' }}</span>
          <input v-model="form.startDate" type="date" required />
        </label>
        <label class="field">
          <span>{{ trip ? 'Date d’arrivée' : 'Départ le' }}</span>
          <input v-model="form.endDate" type="date" required :min="form.startDate" />
        </label>

        <div class="field">
          <span>{{ trip ? 'Position de départ' : 'Position sur la carte' }}</span>
          <LocationPicker v-model="form.start" :center="center" />
        </div>
        <div v-if="trip" class="field">
          <span>Position d'arrivée</span>
          <LocationPicker v-model="form.end" :center="endCenter" />
        </div>

        <p v-if="stage?.hasPath && trip" class="muted">Un tracé GPX de {{ stage.pathPoints }} points est enregistré pour ce trajet.</p>
        <p v-if="losesTripData" class="stages__warning" role="status">
          En passant cette étape en lieu fixe, sa position d'arrivée{{ stage.hasPath ? ' et son tracé GPX seront supprimés' : ' sera supprimée' }} à l'enregistrement.
        </p>

        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="saved" class="success" role="status">Modifications enregistrées.</p>
        <button class="btn" type="submit" :disabled="saving">
          {{ saving ? 'Enregistrement…' : id === null ? 'Créer l’étape' : 'Enregistrer' }}
        </button>
      </form>

      <section v-if="id !== null" class="card admin__section">
        <h2>Actions</h2>
        <button class="btn btn--ghost btn--danger-text" type="button" @click="confirmDelete = true">Supprimer l'étape</button>
      </section>
    </template>

    <ConfirmDialog
      v-if="confirmDelete"
      :title="`Supprimer l'étape « ${form.name} » ?`"
      confirm-label="Supprimer définitivement"
      :busy="deleting"
      @confirm="remove"
      @cancel="confirmDelete = false"
    >
      <p>Cette étape sera supprimée <strong>de façon définitive</strong>. Elle disparaîtra de la carte et du fil des photos.</p>
    </ConfirmDialog>
  </AdminLayout>
</template>
