<script setup>
import { STAGE_KINDS, stageKindClass } from '../stageKinds'

defineProps({ kind: { type: String, required: true } })
</script>

<template>
  <svg class="stage-icon" :class="stageKindClass(kind)" viewBox="0 0 24 24" aria-hidden="true"><path :d="STAGE_KINDS[kind]?.icon" /></svg>
</template>
