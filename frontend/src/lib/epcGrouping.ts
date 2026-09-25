import type { IdentifyEpcResponse } from '@/types'

export interface EpcGroup {
  key:     string
  epcs:    string[]
  info:    IdentifyEpcResponse | null
  loading: boolean
}

/** Collapses same-product EPCs into one group with a quantity count — several
 *  identical shirts alarming together should show one photo, not N copies of
 *  the same one. Groups by resolved productId; unresolved/unknown tags each
 *  stay their own group since we can't confirm they're the same item. */
export function groupEpcs(
  epcs: string[],
  queries: { data?: IdentifyEpcResponse | null; isLoading: boolean }[]
): EpcGroup[] {
  const groups = new Map<string, EpcGroup>()
  epcs.forEach((epc, i) => {
    const info    = queries[i]?.data ?? null
    const loading = !!queries[i]?.isLoading
    const key     = info?.productId ?? (loading ? `loading-${epc}` : `unknown-${epc}`)
    const existing = groups.get(key)
    if (existing) {
      existing.epcs.push(epc)
      if (info) { existing.info = info; existing.loading = false }
    } else {
      groups.set(key, { key, epcs: [epc], info, loading })
    }
  })
  return Array.from(groups.values())
}
