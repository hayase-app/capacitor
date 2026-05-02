import { join, resolve } from 'node:path'
import { platform } from 'node:process'

import CopyWebpackPlugin from 'copy-webpack-plugin'
import webpack from 'webpack'
import LicensePlugin from 'webpack-license-plugin'
import 'webpack-dev-server'

const dirname = import.meta.dirname || new URL('.', import.meta.url).pathname

const nodeJSNativeFolder = join(dirname, 'public', 'nodejs').replaceAll('\\', '/')

const isMac = platform === 'darwin'

/** @type {import('webpack').Configuration[]} */
const config = [
  {
    entry: [join(dirname, 'src', 'background.ts')],
    output: {
      path: join(dirname, 'build', 'nodejs'),
      filename: 'index.js',
      clean: true,
      publicPath: './'
    },
    module: {
      noParse: (filePath) => filePath.includes('y.cjs'),
      rules: [
        {
          test: /\.tsx?$/,
          use: [
            {
              loader: 'ts-loader',
              options: {
                transpileOnly: true
              }
            }
          ]
        }
      ]
    },
    externals: {
      'utp-native': 'commonjs utp-native',
      bridge: 'commonjs bridge',
      'node-gyp-build': 'commonjs node-gyp-build',
      '@thaunknown/yencode': 'commonjs @thaunknown/yencode'
    },
    resolve: {
      aliasFields: [],
      extensions: ['.ts', '.tsx', '.js', '.json'],
      mainFields: ['module', 'main', 'node'],
      alias: {
        wrtc: false,
        'node-datachannel': false,
        'http-tracker': resolve('./node_modules/bittorrent-tracker/lib/client/http-tracker.js'),
        'webrtc-polyfill': false // no webrtc on mobile, need the resources
      }
    },
    target: 'node',
    devServer: {
      devMiddleware: {
        writeToDisk: true
      },
      hot: false,
      client: false
    },
    plugins: [
      new CopyWebpackPlugin({
        patterns: isMac
          ? [
              {
                from: nodeJSNativeFolder + '/**/!(*.ts|*.map|*.node|*.bare|*.md|*.cts)',
                context: nodeJSNativeFolder
              }
            ]
          : [
              {
                from: nodeJSNativeFolder + '/**/*.{js,cjs,json}',
                context: nodeJSNativeFolder
              },
              {
                from: nodeJSNativeFolder + '/**/prebuilds/android*/node.napi.node',
                context: nodeJSNativeFolder
              }
            ]
      }),
      new webpack.optimize.LimitChunkCountPlugin({
        maxChunks: 1
      }),
      new LicensePlugin({
        outputFilename: 'index.js.LICENSE.txt',
        excludedPackageTest: (packageName) => packageName === 'torrent-client',
        licenseOverrides: {
          'compact2string@1.4.1': 'BSD-3-Clause'
        },
        additionalFiles: {
          'index.js.LICENSE.txt': (packages) => packages.map(({ name, version, license, licenseText, noticeText }) => `${name} ${version} (${license}) \n${noticeText ?? ''}\n${licenseText}`).join('\n\n')
        },
        unacceptableLicenseTest: (licenseId) => !['Apache-2.0', 'MIT', 'ISC', 'BSD-3-Clause', 'BSD-2-Clause', 'CC0-1.0'].includes(licenseId)
      })
    ]
  },
  {
    resolve: {
      extensions: ['.ts', '.tsx', '.js', '.json']
    },
    plugins: [
      new webpack.optimize.LimitChunkCountPlugin({
        maxChunks: 1
      }),
      new LicensePlugin({
        outputFilename: 'preload.js.LICENSE.txt',
        licenseOverrides: {
          'compact2string@1.4.1': 'BSD-3-Clause'
        },
        additionalFiles: {
          'preload.js.LICENSE.txt': (packages) => packages.map(({ name, version, license, licenseText, noticeText }) => `${name} ${version} (${license}) \n${noticeText ?? ''}\n${licenseText}`).join('\n\n')
        },
        unacceptableLicenseTest: (licenseId) => !['Apache-2.0', 'MIT', 'ISC', 'BSD-3-Clause', 'BSD-2-Clause'].includes(licenseId)
      })
    ],
    module: {
      rules: [
        {
          test: /\.m?js$/,
          resolve: {
            fullySpecified: false
          }
        },
        {
          test: /\.tsx?$/,
          use: [
            { loader: 'ts-loader', options: { transpileOnly: true } }
          ]
        }
      ]
    },
    entry: [join(dirname, 'src', 'preload.ts')],
    output: {
      path: join(dirname, 'build'),
      filename: 'preload.js',
      clean: true,
      publicPath: './'
    }
  }
]

if (isMac && config[0]?.resolve?.alias) {
  config[0].resolve.alias['cross-fetch-ponyfill'] = resolve('./src/fetch.js')
}

export default config
