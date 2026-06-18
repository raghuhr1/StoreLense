'use client'

import { useEffect, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface Suggestion {
  id:        string
  label:     string
  sublabel?: string
  category:  string
  value:     string
}

interface Props {
  value:           string
  onChange:        (value: string) => void
  suggestions:     Suggestion[]
  placeholder?:    string
  className?:      string
  maxSuggestions?: number
}

export default function SearchWithSuggestions({
  value,
  onChange,
  suggestions,
  placeholder    = 'Search…',
  className,
  maxSuggestions = 8,
}: Props) {
  const [open, setOpen]           = useState(false)
  const [activeIdx, setActiveIdx] = useState(-1)
  const containerRef              = useRef<HTMLDivElement>(null)
  const inputRef                  = useRef<HTMLInputElement>(null)

  const q = value.trim().toLowerCase()

  const filtered = q.length === 0 ? [] : suggestions
    .filter(s =>
      s.label.toLowerCase().includes(q) ||
      (s.sublabel?.toLowerCase().includes(q) ?? false) ||
      s.value.toLowerCase().includes(q)
    )
    .slice(0, maxSuggestions)

  // Group by category preserving insertion order
  const groups = filtered.reduce<Record<string, Suggestion[]>>((acc, s) => {
    (acc[s.category] ??= []).push(s)
    return acc
  }, {})

  const flat = Object.values(groups).flat()

  // Close when clicking outside
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
        setActiveIdx(-1)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // Reset active index when query changes
  useEffect(() => { setActiveIdx(-1) }, [value])

  function select(s: Suggestion) {
    onChange(s.value)
    setOpen(false)
    setActiveIdx(-1)
    inputRef.current?.blur()
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (!open) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIdx(i => Math.min(i + 1, flat.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIdx(i => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && activeIdx >= 0) {
      e.preventDefault()
      select(flat[activeIdx])
    } else if (e.key === 'Escape') {
      setOpen(false)
      setActiveIdx(-1)
    }
  }

  const showDropdown = open && q.length > 0

  return (
    <div ref={containerRef} className={cn('relative', className)}>
      <div className="relative">
        <Search
          size={15}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"
        />
        <input
          ref={inputRef}
          value={value}
          onChange={e => { onChange(e.target.value); setOpen(true) }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          className="input-field pl-9 pr-8"
          autoComplete="off"
        />
        {value && (
          <button
            type="button"
            onClick={() => { onChange(''); setOpen(false); inputRef.current?.focus() }}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
            aria-label="Clear search"
          >
            <X size={14} />
          </button>
        )}
      </div>

      {showDropdown && (
        <div className="absolute z-50 mt-1 w-full min-w-[280px] bg-white rounded-xl border border-gray-200 shadow-lg overflow-hidden">
          {flat.length === 0 ? (
            <p className="text-xs text-gray-400 text-center py-4 px-3">
              No suggestions for &ldquo;{value}&rdquo;
            </p>
          ) : (
            Object.entries(groups).map(([category, items]) => (
              <div key={category}>
                <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider px-3 pt-2.5 pb-1 select-none">
                  {category}
                </p>
                {items.map(s => {
                  const idx = flat.indexOf(s)
                  return (
                    <button
                      key={s.id}
                      type="button"
                      // mousedown fires before blur, so we prevent default to keep focus
                      onMouseDown={e => { e.preventDefault(); select(s) }}
                      className={cn(
                        'w-full text-left flex items-center justify-between gap-3 px-3 py-2 text-sm transition-colors',
                        idx === activeIdx
                          ? 'bg-brand-50 text-brand-700'
                          : 'hover:bg-gray-50 text-gray-800'
                      )}
                    >
                      <span className="font-medium truncate">{s.label}</span>
                      {s.sublabel && (
                        <span className="text-xs text-gray-400 shrink-0">{s.sublabel}</span>
                      )}
                    </button>
                  )
                })}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
