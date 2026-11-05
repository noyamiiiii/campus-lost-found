import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useFeedStore = defineStore('feed', () => {
  const version = ref(0)

  function bump(): void {
    version.value += 1
  }

  return { version, bump }
})
