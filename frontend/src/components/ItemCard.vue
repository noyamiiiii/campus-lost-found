<script setup lang="ts">
import { ChevronRight, Clock, MapPin, Tag } from 'lucide-vue-next'
import type { LostItem } from '@/types/item'
import { itemKindLabels, itemStatusLabels } from '@/types/item'

const props = defineProps<{ item: LostItem }>()
const emit = defineEmits<{ open: [item: LostItem] }>()

function open(): void {
  emit('open', props.item)
}
</script>

<template>
  <article class="item-card" role="button" tabindex="0" @click="open" @keydown.enter="open" @keydown.space.prevent="open">
    <div class="item-card__head">
      <span class="kind-tag" :class="`kind-tag--${item.kind}`">{{ itemKindLabels[item.kind] }}</span>
      <h3 class="item-card__title">{{ item.title }}</h3>
      <span class="item-status" :class="`item-status--${item.status}`">{{ itemStatusLabels[item.status] }}</span>
    </div>
    <p class="item-card__desc">{{ item.description }}</p>
    <div class="item-card__meta">
      <span><MapPin :size="14" />{{ item.location }}</span>
      <span><Clock :size="14" />{{ item.happenedAt }}</span>
      <span><Tag :size="14" />{{ item.category }}</span>
    </div>
    <div class="item-card__foot">
      <span>{{ item.publisher }}</span>
      <span class="item-card__hint">查看详情<ChevronRight :size="14" /></span>
    </div>
  </article>
</template>

