import { registerPlugin } from '@capacitor/core'

import type { WakeLockPlugin } from './definitions'

const plugin = registerPlugin<WakeLockPlugin>('WakeLock', {})

class WakeLockSentinel extends EventTarget implements globalThis.WakeLockSentinel {
  released = false
  readonly type = 'screen'
  onrelease: ((this: WakeLockSentinel, ev: Event) => unknown) | null = null

  async release () {
    if (this.released) return
    this.released = true
    await plugin.release()
    this.dispatchEvent(new Event('release'))
    this.onrelease?.(new Event('release'))
  }
}

export function register () {
  // @ts-expect-error yep
  navigator.wakeLock = {
    async request (type = 'screen') {
      await plugin.acquire()
      return new WakeLockSentinel()
    }
  }
}
