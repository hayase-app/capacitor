import type { Plugin } from '@capacitor/core'

export interface WakeLockPlugin extends Plugin {
  acquire: () => Promise<void>
  release: () => Promise<void>
}
