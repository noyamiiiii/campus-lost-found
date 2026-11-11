<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, Clock, MapPin, Package, Tag } from 'lucide-vue-next'
import type { ItemSummary } from '@/types/item'
import { formatEventTime, itemKindLabels, itemStatusLabels, itemStatusTone } from '@/types/item'

const props = defineProps<{ item: ItemSummary }>()
const emit = defineEmits<{ open: [item: ItemSummary] }>()

// 列表接口返回的 images 已经按主图在前排过序，这里只兜底一次
const coverUrl = computed(() => {
  const images = props.item.images ?? []
  const primary = images.find((image) => image.primary) ?? images[0]
  return primary?.url ?? ''
})

function open(): void {
  emit('open', props.item)
}
</script>

<template>
  <article class="item-card" role="button" tabindex="0" @click="open" @keydown.enter="open" @keydown.space.prevent="open">
    <div v-if="coverUrl" class="item-card__media">
      <img :src="coverUrl" :alt="item.title" loading="lazy" />
    </div>
    <div v-else class="item-card__media item-card__media--empty" aria-hidden="true">
      <Package :size="20" />
    </div>

    <div class="item-card__content">
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
    </div>
  </article>
</template>

