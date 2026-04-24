const mode = process.env.NODE_ENV?.trim() ?? 'development'

module.exports = {
  appId: 'app.hayase',
  appName: 'Hayase',
  webDir: 'build',
  loggingBehavior: 'none',
  android: {
    webContentsDebuggingEnabled: mode === 'development'
  },
  plugins: {
    SplashScreen: { launchShowDuration: 0 },
    CapacitorHttp: { enabled: false },
    CapacitorNodeJS: { nodeDir: 'nodejs', startMode: 'manual' },
    SystemBars: { insetsHandling: 'disable', hidden: true }
  },
  server: {
    cleartext: true,
    // url: mode === 'development' ? 'http://localhost:5001' : 'https://hayase.app'
    url: 'https://hayase.app',
    allowNavigation: ['hayase.app']
  }
}
