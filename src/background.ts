import { env } from 'node:process'

import { expose, type Endpoint } from 'abslink'
import { type BridgeChannel, channel } from 'bridge'
import TorrentClient, { destroy, updateSettings } from 'torrent-client'

import './serializers/error'

import type { ClientSettings } from 'native'

function createWrapper <T> (c: BridgeChannel<T>): { endpoint: Endpoint, dispose: () => void } {
  const added: Array<[string, (data: T) => void]> = []
  return {
    endpoint: {
      on (event: string, listener: (data: T) => void) {
        c.on(event, listener)
        added.push([event, listener])
      },
      off (event: string, listener: (...args: any[]) => void) {
        c.removeListener(event, listener)
      },
      postMessage (message: T) {
        c.send('message', message)
      }
    },
    dispose () {
      for (const [event, listener] of added) c.removeListener(event, listener)
      added.length = 0
    }
  }
}

let tclient: TorrentClient | undefined
let exposed: ReturnType<typeof createWrapper> | undefined
let destroying: Promise<void> | undefined

// The process can outlive the activity, so do not exit. The next 'init' creates a new client.
function destroyClient () {
  exposed?.dispose()
  exposed = undefined
  if (!tclient) return
  destroying = tclient[destroy](false)
  tclient = undefined
}

channel.on('port-init', async _data => {
  let settings: ClientSettings & { path: string } | undefined
  const { id, data } = _data as { id: string, data: unknown}
  if (id === 'settings' || id === 'init') settings = data as ClientSettings & { path: string }
  if (id === 'destroy') destroyClient()

  if (id === 'init') {
    // The new client uses the same ports as the old one.
    await destroying
    tclient ??= new TorrentClient(settings!, env.TMPDIR!)
    // eslint-disable-next-line no-undef
    global.tclient = tclient
    exposed?.dispose()
    exposed = createWrapper(channel)
    expose(tclient, exposed.endpoint)
  } else if (settings) {
    tclient?.[updateSettings](settings)
  }
})

channel.on('destroy', destroyClient)
