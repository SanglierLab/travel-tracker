<script setup>
import { onMounted, ref } from 'vue'

defineProps({
  title: { type: String, required: true },
  confirmLabel: { type: String, default: 'Confirmer' },
  cancelLabel: { type: String, default: 'Annuler' },
  busy: { type: Boolean, default: false },
})
defineEmits(['confirm', 'cancel'])

const dialog = ref(null)

// <dialog> natif : focus piégé, Échap et arrière-plan gérés par le navigateur.
onMounted(() => {
  const element = dialog.value
  if (typeof element.showModal === 'function') element.showModal()
  else element.setAttribute('open', '') // très vieux navigateurs : sans fond grisé, mais fonctionnel
})
</script>

<template>
  <dialog ref="dialog" class="dialog" @cancel.prevent="$emit('cancel')" @click.self="$emit('cancel')">
    <div class="dialog__inner">
      <h2 class="dialog__title">{{ title }}</h2>
      <div class="dialog__body"><slot /></div>
      <div class="dialog__actions">
        <button class="btn btn--ghost" type="button" :disabled="busy" @click="$emit('cancel')">{{ cancelLabel }}</button>
        <button class="btn btn--danger" type="button" :disabled="busy" @click="$emit('confirm')">{{ confirmLabel }}</button>
      </div>
    </div>
  </dialog>
</template>
