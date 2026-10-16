<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { api } from '../../api'
import { formatDateTime, formatSize, timeAgo } from '../../format'
import { TRACK_SOURCES } from '../../trackStyle'

// Page en lecture seule : ce que le serveur a comme réglages et ce qu'il fait en ce moment.
// Les secrets (mots de passe, token d'API, jeton LiveTrack) ne sont jamais renvoyés par le serveur.

const info = ref(null)
const loadError = ref(false)

async function load() {
  loadError.value = false
  try {
    info.value = await api('/api/admin/info')
  } catch {
    loadError.value = true
  }
}
onMounted(load)

/** 30 -> « 30 s », 300 -> « 5 min », 5400 -> « 1 h 30 ». */
function duration(seconds) {
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} h ${String(rest).padStart(2, '0')}` : `${hours} h`
}

const number = new Intl.NumberFormat('fr-FR')

const sourceRows = computed(() =>
  (info.value?.sources ?? []).map((source) => ({
    ...source,
    label: TRACK_SOURCES[source.source]?.label ?? source.source,
    cssVar: TRACK_SOURCES[source.source]?.cssVar,
    state: source.trackable ? (source.activeLabel ? `Suivi en cours : ${source.activeLabel}` : 'Aucun suivi actif') : 'Envoi par le téléphone',
    poll: source.pollSeconds ? `toutes les ${duration(source.pollSeconds)}` : '—',
    last: source.lastPointAt ? timeAgo(source.lastPointAt) : 'aucune position',
  })),
)

const buildText = computed(() => {
  const version = info.value?.version
  if (!version) return ''
  return version.builtAt ? `${version.version}, construite le ${formatDateTime(version.builtAt)}` : version.version
})

const disk = computed(() => {
  const content = info.value?.content
  if (!content || content.diskFreeBytes == null) return 'illisible'
  return `${formatSize(content.diskFreeBytes)} libres sur ${formatSize(content.diskTotalBytes)}`
})

/** URL de l'API de positions, complétée avec l'adresse du site telle que la voit ce navigateur. */
const positionsUrl = computed(() => (info.value ? `${window.location.origin}${info.value.tools.positionsPath}` : ''))
</script>

<template>
  <AdminLayout>
    <h1>Configuration</h1>
    <p class="muted">Informations en lecture seule. Les réglages se modifient dans le fichier <code>application.yml</code> du serveur ; les secrets ne sont jamais affichés.</p>

    <p v-if="loadError" class="error" role="alert">
      Impossible de charger les informations.
      <button class="btn btn--ghost btn--small" type="button" @click="load">Réessayer</button>
    </p>
    <p v-else-if="!info" class="muted">Chargement…</p>

    <template v-else>
      <section class="card admin__section">
        <h2>Version</h2>
        <dl class="info-list">
          <div><dt>Application</dt><dd>{{ info.version.name }} {{ buildText }}</dd></div>
        </dl>
      </section>

      <section class="card admin__section">
        <h2>Suivi en direct</h2>
        <ul class="info-sources">
          <li v-for="source in sourceRows" :key="source.source">
            <p class="info-sources__name">
              <span class="info-sources__swatch" :style="{ background: `var(${source.cssVar})` }" aria-hidden="true"></span>
              <strong>{{ source.label }}</strong>
              <span :class="source.activeLabel ? 'badge badge--active' : 'muted'">{{ source.state }}</span>
            </p>
            <dl class="info-list">
              <div v-if="source.trackable"><dt>Relève</dt><dd>{{ source.poll }}</dd></div>
              <div><dt>« En retard » après</dt><dd>{{ duration(source.lateAfterSeconds) }}</dd></div>
              <div><dt>Positions</dt><dd>{{ number.format(source.pointCount) }}</dd></div>
              <div><dt>Dernière</dt><dd>{{ source.last }}</dd></div>
            </dl>
          </li>
        </ul>
      </section>

      <section class="card admin__section">
        <h2>Contenu</h2>
        <dl class="info-list">
          <div><dt>Galeries</dt><dd>{{ number.format(info.content.galleries) }}</dd></div>
          <div><dt>Photos</dt><dd>{{ number.format(info.content.photos) }}</dd></div>
          <div><dt>Vidéos</dt><dd>{{ number.format(info.content.videos) }}</dd></div>
          <div><dt>Blocs de texte</dt><dd>{{ number.format(info.content.texts) }}</dd></div>
          <div><dt>Originaux stockés</dt><dd>{{ formatSize(info.content.originalsBytes) || '0 Ko' }}</dd></div>
          <div><dt>Disque des médias</dt><dd>{{ disk }}</dd></div>
        </dl>
      </section>

      <section class="card admin__section">
        <h2>Limites</h2>
        <dl class="info-list">
          <div><dt>Taille d'une photo</dt><dd>{{ formatSize(info.limits.maxPhotoBytes) }} au plus</dd></div>
          <div><dt>Taille d'une vidéo</dt><dd>{{ formatSize(info.limits.maxVideoBytes) }} au plus</dd></div>
          <div><dt>Galeries par page</dt><dd>{{ info.limits.pageSize }}</dd></div>
          <div><dt>Étape « bientôt »</dt><dd>{{ info.limits.upcomingDays }} jour{{ info.limits.upcomingDays > 1 ? 's' : '' }} avant</dd></div>
          <div><dt>Coupure du tracé</dt><dd>après {{ info.limits.trackGapHours }} h sans position</dd></div>
          <div><dt>Précision GPS retenue</dt><dd>{{ number.format(info.limits.trackMaxAccuracyMeters) }} m au plus</dd></div>
        </dl>
      </section>

      <section class="card admin__section">
        <h2>Outils</h2>
        <dl class="info-list">
          <div>
            <dt>ffmpeg</dt>
            <dd>
              <template v-if="info.tools.ffmpegAvailable">disponible, version {{ info.tools.ffmpegVersion }}</template>
              <span v-else class="error">introuvable : miniatures de vidéos et photos WebP impossibles</span>
            </dd>
          </div>
          <div><dt>API de positions (GPSLogger)</dt><dd><code class="info-url">{{ positionsUrl }}</code></dd></div>
          <div><dt>En-tête du token</dt><dd><code>{{ info.tools.tokenHeader }}</code> (valeur : voir le fichier de configuration)</dd></div>
        </dl>
      </section>
    </template>
  </AdminLayout>
</template>
