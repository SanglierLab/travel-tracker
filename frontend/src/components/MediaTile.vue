<script setup>
defineProps({
  item: { type: Object, required: true },
  label: { type: String, default: '' },
})
defineEmits(['open'])
</script>

<template>
  <button class="media" type="button" :aria-label="label" @click="$emit('open')">
    <img
      v-if="item.thumbUrl"
      :src="item.thumbUrl"
      :width="item.width || undefined"
      :height="item.height || undefined"
      alt=""
      loading="lazy"
      decoding="async"
    />
    <!-- Vidéo sans miniature : simple fond neutre, la pastille de lecture suffit -->
    <span v-if="item.type === 'VIDEO'" class="media__play" aria-hidden="true">
      <svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z" /></svg>
    </span>
  </button>
</template>
