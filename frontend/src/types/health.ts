export type ServiceStatus = 'idle' | 'checking' | 'up' | 'down'

export interface ServiceState {
  status: ServiceStatus
  detail: string
  latencyMs: number | null
}

