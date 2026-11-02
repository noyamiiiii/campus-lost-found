<script setup lang="ts">
import { ChevronRight, Clock, MapPin, Tag } from 'lucide-vue-next'
import type { ItemSummary } from '@/types/item'
import { formatEventTime, itemKindLabels, itemStatusLabels, itemStatusTone } from '@/types/item'

const props = defineProps<{ item: ItemSummary }>()
const emit = defineEmits<{ open: [item: ItemSummary] }>()

function open(): void {
  emit('open', props.item)
}
</script>

<template>
  <article class="item-card" role="button" tabindex="0" @click="open" @keydown.enter="open" @keydown.space.prevent="open">
    <div class="item-card__head">
      <span class="kind-tag" :class="`kind-tag--${item.type.toLowerCase()}`">{{ itemKindLabels[item.type] }}</span>
      <h3 class="item-card__title">{{ item.title }}</h3>
      <span class="item-status" :class="`item-status--${itemStatusTone(item.status)}`">
        {{ itemStatusLabels[item.status] }}
      </span>
    </div>
    <p class="item-card__desc">{{ item.description }}</p>
    <div class="item-card__meta">
      <span><MapPin :size="14" />{{ item.locationName || '地点待补充' }}</span>
      <span><Clock :size="14" />{{ formatEventTime(item.eventTime) }}</span>
      <span><Tag :size="14" />{{ item.categoryName }}</span>
    </div>
    <div class="item-card__foot">
      <span>{{ item.publisherName }}</span>
      <span class="item-card__hint">查看详情<ChevronRight :size="14" /></span>
    </div>
  </article>
</template>

