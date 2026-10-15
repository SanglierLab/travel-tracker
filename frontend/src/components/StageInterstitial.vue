<script setup>
import { computed } from 'vue'
import { formatDateRange } from '../format'
import { STAGE_KINDS } from '../stageKinds'
import StageIcon from './StageIcon.vue'

const props = defineProps({
  stage: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  // Étape à venir (bloc « suite du voyage ») : affichée estompée.
  future: { type: Boolean, default: false },
  // Dans le fil, l'étape est posée sur la ligne du temps ; dans le bloc « suite du voyage », non.
  timeline: { type: Boolean, default: true },
})
defineEmits(['select'])

const label = computed(() => STAGE_KINDS[props.stage.kind]?.label ?? 'Étape')
</script>

<template>
  <article
    :id="`etape-${stage.id}`"
    class="stage-item"
    :class="{ 'stage-item--selected': selected, 'stage-item--future': future }"
  >
    <span v-if="timeline" class="stage-item__node" aria-hidden="true"></span>
    <button class="stage-item__body" type="button" :aria-pressed="selected" @click="$emit('select', stage.id)">
      <StageIcon :kind="stage.kind" class="stage-item__icon" />
      <span class="stage-item__text">
        <strong>{{ stage.name }}</strong>
        <span class="stage-item__meta">
          {{ label }} · {{ formatDateRange(stage.startDate, stage.endDate) }}<template v-if="future"> · à venir</template>
        </span>
      </span>
    </button>
  </article>
</template>
