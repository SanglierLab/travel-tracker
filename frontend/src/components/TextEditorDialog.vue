<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { api, ApiError } from '../api'

// Rédaction d'un bloc de texte (markdown). Boutons de mise en forme pour le téléphone (taper ** au clavier
// tactile est pénible) et aperçu fourni par le serveur : identique à ce que verront les visiteurs.
const props = defineProps({
  title: { type: String, required: true },
  initial: { type: String, default: '' },
  saving: { type: Boolean, default: false },
  error: { type: String, default: '' },
})
const emit = defineEmits(['save', 'cancel'])

const MAX_LENGTH = 20000

const dialog = ref(null)
const area = ref(null)
const text = ref(props.initial)
const preview = ref(false)
const previewState = ref('idle') // idle | loading | ok | error
const previewHtml = ref('')

const canSave = computed(() => text.value.trim().length > 0 && !props.saving)

onMounted(() => {
  const element = dialog.value
  if (typeof element.showModal === 'function') element.showModal()
  else element.setAttribute('open', '')
  area.value?.focus()
})

function requestCancel() {
  if (text.value !== props.initial && !window.confirm('Abandonner les modifications de ce texte ?')) return
  emit('cancel')
}

// --- Mise en forme -----------------------------------------------------------

async function replaceRange(start, end, replacement, selectFrom, selectTo) {
  text.value = text.value.slice(0, start) + replacement + text.value.slice(end)
  await nextTick()
  area.value.focus()
  area.value.setSelectionRange(selectFrom, selectTo)
}

function wrap(before, after, placeholder) {
  const { selectionStart: start, selectionEnd: end } = area.value
  const selected = text.value.slice(start, end) || placeholder
  const from = start + before.length
  replaceRange(start, end, before + selected + after, from, from + selected.length)
}

function insertLink() {
  const { selectionStart: start, selectionEnd: end } = area.value
  const label = text.value.slice(start, end) || 'texte du lien'
  const prefix = `[${label}](https://`
  // Le curseur est placé juste après « https:// » : il ne reste qu'à saisir l'adresse.
  replaceRange(start, end, `${prefix})`, start + prefix.length, start + prefix.length)
}

function bulletList() {
  const { selectionStart: start, selectionEnd: end } = area.value
  const lineStart = text.value.lastIndexOf('\n', start - 1) + 1
  let lineEnd = text.value.indexOf('\n', end)
  if (lineEnd === -1) lineEnd = text.value.length
  const block = text.value.slice(lineStart, lineEnd) || 'élément'
  const list = block
    .split('\n')
    .map((line) => (line.startsWith('- ') ? line : `- ${line}`))
    .join('\n')
  replaceRange(lineStart, lineEnd, list, lineStart, lineStart + list.length)
}

// --- Aperçu -----------------------------------------------------------------

async function showPreview() {
  preview.value = true
  if (text.value.trim() === '') {
    previewState.value = 'idle'
    return
  }
  previewState.value = 'loading'
  try {
    const response = await api('/api/admin/markdown/preview', { method: 'POST', body: { markdown: text.value } })
    previewHtml.value = response.html
    previewState.value = 'ok'
  } catch (e) {
    previewState.value = 'error'
    previewHtml.value = e instanceof ApiError && e.body?.message ? e.body.message : ''
  }
}

async function showEditor() {
  preview.value = false
  await nextTick()
  area.value?.focus()
}
</script>

<template>
  <dialog ref="dialog" class="dialog dialog--editor" @cancel.prevent="requestCancel">
    <div class="dialog__inner">
      <h2 class="dialog__title">{{ title }}</h2>

      <div class="editor__tabs" role="tablist">
        <button class="editor__tab" type="button" role="tab" :aria-selected="!preview" @click="showEditor">Écrire</button>
        <button class="editor__tab" type="button" role="tab" :aria-selected="preview" @click="showPreview">Aperçu</button>
      </div>

      <template v-if="!preview">
        <div class="editor__tools" role="toolbar" aria-label="Mise en forme">
          <button class="btn btn--ghost btn--small" type="button" aria-label="Gras" @click="wrap('**', '**', 'texte')"><strong>G</strong></button>
          <button class="btn btn--ghost btn--small" type="button" aria-label="Italique" @click="wrap('*', '*', 'texte')"><em>I</em></button>
          <button class="btn btn--ghost btn--small" type="button" aria-label="Liste à puces" @click="bulletList">• Liste</button>
          <button class="btn btn--ghost btn--small" type="button" aria-label="Lien" @click="insertLink">Lien</button>
        </div>
        <textarea
          ref="area"
          v-model="text"
          class="editor__area"
          rows="9"
          :maxlength="MAX_LENGTH"
          placeholder="Racontez un moment du voyage…"
        ></textarea>
        <p class="muted editor__count">{{ text.length }} / 20 000 caractères</p>
      </template>

      <div v-else class="editor__preview" aria-live="polite">
        <p v-if="previewState === 'loading'" class="muted">Chargement de l'aperçu…</p>
        <p v-else-if="previewState === 'idle'" class="muted">Rien à afficher pour l'instant.</p>
        <p v-else-if="previewState === 'error'" class="error">
          {{ previewHtml || "L'aperçu n'est pas disponible pour le moment." }}
        </p>
        <!-- html : produit et assaini par le serveur (même rendu que la partie publique) -->
        <div v-else class="prose" v-html="previewHtml"></div>
      </div>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="dialog__actions">
        <button class="btn btn--ghost" type="button" :disabled="saving" @click="requestCancel">Annuler</button>
        <button class="btn" type="button" :disabled="!canSave" @click="emit('save', text)">
          {{ saving ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
      </div>
    </div>
  </dialog>
</template>
