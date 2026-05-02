export interface AuthParameters {
  url: string
  callbackUrl: string
}

export interface AuthLegacyParameters {
  url: string
  callbackScheme: string
}

export interface AuthResults {
  url: string
}

export interface AuthSessionPlugin {
  auth: (params: AuthParameters) => Promise<AuthResults>
  authLegacy: (params: AuthLegacyParameters) => Promise<AuthResults>
}
