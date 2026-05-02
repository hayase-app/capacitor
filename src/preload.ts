/* globals PictureInPicture */
import { App } from '@capacitor/app'
import { Browser } from '@capacitor/browser'
import { Capacitor, SystemBars, SystemBarType } from '@capacitor/core'
import { Device } from '@capacitor/device'
import { ScreenOrientation } from '@capacitor/screen-orientation'
// import { LocalNotifications } from '@capacitor/local-notifications'
import { Share } from '@capacitor/share'
import { ForegroundService, type ServiceType } from '@capawesome-team/capacitor-android-foreground-service'
import { proxy, wrap as _wrap, type Endpoint, type Remote } from 'abslink'
import { IntentUri } from 'capacitor-intent-uri'
import { type ChannelListenerCallback, NodeJS } from 'capacitor-nodejs'

// import engage from './engage'
// import { PlatformType, WatchNextType } from './engage/definitions'
import authsession from './authsession'
import MediaSessionPlugin from './mediasession'
import fs, { Directory } from './storage'
import './serializers/error'
// import { SafeArea } from 'capacitor-plugin-safe-area'

import type { PluginListenerHandle } from '@capacitor/core'
import type { AuthResponse, Native } from 'native'
import type TorrentClient from 'torrent-client'

// @ts-expect-error yep.
window.authsession = authsession

const MAX_RELOADS = 3

const reloadCount = Number(sessionStorage.getItem('cap_reload_count') ?? '0')
// I DONT KNOW, I GIVE UP WITH CAPACITOR
// this fixes a rare issues on some device where the first page load just doesnt load the plugins for some reason!
if (reloadCount < MAX_RELOADS && !Capacitor.isPluginAvailable('App')) {
  sessionStorage.setItem('cap_reload_count', String(reloadCount + 1))
  location.reload()
} else {
  sessionStorage.removeItem('cap_reload_count')
}

// @ts-expect-error yep.
if (!window.native) {
  const isAndroid = navigator.userAgent.includes('Android')

  SystemBars.hide({ bar: SystemBarType.StatusBar })

  screen.orientation.lock = orientation => ScreenOrientation.lock({ orientation })
  screen.orientation.unlock = () => ScreenOrientation.unlock()

  const protocolRx = /hayase:\/\/([a-z0-9]+)\/(.*)/i

  function _parseProtocol (text: string) {
    const match = text.match(protocolRx)
    if (!match) return null
    return {
      target: match[1]!,
      value: match[2]
    }
  }

  function handleProtocol (text: string) {
    const parsed = _parseProtocol(text)
    if (!parsed) return
    if (parsed.target === 'donate') Browser.open({ url: 'https://github.com/sponsors/ThaUnknown/' })

    return parsed
  }

  // cordova screen orientation plugin is also used, and it patches global screen.orientation.lock

  // hook into pip request, and use our own pip implementation, then instantly report exit pip
  // this is more like DOM PiP, rather than video PiP
  HTMLVideoElement.prototype.requestPictureInPicture = function () {
    // @ts-expect-error global
    PictureInPicture.enter(this.videoWidth, this.videoHeight, success => {
      this.dispatchEvent(new Event('leavepictureinpicture'))
      if (success) document.querySelector('#episodeListTarget')?.requestFullscreen()
    }, err => {
      this.dispatchEvent(new Event('leavepictureinpicture'))
      console.error(err)
    })

    // eslint-disable-next-line @typescript-eslint/no-this-alias
    const self = this

    return Promise.resolve({
      addEventListener: () => {},
      removeEventListener: () => {},
      get width () { return self.videoWidth },
      get height () { return self.videoHeight },
      onresize: null,
      dispatchEvent: () => false
    })
  }

  function createWrapper (channel: typeof NodeJS): Endpoint {
    const listeners = new WeakMap<(...args: any[]) => void, PluginListenerHandle>()

    return {
      async on (event: string, listener: (data: unknown) => void) {
        // @ts-expect-error idfk
        const unwrapped: ChannelListenerCallback = (event) => listener(...event.args)
        listeners.set(listener, await channel.addListener(event, unwrapped))
      },
      off (event: string, listener: (...args: any[]) => void) {
        const unwrapped = listeners.get(listener)!
        channel.removeListener(unwrapped)

        listeners.delete(listener)
      },
      postMessage (message: unknown) {
        channel.send({ eventName: 'message', args: [message] })
      }
    }
  }

  function wrap<T> (): Remote<T> {
    return _wrap(createWrapper(NodeJS))
  }

  console.warn('loaded native')

  const DEFAULTS = {
    player: '',
    torrentPath: isAndroid ? 'cache' : 'internal' as 'cache' | 'internal' | 'sdcard',
    torrentSettings: {
      torrentPersist: false,
      torrentDHT: false,
      torrentStreamedDownload: true,
      torrentSpeed: 40,
      maxConns: 50,
      torrentPort: 0,
      dhtPort: 0,
      torrentPeX: false
    }
  }

  class Store {
    data = parseDataFile()

    get<K extends keyof Store['data']> (key: K): Store['data'][K] {
      return this.data[key]
    }

    set<K extends keyof Store['data']> (key: K, val: Store['data'][K]) {
      this.data[key] = val
      localStorage.setItem('userData', JSON.stringify(this.data))
    }
  }

  function parseDataFile () {
    try {
      return { ...DEFAULTS, ...(JSON.parse(localStorage.getItem('userData') ?? '') as typeof DEFAULTS) }
    } catch (error) {
      console.error('Failed to load native settings: ', error)
      return DEFAULTS
    }
  }

  const store = new Store()

  async function sendNodeSettings (id: 'init' | 'settings') {
    let path = await storageTypeToPath(store.data.torrentPath)
    if (path) path += '/hayase'
    NodeJS.send({ eventName: 'port-init', args: [{ id, data: { ...store.data.torrentSettings, path } }] })
  }

  const torrent = NodeJS.whenReady().then(async () => {
    await sendNodeSettings('init')
    return wrap<TorrentClient>()
  })
  globalThis.__torrent = torrent
  const version = App.getInfo().then(info => info.version)

  async function storageTypeToPath (type?: 'cache' | 'internal' | 'sdcard') {
    if (!isAndroid) {
      // @ts-expect-error Probably should use a plugin for getting this directory
      if (type === 'cache') return window.nativeLocations?.cache ?? ''
      // @ts-expect-error Probably should use a plugin for getting this directory
      return window.nativeLocations?.documents ?? ''
    }
    try {
      if (type !== 'cache') await fs.requestPermissions()
      let path: string | undefined
      if (type === 'sdcard') {
        if ((await fs.isPortableStorageAvailable()).available) {
          const { uri } = await fs.stat({ path: '', directory: Directory.PortableStorage })
          const match = uri.match(/file:\/\/\/storage\/([A-z0-9]{4}-[A-z0-9]{4})\/Android\/data/)
          if (match) {
            const [, type] = match
            if (type) {
              path = `/storage/${type}/Download`
            }
          }
        }
      } else if (type === 'cache') {
        path = ''
      }

      path ??= '/storage/emulated/0/Download'

      return path
    } catch {
      return ''
    }
  }

  const stateMapping = {
    none: 6, // 0
    stopped: 1,
    paused: 2,
    playing: 3,
    fast_forwarding: 4,
    rewinding: 5,
    buffering: 6,
    error: 7,
    connecting: 8,
    skipping_to_previous: 9,
    skipping_to_next: 10,
    skipping_to_queue_item: 11,
    position_unknown: -1
  } as const

  const native: Partial<Native> = {
    openURL: (url: string) => Browser.open({ url }),
    getDeviceInfo: async () => ({
      features: {},
      info: await Device.getInfo(),
      cpu: {},
      ram: {}
    }),
    selectDownload: async (type?: 'cache' | 'internal' | 'sdcard') => {
      const path = await storageTypeToPath(type)
      if (isAndroid) await (await torrent).verifyDirectoryPermissions(path)
      store.set('torrentPath', type ?? 'cache')
      await sendNodeSettings('settings')
      return path
    },
    checkAvailableSpace: async () => await (await torrent).checkAvailableSpace(),
    checkIncomingConnections: async (port) => await (await torrent).checkIncomingConnections(port),
    updatePeerCounts: async (hashes) => await (await torrent).scrape(hashes),
    playTorrent: async (id, mediaID, episode) => await (await torrent).playTorrent(id, mediaID, episode),
    rescanTorrents: async (hashes) => await (await torrent).rescanTorrents(hashes),
    deleteTorrents: async (hashes) => await (await torrent).deleteTorrents(hashes),
    library: async () => await (await torrent).library(),
    attachments: async (hash, id) => await (await torrent).attachments.attachments(hash, id),
    tracks: async (hash, id) => await (await torrent).attachments.tracks(hash, id),
    subtitles: async (hash, id, cb) => await (await torrent).attachments.subtitle(hash, id, proxy(cb)),
    errors: async (cb) => await (await torrent).errors(proxy(cb)),
    chapters: async (hash, id) => await (await torrent).attachments.chapters(hash, id),
    torrentInfo: async (hash) => await (await torrent).torrentInfo(hash),
    peerInfo: async (hash) => await (await torrent).peerInfo(hash),
    fileInfo: async (hash) => await (await torrent).fileInfo(hash),
    protocolStatus: async (hash) => await (await torrent).protocolStatus(hash),
    updateSettings: async (settings) => {
      store.set('torrentSettings', settings)
      await torrent
      await sendNodeSettings('settings')
    },
    cachedTorrents: async () => await (await torrent).cached(),
    createNZB: async (id, url, domain, port, login, password, poolSize) => await (await torrent).createNZBWebSeed(id, url, domain, port, login, password, poolSize),
    getDisplays: async cb => await (await torrent).listenDisplay(proxy(cb)),
    castPlay: async (host, hash, id, media) => await (await torrent).playDisplay(host, hash, id, media),
    castClose: async (host) => await (await torrent).closeDisplay(host),
    enableCORS: async (urls) => {
      try {
        // @ts-expect-error plugin is native-only
        await Capacitor.Plugins.CorsProxy.enableCORS({ urls })
      } catch (error) {
        console.error('Failed to enable CORS proxy', error)
      }
    },
    isApp: true,
    version: () => version,
    navigate: async (cb) => {
      App.addListener('appUrlOpen', ({ url }) => {
        const res = handleProtocol(url)
        if (res) cb(res)
      })
      const url = await App.getLaunchUrl()
      if (!url) return
      const res = handleProtocol(url.url)
      if (res) cb(res)
    },
    share: async (data) => {
      if (!data) return
      Share.share({ title: data.title, url: data.url, dialogTitle: data.title })
    },
    defaultTransparency: () => false,
    debug: async (levels) => await (await torrent).debug(levels)
  }

  if (isAndroid) {
    native.setActionHandler = (name, cb) => MediaSessionPlugin.addListener(name, cb!)
    native.setMediaSession = (session, _id, duration) => MediaSessionPlugin.setMediaSession({ ...session, duration })
    native.setPositionState = (state, paused) => MediaSessionPlugin.setPlaybackState({
      ...(state as { duration: number, playbackRate: number, position: number }),
      state: stateMapping[paused]
    })
    native.setPlayBackState = async () => {}
    native.spawnPlayer = async (url) => {
      let notiPermission = await ForegroundService.checkPermissions()
      if (notiPermission.display === 'prompt') notiPermission = await ForegroundService.requestPermissions()
      if (notiPermission.display === 'granted') {
        await ForegroundService.startForegroundService({
          id: 1,
          title: 'Hayase is running',
          body: 'Hayase is currently running in the background',
          smallIcon: 'ic_launcher_foreground',
          silent: true,
          serviceType: 2 as ServiceType,
          notificationChannelId: 'default'
        })
      }

      const res = await IntentUri.openUri({ url: `${url.replace('http', 'intent')}#Intent;type=video/any;scheme=http;end;` })

      if (notiPermission.display === 'granted') await ForegroundService.stopForegroundService()

      if (!res.completed) throw new Error(res.message)
    }
    native.setDOH = async () => {
      const res = await IntentUri.openUri({ url: 'intent:#Intent;action=android.settings.SETTINGS;end;' })
      if (!res.completed) throw new Error(res.message)
    }
    native.castPlay = async (host, hash, id, media) => {
      let notiPermission = await ForegroundService.checkPermissions()
      if (notiPermission.display === 'prompt') notiPermission = await ForegroundService.requestPermissions()
      if (notiPermission.display === 'granted') {
        await ForegroundService.startForegroundService({
          id: 1,
          title: 'Hayase is running',
          body: 'Hayase is currently running in the background',
          smallIcon: 'ic_launcher_foreground',
          silent: true,
          serviceType: 2 as ServiceType,
          notificationChannelId: 'default'
        })
      }
      await (await torrent).playDisplay(host, hash, id, media)

      if (notiPermission.display === 'granted') await ForegroundService.stopForegroundService()
    }
    native.castClose = async (host) => {
      await (await torrent).closeDisplay(host)
      try {
        await ForegroundService.stopForegroundService()
      } catch (error) {
        // ignore
      }
    }
  } else {
    native.authAL = async (url: string) => {
      const { url: res } = await authsession.authLegacy({ url, callbackScheme: 'hayase' })
      const { hash } = new URL(res)

      if (hash.startsWith('#access_token=')) {
        return Object.fromEntries(new URLSearchParams(hash.replace('#', '?')).entries()) as unknown as AuthResponse
      }
      throw new Error('Invalid url')
    }
    native.authMAL = async (url: string) => {
      const { url: res } = await authsession.authLegacy({ url, callbackScheme: 'hayase' })
      const { search } = new URL(res)

      if (search.startsWith('?code=')) {
        return Object.fromEntries(new URLSearchParams(search).entries()) as unknown as { code: string, state: string }
      }
      throw new Error('Invalid url')
    }

    NodeJS.start({ args: ['--disallow-code-generation-from-strings', '--disable-proto=throw', '--frozen-intrinsics'] })
  }

  // @ts-expect-error yep.
  window.native = native
}
