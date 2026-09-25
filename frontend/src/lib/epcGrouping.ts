import type { IdentifyEpcResponse } from '@/types'

export interface EpcGroup {
  key:     string
  epcs:    string[]
  info:    IdentifyEpcResponse | null
  loading: boolean
  /** The lookup failed (network/auth/5xx — NOT a confirmed 404). Distinct
   *  from "confirmed unknown": callers must never treat this as foreign,
   *  since that would hide a possibly-real item because of a transient
   *  error rather than because it genuinely isn't in our system. */
  failed:  boolean
}

/** Collapses same-product EPCs into one group with a quantity count — several
 *  identical shirts alarming together should show one photo, not N copies of
 *  the same one. Groups by resolved productId; unresolved/unknown/failed tags
 *  each stay their own group since we can't confirm they're the same item. */
export function groupEpcs(
  epcs: string[],
  queries: { data?: IdentifyEpcResponse | null; isLoading: boolean; isError?: boolean }[]
): EpcGroup[] {
  const groups = new Map<string, EpcGroup>()
  epcs.forEach((epc, i) => {
    const q       = queries[i]
    const info    = q?.data ?? null
    const loading = !!q?.isLoading
    const failed  = !!q?.isError
    const key     = info?.productId ?? (loading ? `loading-${epc}` : failed ? `failed-${epc}` : `unknown-${epc}`)
    const existing = groups.get(key)
    if (existing) {
      existing.epcs.push(epc)
      if (info) { existing.info = info; existing.loading = false; existing.failed = false }
    } else {
      groups.set(key, { key, epcs: [epc], info, loading, failed })
    }
  })
  return Array.from(groups.values())
}

/** True for a group that's confirmed to have nothing worth showing — not
 *  still loading, not a failed lookup (which must be retried, not hidden),
 *  and genuinely not registered in our system. This is the only case safe
 *  to drop from display. */
export function isKnownOrPending(group: EpcGroup): boolean {
  return group.loading || group.failed || !!group.info
}
