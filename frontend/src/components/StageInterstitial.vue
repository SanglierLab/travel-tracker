<script setup>
import { computed } from 'vue'
import { formatDateRange } from '../format'
import { STAGE_KINDS } from '../stageKinds'
import StageIcon from './StageIcon.vue'

const props = defineProps({
  stage: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  // Étape à venir : affichée estompée.
  future: { type: Boolean, default: false },
  // Dans le fil, la carte est posée sur la ligne du temps ; dans le bloc « suite du voyage », non.
  timeline: { type: Boolean, default: true },
})
defineEmits(['select'])

const label = computed(() => STAGE_KINDS[props.stage.kind]?.label ?? 'Étape')
</script>

<template>
  <!-- Même carte qu'un album (classes « gallery ») : mêmes marges, bordure, titre, ligne de date et sélection. -->
  <article
    :id="`etape-${stage.id}`"
    class="gallery stage-card"
    :class="{ 'gallery--selected': selected, 'stage-card--future': future }"
  >
    <span v-if="timeline" class="gallery__node" aria-hidden="true"></span>
    <header
      class="gallery__head"
      role="button"
      tabindex="0"
      :aria-pressed="selected"
      @click="$emit('select', stage.id)"
      @keydown.enter.prevent="$emit('select', stage.id)"
      @keydown.space.prevent="$emit('select', stage.id)"
    >
      <p class="gallery__meta">
        <StageIcon :kind="stage.kind" class="stage-card__icon" />
        <time :datetime="stage.startDate">{{ formatDateRange(stage.startDate, stage.endDate) }}</time>
        · {{ label }}<template v-if="future"> · à venir</template>
      </p>
      <h2 class="gallery__title">{{ stage.name }}</h2>
    </header>
  </article>
</template>
