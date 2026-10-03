<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { L, createMap, pinIcon, prefersReducedMotion } from '../leafletSupport'

// v-model : { lat, lon } ou null. Le point se place en touchant la carte, se déplace en le faisant glisser,
// ou se pose automatiquement avec la position GPS du téléphone (bouton « Me positionner »).
const props = defineProps({
  modelValue: { type: Object, default: null },
  center: { type: Array, default: null }, // [lat, lon] où centrer la carte tant qu'aucun point n'est posé
})
const emit = defineEmits(['update:modelValue'])

const element = ref(null)
const locating = ref(false)
const message = ref('')

// Objets Leaflet gardés hors de la réactivité de Vue.
let map = null
let marker = null
let observer = null

const round6 = (n) => Math.round(n * 1e6) / 1e6

function commit(latlng) {
  emit('update:modelValue', { lat: round6(latlng.lat), lon: round6(latlng.lng) })
}

function sameAsMarker(position) {
  if (!marker || !position) return false
  const current = marker.getLatLng()
  return round6(current.lat) === round6(position.lat) && round6(current.lng) === round6(position.lon)
}

function showMarker(position, pan) {
  const latlng = L.latLng(position.lat, position.lon)
  if (!marker) {
    marker = L.marker(latlng, { icon: pinIcon(true), draggable: true, keyboard: false }).addTo(map)
    marker.on('dragend', () => commit(marker.getLatLng()))
  } else {
    marker.setLatLng(latlng)
  }
  if (pan) map.setView(latlng, Math.max(map.getZoom(), 13), { animate: !prefersReducedMotion() })
}

function locate() {
  message.value = ''
  if (!navigator.geolocation) {
    message.value = "La géolocalisation n'est pas disponible sur cet appareil. Placez le point sur la carte."
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      locating.value = false
      commit({ lat: coords.latitude, lng: coords.longitude })
      map.setView([coords.latitude, coords.longitude], 14, { animate: !prefersReducedMotion() })
      message.value = `Position trouvée (précision d'environ ${Math.round(coords.accuracy)} m). Vous pouvez déplacer le point.`
    },
    (error) => {
      locating.value = false
      if (error.code === 1) {
        message.value =
          "Autorisation de localisation refusée. Autorisez-la dans les réglages du navigateur, ou placez le point sur la carte."
      } else if (error.code === 3) {
        message.value = 'La localisation prend trop de temps. Réessayez, ou placez le point sur la carte.'
      } else {
        message.value = 'Position indisponible. Placez le point sur la carte.'
      }
    },
    { enableHighAccuracy: true, timeout: 20000, maximumAge: 30000 },
  )
}

onMounted(() => {
  map = createMap(element.value)
  if (props.modelValue) {
    map.setView([props.modelValue.lat, props.modelValue.lon], 13)
    showMarker(props.modelValue, false)
  } else if (props.center) {
    map.setView(props.center, 6)
  } else {
    map.setView([20, 0], 2)
  }

  map.on('click', (event) => {
    showMarker({ lat: event.latlng.lat, lon: event.latlng.lng }, false)
    commit(event.latlng)
  })

  observer = new ResizeObserver(() => map.invalidateSize())
  observer.observe(element.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  map?.remove()
  map = null
  marker = null
})

// Changement venu de l'extérieur (chargement d'une galerie, coordonnées saisies à la main) : on suit.
watch(
  () => props.modelValue,
  (position) => {
    if (!map) return
    if (!position) {
      marker?.remove()
      marker = null
    } else if (!sameAsMarker(position)) {
      showMarker(position, true)
    }
  },
)

// Le centre peut arriver après l'affichage (chargement de la liste) : on ne l'applique que s'il n'y a pas de point.
watch(
  () => props.center,
  (center) => {
    if (map && center && !props.modelValue) map.setView(center, 6)
  },
)
</script>

<template>
  <div class="picker">
    <div ref="element" class="picker__map"></div>
    <div class="picker__bar">
      <button class="btn" type="button" :disabled="locating" @click="locate">
        {{ locating ? 'Localisation en cours…' : 'Me positionner' }}
      </button>
      <p class="muted picker__hint">Touchez la carte pour placer le point, ou faites-le glisser.</p>
    </div>
    <p v-if="message" class="picker__message" role="status">{{ message }}</p>
  </div>
</template>
