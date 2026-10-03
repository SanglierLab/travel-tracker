<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { api } from '../../api'
import { formatDate } from '../../format'

const galleries = ref(null)
const error = ref(false)

async function load() {
  error.value = false
  try {
    galleries.value = await api('/api/admin/galleries')
  } catch {
    error.value = true
  }
}
onMounted(load)
</script>

<template>
  <AdminLayout>
    <div class="admin__head">
      <h1>Galeries</h1>
      <RouterLink class="btn" :to="{ name: 'admin-gallery-new' }">+ Nouvelle</RouterLink>
    </div>

    <p v-if="error" class="error" role="alert">
      Impossible de charger les galeries.
      <button class="btn btn--ghost btn--small" type="button" @click="load">Réessayer</button>
    </p>
    <p v-else-if="!galleries" class="muted">Chargement…</p>
    <p v-else-if="galleries.length === 0" class="muted">Aucune galerie pour le moment. Créez la première !</p>

    <ul v-else class="admin-list">
      <li v-for="gallery in galleries" :key="gallery.id">
        <RouterLink class="admin-list__item" :to="{ name: 'admin-gallery', params: { id: gallery.id } }">
          <img v-if="gallery.coverUrl" class="admin-list__thumb" :src="gallery.coverUrl" alt="" loading="lazy" />
          <span v-else class="admin-list__thumb" aria-hidden="true"></span>
          <span class="admin-list__text">
            <strong>{{ gallery.title }}</strong>
            <span class="muted">{{ formatDate(gallery.galleryDate) }} · {{ gallery.placeName }}</span>
          </span>
        </RouterLink>
      </li>
    </ul>
  </AdminLayout>
</template>
