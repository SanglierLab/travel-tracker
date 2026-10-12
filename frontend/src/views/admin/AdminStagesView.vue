<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import StageIcon from '../../components/StageIcon.vue'
import { api, ApiError } from '../../api'
import { formatDateRange } from '../../format'
import { STAGE_KINDS } from '../../stageKinds'

// Étapes du voyage, dans l'ordre manuel : c'est l'ordre du tracé fléché sur la carte.

const stages = ref(null)
const loadError = ref(false)
const error = ref('')
const busy = ref(false)
const toDelete = ref(null)

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function messageOf(e, fallback) {
  return e instanceof ApiError && e.body?.message ? e.body.message : fallback
}

async function load() {
  loadError.value = false
  try {
    stages.value = await api('/api/admin/stages')
  } catch {
    loadError.value = true
  }
}
onMounted(load)

// Les dates ne suivent pas l'ordre de la liste : une étape commence avant la précédente.
const outOfOrder = computed(() => {
  const list = stages.value ?? []
  return list.some((stage, i) => i > 0 && stage.startDate < list[i - 1].startDate)
})

async function move(stage, direction) {
  error.value = ''
  busy.value = true
  try {
    await api(`/api/admin/stages/${stage.id}/move?direction=${direction}`, { method: 'POST' })
    await load() // le serveur renumérote : on réaffiche son ordre
  } catch (e) {
    error.value = messageOf(e, 'Le déplacement a échoué. Vérifiez votre connexion et réessayez.')
  } finally {
    busy.value = false
  }
}

async function sortByDate() {
  error.value = ''
  busy.value = true
  try {
    stages.value = await api('/api/admin/stages/sort-by-date', { method: 'POST' })
  } catch (e) {
    error.value = messageOf(e, 'Le tri a échoué. Vérifiez votre connexion et réessayez.')
  } finally {
    busy.value = false
  }
}

async function remove() {
  const stage = toDelete.value
  busy.value = true
  error.value = ''
  try {
    await api(`/api/admin/stages/${stage.id}`, { method: 'DELETE' })
    stages.value = stages.value.filter((other) => other.id !== stage.id)
  } catch (e) {
    error.value = messageOf(e, 'La suppression a échoué.')
  } finally {
    busy.value = false
    toDelete.value = null
  }
}
</script>

<template>
  <AdminLayout>
    <div class="admin__head">
      <h1>Étapes</h1>
      <RouterLink class="btn" :to="{ name: 'admin-stage-new' }">+ Nouvelle</RouterLink>
    </div>
    <p class="muted">
      Les grandes étapes du voyage, lieux ou trajets. Elles sont reliées dans cet ordre par un tracé fléché sur la carte du site.
    </p>

    <p v-if="outOfOrder" class="stages__warning" role="status">
      Les dates ne suivent pas l'ordre de la liste.
      <button class="btn btn--ghost btn--small" type="button" :disabled="busy" @click="sortByDate">Trier par date</button>
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <p v-if="loadError" class="error" role="alert">
      Impossible de charger les étapes.
      <button class="btn btn--ghost btn--small" type="button" @click="load">Réessayer</button>
    </p>
    <p v-else-if="!stages" class="muted">Chargement…</p>
    <p v-else-if="stages.length === 0" class="muted">Aucune étape pour le moment. Ajoutez la première !</p>

    <template v-else>
      <ol class="stages">
        <li v-for="(stage, index) in stages" :key="stage.id" class="stage-row">
          <span class="stage-row__icon" :title="STAGE_KINDS[stage.kind].label"><StageIcon :kind="stage.kind" /></span>
          <span class="stage-row__text">
            <strong>{{ stage.name }}</strong>
            <span class="muted">
              {{ STAGE_KINDS[stage.kind].label }} · {{ formatDateRange(stage.startDate, stage.endDate) }}
              <span v-if="stage.startDate > today()" class="badge">À venir</span>
            </span>
          </span>
          <span class="stage-row__actions">
            <button class="btn btn--ghost btn--icon" type="button" aria-label="Monter" :disabled="index === 0 || busy" @click="move(stage, 'UP')">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 15l6-6 6 6" /></svg>
            </button>
            <button class="btn btn--ghost btn--icon" type="button" aria-label="Descendre" :disabled="index === stages.length - 1 || busy" @click="move(stage, 'DOWN')">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
            </button>
            <RouterLink class="btn btn--ghost btn--icon" :to="{ name: 'admin-stage', params: { id: stage.id } }" aria-label="Modifier ou positionner">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16zM13 7l4 4" /></svg>
            </RouterLink>
            <button class="btn btn--ghost btn--icon" type="button" aria-label="Supprimer" :disabled="busy" @click="toDelete = stage">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" /></svg>
            </button>
          </span>
        </li>
      </ol>
      <button class="btn btn--ghost" type="button" :disabled="busy || stages.length < 2" @click="sortByDate">Trier par date</button>
    </template>

    <ConfirmDialog
      v-if="toDelete"
      :title="`Supprimer l'étape « ${toDelete.name} » ?`"
      confirm-label="Supprimer"
      :busy="busy"
      @confirm="remove"
      @cancel="toDelete = null"
    >
      <p>Cette étape sera supprimée <strong>de façon définitive</strong><template v-if="toDelete.hasPath">, avec son tracé GPX</template>. Elle disparaîtra de la carte et du fil des photos.</p>
    </ConfirmDialog>
  </AdminLayout>
</template>
