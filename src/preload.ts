/* globals PictureInPicture */
import { App } from '@capacitor/app'
import { Browser } from '@capacitor/browser'
import { Capacitor } from '@capacitor/core'
import { Device } from '@capacitor/device'
// import { LocalNotifications } from '@capacitor/local-notifications'
import { Share } from '@capacitor/share'
import { ForegroundService, type ServiceType } from '@capawesome-team/capacitor-android-foreground-service'
import { proxy, wrap as _wrap, type Endpoint, type Remote } from 'abslink'
import { IntentUri } from 'capacitor-intent-uri'
import { type ChannelListenerCallback, NodeJS } from 'capacitor-nodejs'

// import engage from './engage'
// import { PlatformType, WatchNextType } from './engage/definitions'
import MediaSessionPlugin from './mediasession'
import fs, { Directory } from './storage'
import './serializers/error'
// import { SafeArea } from 'capacitor-plugin-safe-area'

import type { PluginListenerHandle } from '@capacitor/core'
import type { Native } from 'native'
import type TorrentClient from 'torrent-client'

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
  // if ((await engage.isServiceAvailable()).result) {
  //   engage.publishContinuationCluster({
  //     accountProfile: {
  //       accoundId: 'global'
  //     },
  //     entries: [
  //       {
  //         type: 'tv_episode',
  //         watchNextType: WatchNextType.NEW,
  //         entityId: 'a',
  //         name: 'Something Else',
  //         platformSpecificPlaybackUris: [{
  //           type: PlatformType.ANDROID_TV,
  //           uri: 'intent:#Intent;action=android.intent.action.VIEW;data=hayase://playback/uniqueId1;package=app.hayase;end'
  //         }, {
  //           type: PlatformType.ANDROID_MOBILE,
  //           uri: 'intent:#Intent;action=android.intent.action.VIEW;data=hayase://playback/uniqueId1;package=app.hayase;end'
  //         }],
  //         posterImages: [{
  //           height: 720,
  //           width: 1280,
  //           uri: 'https://i.ytimg.com/vi/nEj2X9x9M7Q/maxresdefault.jpg'
  //         }],
  //         lastEngagementTimeMillis: 1752717555387,
  //         durationMillis: 1500000,
  //         episodeNumber: 1,
  //         seasonNumber: '1',
  //         showTitle: 'Sesbian Lex',
  //         seasonTitle: "Can't believe I'm not gay",
  //         airDateEpochMillis: 1752685200000,
  //         genres: ['lesbian', 'romance']
  //       },
  //       {
  //         type: 'movie',
  //         watchNextType: WatchNextType.NEW,
  //         entityId: 'd',
  //         name: 'Same but movie',
  //         releaseDateEpochMillis: 1752017555387,
  //         posterImages: [{
  //           height: 720,
  //           width: 1280,
  //           uri: 'https://i.ytimg.com/vi/nEj2X9x9M7Q/maxresdefault.jpg'
  //         }],
  //         platformSpecificPlaybackUris: [{
  //           type: PlatformType.ANDROID_TV,
  //           uri: 'intent:#Intent;action=android.intent.action.VIEW;data=hayase://playback/uniqueId1;package=app.hayase;end'
  //         }, {
  //           type: PlatformType.ANDROID_MOBILE,
  //           uri: 'intent:#Intent;action=android.intent.action.VIEW;data=hayase://playback/uniqueId1;package=app.hayase;end'
  //         }],
  //         lastEngagementTimeMillis: 1752717555387,
  //         durationMillis: 1500000,
  //         description: 'Wowie',
  //         genres: ['lesbian', 'romance']
  //       }
  //     ]
  //   })
  //   engage.publishRecommendationCluster({
  //     accountProfile: {
  //       accoundId: 'global'
  //     },
  //     clusters: [
  //       {
  //         entries: [
  //           {
  //             type: 'tv_season',
  //             watchNextType: WatchNextType.NEW,
  //             entityId: 'b',
  //             infoPageUri: 'intent:#Intent;action=android.intent.action.VIEW;data=hayase://playback/uniqueId1;package=app.hayase;end',
  //             name: 'Same stuff',
  //             posterImages: [{
  //               height: 720,
  //               width: 1280,
  //               uri: 'https://i.ytimg.com/vi/nEj2X9x9M7Q/maxresdefault.jpg'
  //             }],
  //             lastEngagementTimeMillis: 1752717555387,
  //             firstEpisodeAirDateEpochMillis: 1752685200000,
  //             latestEpisodeAirDateEpochMillis: 1752685200000,
  //             episodeCount: 6,
  //             seasonNumber: 1,
  //             genres: ['lesbian', 'romance']
  //           },
  //           {
  //             type: 'tv_show',
  //             entityId: 'c',
  //             watchNextType: WatchNextType.NEW,
  //             infoPageUri: 'intent:#Intent;action=android.intent.action.VIEW;data=hayase://playback/uniqueId1;package=app.hayase;end',
  //             name: 'Same but show',
  //             posterImages: [{
  //               height: 720,
  //               width: 1280,
  //               uri: 'https://i.ytimg.com/vi/nEj2X9x9M7Q/maxresdefault.jpg'
  //             }],
  //             lastEngagementTimeMillis: 1752717555387,
  //             firstEpisodeAirDateEpochMillis: 1752685200000,
  //             latestEpisodeAirDateEpochMillis: 1752685200000,
  //             seasonCount: 2,
  //             genres: ['lesbian', 'romance']
  //           }
  //         ]
  //       }
  //     ]
  //   })
  // }

  // LocalNotifications.checkPermissions().then(async value => {
  //   if (value) {
  //     try {
  //       await LocalNotifications.requestPermissions()
  //       canShowNotifications = true
  //     } catch (e) {
  //       console.error(e)
  //     }
  //   }
  // })

  // let id = 0
  // IPC.on('notification', noti => {
  //   /** @type {import('@capacitor/local-notifications').LocalNotificationSchema} */
  //   const notification = {
  //     title: noti.title,
  //     body: noti.body,
  //     id: id++,
  //     attachments: [
  //       {
  //         id: '' + id++,
  //         url: noti.icon
  //       }
  //     ]
  //   }
  //   if (canShowNotifications) LocalNotifications.schedule({ notifications: [notification] })
  // })

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
    torrentPath: 'cache' as 'cache' | 'internal' | 'sdcard',
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
  const version = App.getInfo().then(info => info.version)

  async function storageTypeToPath (type?: 'cache' | 'internal' | 'sdcard') {
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
    selectDownload: async (type?: 'cache' | 'internal' | 'sdcard') => {
      const path = await storageTypeToPath(type)
      await (await torrent).verifyDirectoryPermissions(path)
      store.set('torrentPath', type ?? 'cache')
      await sendNodeSettings('settings')
      return path
    },
    // getLogs: () => main.getLogs(),
    getDeviceInfo: async () => ({
      features: {},
      info: await Device.getInfo(),
      cpu: {},
      ram: {}
    }),
    setActionHandler: (name, cb) => MediaSessionPlugin.addListener(name, cb!),
    setMediaSession: (session, _id, duration) => MediaSessionPlugin.setMediaSession({ ...session, duration }),
    setPositionState: (state, paused) => MediaSessionPlugin.setPlaybackState({
      ...(state as { duration: number, playbackRate: number, position: number }),
      state: stateMapping[paused]
    }),
    setPlayBackState: async () => {},
    checkAvailableSpace: async () => await (await torrent).checkAvailableSpace(),
    checkIncomingConnections: async (port) => await (await torrent).checkIncomingConnections(port),
    updatePeerCounts: async (hashes) => await (await torrent).scrape(hashes),
    playTorrent: async (id, mediaID, episode) => await (await torrent).playTorrent(id, mediaID, episode),
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
    getDisplays: async cb => await (await torrent).chromecasts.listen(proxy(cb)),
    castPlay: async (host, hash, id, media) => {
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
      await (await torrent).chromecasts.play(host, hash, id, media)

      if (notiPermission.display === 'granted') await ForegroundService.stopForegroundService()
    },
    castClose: async (host) => {
      await (await torrent).chromecasts.close(host)
      try {
        await ForegroundService.stopForegroundService()
      } catch (error) {
        // ignore
      }
    },
    enableCORS: async (urls) => {
      try {
        if (Capacitor.isPluginAvailable('CorsProxy')) {
          // @ts-expect-error plugin is native-only
          await Capacitor.Plugins.CorsProxy.enableCORS({ urls })
        }
      } catch (error) {
        console.error('Failed to enable CORS proxy', error)
      }
    },
    isApp: true,
    spawnPlayer: async (url) => {
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
    },
    setDOH: async () => {
      const res = await IntentUri.openUri({ url: 'intent:#Intent;action=android.settings.SETTINGS;end;' })
      if (!res.completed) throw new Error(res.message)
    },
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

  // @ts-expect-error yep.
  window.native = native
}
