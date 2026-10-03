<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api, ApiError, uploadFile } from '../api'
import { formatSize } from '../format'
import { createUploadQueue } from '../uploadQueue'
import ConfirmDialog from './ConfirmDialog.vue'

// Contenu d'une galerie : envoi de photos et vidéos (un par un, avec progression et « Réessayer »),
// liste des éléments, suppression. `items` est géré par le parent (v-model:items).
const props = defineProps({
  galleryId: { type: Number, required: true },
  items: { type: Array, required: true },
})
const emit = defineEmits(['update:items'])

// --- Envoi ---------------------------------------------------------------

const queue = createUploadQueue(
  (file, onProgress, signal) => uploadFile(`/api/admin/galleries/${props.galleryId}/items/media`, file, { onProgress, signal }),
  { onUploaded: addItem },
)

function addItem(item) {
  // Garde-fou : jamais deux fois le même élément (ex. rechargement de la galerie pendant un envoi).
  if (!props.items.some((existing) => existing.id === item.id)) emit('update:items', [...props.items, item])
}

function onPick(event) {
  queue.add([...event.target.files])
  event.target.value = '' // permet de re-choisir le même fichier
}

function onDrop(event) {
  queue.add([...(event.dataTransfer?.files ?? [])])
}

function statusText(entry) {
  if (entry.status === 'waiting') return 'En attente'
  if (entry.status === 'uploading') {
    return entry.progress >= 1 ? 'Traitement sur le serveur…' : `Envoi ${Math.round(entry.progress * 100)} %`
  }
  return entry.status === 'done' ? 'Envoyé' : 'Échec'
}

// --- Garde-fous pendant un envoi -------------------------------------------

function onBeforeUnload(event) {
  if (queue.busy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(() => {
  if (queue.busy.value) {
    return window.confirm('Des envois sont en cours. Quitter cette page les interrompra. Quitter quand même ?')
  }
})

// Écran maintenu allumé pendant les envois (un téléphone qui s'éteint suspend l'envoi).
let wakeLock = null
async function updateWakeLock() {
  try {
    if (queue.busy.value && !wakeLock && navigator.wakeLock) {
      wakeLock = await navigator.wakeLock.request('screen')
      wakeLock.addEventListener('release', () => (wakeLock = null))
    } else if (!queue.busy.value && wakeLock) {
      await wakeLock.release()
      wakeLock = null
    }
  } catch {
    /* non pris en charge ou refusé : sans importance */
  }
}
function onVisibilityChange() {
  if (document.visibilityState === 'visible') updateWakeLock()
}
watch(queue.busy, updateWakeLock)

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  queue.dispose()
  wakeLock?.release?.().catch(() => {})
})

// --- Liste et suppression ----------------------------------------------------

const toDelete = ref(null)
const deleting = ref(false)
const error = ref('')

function label(item) {
  if (item.type === 'PHOTO') return 'Photo'
  if (item.type === 'VIDEO') return item.thumbUrl ? 'Vidéo' : 'Vidéo (sans miniature)'
  const text = (item.textMarkdown ?? '').replace(/\s+/g, ' ').trim()
  return text.length > 80 ? `${text.slice(0, 80)}…` : text
}

function details(item) {
  if (item.type === 'TEXT') return 'Texte'
  return [item.originalFilename, formatSize(item.sizeBytes)].filter(Boolean).join(' · ')
}

function deletionTitle(item) {
  return item.type === 'PHOTO' ? 'Supprimer cette photo ?' : item.type === 'VIDEO' ? 'Supprimer cette vidéo ?' : 'Supprimer ce texte ?'
}

async function confirmDeletion() {
  const item = toDelete.value
  deleting.value = true
  error.value = ''
  try {
    await api(`/api/admin/items/${item.id}`, { method: 'DELETE' })
    emit('update:items', props.items.filter((existing) => existing.id !== item.id))
  } catch (e) {
    error.value = e instanceof ApiError && e.body?.message ? e.body.message : 'La suppression a échoué.'
  } finally {
    deleting.value = false
    toDelete.value = null
  }
}
</script>

<template>
  <section class="card admin__section content">
    <h2>Contenu</h2>

    <!-- Zone d'envoi : bouton pour le téléphone, glisser-déposer pour l'ordinateur -->
    <div class="dropzone" @dragover.prevent @drop.prevent="onDrop">
      <label class="btn btn--block">
        Ajouter des photos ou des vidéos
        <input
          class="visually-hidden"
          type="file"
          multiple
          accept="image/jpeg,image/png,image/webp,video/mp4"
          @change="onPick"
        />
      </label>
      <p class="muted dropzone__hint">
        JPEG, PNG, WebP ou MP4. Les fichiers partent un par un : gardez l'écran allumé jusqu'à la fin.
      </p>
    </div>

    <!-- File d'envoi -->
    <div v-if="queue.entries.length > 0" class="uploads">
      <ul class="uploads__list">
        <li v-for="entry in queue.entries" :key="entry.id" class="uploads__row" :class="`uploads__row--${entry.status}`">
          <div class="uploads__main">
            <strong class="uploads__name">{{ entry.name }}</strong>
            <span class="muted">{{ formatSize(entry.size) }} · {{ statusText(entry) }}</span>
            <progress
              v-if="entry.status === 'uploading'"
              max="100"
              :value="Math.round(entry.progress * 100)"
              :aria-label="`Envoi de ${entry.name}`"
            ></progress>
            <span v-if="entry.status === 'error'" class="error uploads__error" role="alert">{{ entry.error }}</span>
          </div>
          <div class="uploads__actions">
            <button v-if="entry.status === 'error'" class="btn btn--small" type="button" @click="queue.retry(entry)">
              Réessayer
            </button>
            <button class="btn btn--ghost btn--small" type="button" @click="queue.cancel(entry)">
              {{ entry.status === 'done' ? 'Masquer' : entry.status === 'error' ? 'Retirer' : 'Annuler' }}
            </button>
          </div>
        </li>
      </ul>
      <div class="uploads__bulk">
        <button
          v-if="queue.entries.some((e) => e.status === 'error')"
          class="btn btn--ghost btn--small"
          type="button"
          @click="queue.retryAll()"
        >
          Tout réessayer
        </button>
        <button
          v-if="queue.entries.some((e) => e.status === 'done')"
          class="btn btn--ghost btn--small"
          type="button"
          @click="queue.clearDone()"
        >
          Masquer les envois terminés
        </button>
      </div>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <!-- Éléments de la galerie, dans l'ordre d'affichage -->
    <p v-if="items.length === 0" class="muted">Aucun contenu pour le moment.</p>
    <ul v-else class="items">
      <li v-for="item in items" :key="item.id" class="items__row">
        <component
          :is="item.type === 'TEXT' ? 'span' : 'a'"
          class="items__thumb"
          :href="item.type === 'TEXT' ? undefined : item.displayUrl || item.originalUrl"
          target="_blank"
          rel="noopener"
          :aria-label="item.type === 'TEXT' ? undefined : 'Voir en grand'"
        >
          <img v-if="item.thumbUrl" :src="item.thumbUrl" alt="" loading="lazy" />
          <span v-else class="items__icon" aria-hidden="true">{{ item.type === 'TEXT' ? '¶' : '▶' }}</span>
        </component>
        <span class="items__text">
          <strong>{{ label(item) }}</strong>
          <span class="muted">{{ details(item) }}</span>
        </span>
        <button class="btn btn--ghost btn--icon" type="button" aria-label="Supprimer" @click="toDelete = item">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" /></svg>
        </button>
      </li>
    </ul>

    <ConfirmDialog
      v-if="toDelete"
      :title="deletionTitle(toDelete)"
      confirm-label="Supprimer"
      :busy="deleting"
      @confirm="confirmDeletion"
      @cancel="toDelete = null"
    >
      <p v-if="toDelete.type === 'TEXT'">Ce texte sera supprimé de façon définitive.</p>
      <p v-else>Le fichier sera supprimé <strong>de façon définitive</strong>, y compris sur le serveur.</p>
    </ConfirmDialog>
  </section>
</template>
