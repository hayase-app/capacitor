import { env } from 'node:process'

import { expose, type Endpoint } from 'abslink'
import { type BridgeChannel, channel } from 'bridge'
import TorrentClient from 'torrent-client'

import './serializers/error'

import type { TorrentSettings } from 'native'

function createWrapper <T> (c: BridgeChannel<T>): Endpoint {
  return {
    on (event: string, listener: (data: T) => void) {
      c.on(event, listener)
    },
    off (event: string, listener: (...args: any[]) => void) {
      c.removeListener(event, listener)
    },
    postMessage (message: T) {
      c.send('message', message)
    }
  }
}

let tclient: TorrentClient | undefined

channel.on('port-init', _data => {
  let settings: TorrentSettings & { path: string } | undefined
  const { id, data } = _data as { id: string, data: unknown}
  if (id === 'settings' || id === 'init') settings = data as TorrentSettings & { path: string }
  if (id === 'destroy') tclient?.destroy()

  if (id === 'init') {
    tclient ??= new TorrentClient(settings!, env.TMPDIR!)
    // eslint-disable-next-line no-undef
    global.tclient = tclient
    // re-exposing leaks memory, but not that much, so it's fine
    expose(tclient, createWrapper(channel))
  } else if (settings) {
    tclient?.updateSettings(settings)
  }
})

channel.on('destroy', () => {
  tclient?.destroy()
})
