<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import LocationPicker from '../../components/LocationPicker.vue'
import { api, ApiError } from '../../api'

const route = useRoute()
const router = useRouter()

// null = création ; sinon identifiant de la galerie modifiée.
const id = computed(() => (route.params.id ? Number(route.params.id) : null))

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const form = reactive({ title: '', placeName: '', galleryDate: today(), position: null })
const latText = ref('')
const lonText = ref('')

const detail = ref(null) // galerie chargée (édition) : sert à afficher son contenu
const center = ref(null) // création : on centre la carte près de la dernière galerie
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const confirmDelete = ref(false)
const error = ref('')
const saved = ref(false)

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

async function load() {
  error.value = ''
  saved.value = false
  detail.value = null
  if (id.value === null) {
    Object.assign(form, { title: '', placeName: '', galleryDate: today(), position: null })
    try {
      const list = await api('/api/admin/galleries')
      if (list.length > 0) center.value = [list[0].latitude, list[0].longitude]
    } catch {
      /* le centrage est un confort : on ignore l'échec */
    }
    return
  }
  loading.value = true
  try {
    const gallery = await api(`/api/admin/galleries/${id.value}`)
    detail.value = gallery
    Object.assign(form, {
      title: gallery.title,
      placeName: gallery.placeName,
      galleryDate: gallery.galleryDate,
      position: { lat: Number(gallery.latitude), lon: Number(gallery.longitude) },
    })
  } catch (e) {
    error.value = e instanceof ApiError && e.status === 404 ? 'Cette galerie n’existe pas (ou plus).' : 'Impossible de charger la galerie.'
  } finally {
    loading.value = false
  }
}
// Après la création, on arrive sur /admin/galeries/:id avec le même composant : on recharge.
watch(id, load, { immediate: true })

// --- Coordonnées saisies à la main (secours, utile sur ordinateur) ---
watch(
  () => form.position,
  (position) => {
    if (!position) {
      latText.value = ''
      lonText.value = ''
      return
    }
    if (Number(latText.value) !== position.lat) latText.value = String(position.lat)
    if (Number(lonText.value) !== position.lon) lonText.value = String(position.lon)
  },
  { immediate: true },
)

function onCoordinatesTyped() {
  const lat = parseFloat(latText.value)
  const lon = parseFloat(lonText.value)
  if (Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
    form.position = { lat, lon }
  }
}

// --- Enregistrement ---
async function save() {
  error.value = ''
  saved.value = false
  if (!form.position) {
    error.value = 'Placez le point de la galerie sur la carte, ou utilisez « Me positionner ».'
    return
  }
  const body = {
    title: form.title,
    placeName: form.placeName,
    galleryDate: form.galleryDate,
    latitude: form.position.lat,
    longitude: form.position.lon,
  }
  saving.value = true
  try {
    if (id.value === null) {
      const created = await api('/api/admin/galleries', { method: 'POST', body })
      router.replace({ name: 'admin-gallery', params: { id: created.id } })
    } else {
      detail.value = await api(`/api/admin/galleries/${id.value}`, { method: 'PUT', body })
      saved.value = true
    }
  } catch (e) {
    error.value = messageOf(e, "L'enregistrement a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    saving.value = false
  }
}

// --- Suppression (avec confirmation) ---
const contentSummary = computed(() => {
  const items = detail.value?.items ?? []
  const count = (type) => items.filter((item) => item.type === type).length
  const part = (n, one, many) => (n === 0 ? null : `${n} ${n === 1 ? one : many}`)
  return [part(count('PHOTO'), 'photo', 'photos'), part(count('VIDEO'), 'vidéo', 'vidéos'), part(count('TEXT'), 'texte', 'textes')]
    .filter(Boolean)
    .join(', ')
})

async function remove() {
  deleting.value = true
  try {
    await api(`/api/admin/galleries/${id.value}`, { method: 'DELETE' })
    router.replace({ name: 'admin-galleries' })
  } catch (e) {
    confirmDelete.value = false
    error.value = messageOf(e, 'La suppression a échoué.')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <AdminLayout>
    <p><RouterLink :to="{ name: 'admin-galleries' }">‹ Galeries</RouterLink></p>
    <h1>{{ id === null ? 'Nouvelle galerie' : 'Modifier la galerie' }}</h1>

    <p v-if="loading" class="muted">Chargement…</p>
    <p v-else-if="id !== null && !detail" class="error" role="alert">{{ error }}</p>

    <template v-else>
      <form class="card" @submit.prevent="save">
        <label class="field">
          <span>Titre</span>
          <input v-model="form.title" type="text" maxlength="200" required placeholder="Palais impérial" />
        </label>
        <label class="field">
          <span>Lieu</span>
          <input v-model="form.placeName" type="text" maxlength="200" required placeholder="Tokyo" />
        </label>
        <label class="field">
          <span>Date</span>
          <input v-model="form.galleryDate" type="date" required />
        </label>

        <div class="field">
          <span>Position sur la carte</span>
          <LocationPicker v-model="form.position" :center="center" />
          <details class="picker__manual">
            <summary>Saisir les coordonnées à la main</summary>
            <label class="field">
              <span>Latitude</span>
              <input v-model="latText" type="number" step="any" min="-90" max="90" inputmode="decimal" @input="onCoordinatesTyped" />
            </label>
            <label class="field">
              <span>Longitude</span>
              <input v-model="lonText" type="number" step="any" min="-180" max="180" inputmode="decimal" @input="onCoordinatesTyped" />
            </label>
          </details>
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="saved" class="success" role="status">Modifications enregistrées.</p>
        <button class="btn" type="submit" :disabled="saving">
          {{ saving ? 'Enregistrement…' : id === null ? 'Créer la galerie' : 'Enregistrer' }}
        </button>
      </form>

      <section v-if="id !== null" class="card admin__section">
        <h2>Contenu</h2>
        <p class="muted">{{ contentSummary || 'Aucun contenu pour le moment.' }}</p>
        <p><RouterLink :to="{ name: 'gallery', params: { id } }">Voir sur le site</RouterLink></p>
        <button class="btn btn--ghost btn--danger-text" type="button" @click="confirmDelete = true">
          Supprimer la galerie
        </button>
      </section>
    </template>

    <ConfirmDialog
      v-if="confirmDelete"
      title="Supprimer cette galerie ?"
      confirm-label="Supprimer définitivement"
      :busy="deleting"
      @confirm="remove"
      @cancel="confirmDelete = false"
    >
      <p>
        « {{ form.title }} » sera supprimée
        <template v-if="contentSummary">avec son contenu ({{ contentSummary }}) </template>
        <strong>de façon définitive</strong>, y compris les fichiers sur le serveur.
      </p>
    </ConfirmDialog>
  </AdminLayout>
</template>
