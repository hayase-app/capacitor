import { proxyMarker, isObject } from 'abslink'
import { transferHandlers, type TransferHandler } from 'abslink'

const errorSerializer: TransferHandler<Error, { message: string, name: string, stack?: string }> = {
  canHandle: (value): value is Error => isObject(value) && value instanceof Error && !(proxyMarker in value),
  serialize: (value) => [({ message: value.message, name: value.name, stack: value.stack }), []],
  deserialize: (serialized) => Object.assign(new Error(serialized.message), serialized)
}

transferHandlers.set('error', errorSerializer)
