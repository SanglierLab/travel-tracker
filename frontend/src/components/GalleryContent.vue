<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api, ApiError, uploadFile } from '../api'
import { formatSize } from '../format'
import { createUploadQueue } from '../uploadQueue'
import ConfirmDialog from './ConfirmDialog.vue'
import TextEditorDialog from './TextEditorDialog.vue'

// Contenu d'une galerie : envoi de photos et vidéos (un par un, avec progression et « Réessayer »),
// blocs de texte, réordonnancement, suppression. `items` est géré par le parent (v-model:items).
const props = defineProps({
  galleryId: { type: Number, required: true },
  items: { type: Array, required: true },
})
const emit = defineEmits(['update:items'])

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

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

// --- Textes ---------------------------------------------------------------------

const editor = ref(null) // { item } : item = null pour un nouveau texte
const editorSaving = ref(false)
const editorError = ref('')

function openEditor(item = null) {
  editor.value = { item }
  editorError.value = ''
}

async function saveText(markdown) {
  editorSaving.value = true
  editorError.value = ''
  try {
    const item = editor.value.item
    if (item) {
      const updated = await api(`/api/admin/items/${item.id}`, { method: 'PUT', body: { markdown } })
      emit('update:items', props.items.map((existing) => (existing.id === updated.id ? updated : existing)))
    } else {
      addItem(await api(`/api/admin/galleries/${props.galleryId}/items/text`, { method: 'POST', body: { markdown } }))
    }
    editor.value = null
  } catch (e) {
    editorError.value = messageOf(e, "L'enregistrement a échoué. Vérifiez votre connexion et réessayez.")
  } finally {
    editorSaving.value = false
  }
}

// --- Réordonnancement ---------------------------------------------------------

const moving = ref(false)

async function move(index, direction) {
  const target = index + (direction === 'UP' ? -1 : 1)
  if (moving.value || target < 0 || target >= props.items.length) return
  const first = props.items[index]
  const second = props.items[target]
  moving.value = true
  error.value = ''
  try {
    await api(`/api/admin/items/${first.id}/move?direction=${direction}`, { method: 'POST' })
    // Le serveur échange les deux éléments voisins : on fait de même ici, sans recharger la galerie.
    const next = [...props.items]
    next[index] = { ...second, sortOrder: first.sortOrder }
    next[target] = { ...first, sortOrder: second.sortOrder }
    emit('update:items', next)
    await nextTick()
    document.getElementById(`item-${first.id}`)?.scrollIntoView({ block: 'nearest' })
  } catch (e) {
    error.value = messageOf(e, 'Le déplacement a échoué. Vérifiez votre connexion et réessayez.')
  } finally {
    moving.value = false
  }
}

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
    error.value = messageOf(e, 'La suppression a échoué.')
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
      <div class="dropzone__buttons">
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
        <button class="btn btn--ghost btn--block" type="button" @click="openEditor()">Ajouter un texte</button>
      </div>
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

    <!-- Éléments de la galerie, dans l'ordre d'affichage du site -->
    <p v-if="items.length === 0" class="muted">Aucun contenu pour le moment.</p>
    <ul v-else class="items">
      <li v-for="(item, index) in items" :id="`item-${item.id}`" :key="item.id" class="items__row">
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
        <span class="items__actions">
          <button
            class="btn btn--ghost btn--icon"
            type="button"
            aria-label="Monter"
            :disabled="index === 0 || moving"
            @click="move(index, 'UP')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 15l6-6 6 6" /></svg>
          </button>
          <button
            class="btn btn--ghost btn--icon"
            type="button"
            aria-label="Descendre"
            :disabled="index === items.length - 1 || moving"
            @click="move(index, 'DOWN')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
          </button>
          <button
            v-if="item.type === 'TEXT'"
            class="btn btn--ghost btn--icon"
            type="button"
            aria-label="Modifier le texte"
            @click="openEditor(item)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16zM13 7l4 4" /></svg>
          </button>
          <button class="btn btn--ghost btn--icon" type="button" aria-label="Supprimer" @click="toDelete = item">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" /></svg>
          </button>
        </span>
      </li>
    </ul>

    <TextEditorDialog
      v-if="editor"
      :title="editor.item ? 'Modifier le texte' : 'Nouveau texte'"
      :initial="editor.item?.textMarkdown ?? ''"
      :saving="editorSaving"
      :error="editorError"
      @save="saveText"
      @cancel="editor = null"
    />

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
