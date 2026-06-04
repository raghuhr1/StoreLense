import { useEffect, useRef, useCallback } from 'react'
import { tokenStore } from '@/utils/storage'

interface WsMessage {
  type: string
  [key: string]: unknown
}

/**
 * Subscribes to a STOMP-over-SockJS topic for real-time push notifications.
 * Reconnects automatically on disconnect.
 */
export function useWebSocket(
  topic: string,
  onMessage: (msg: WsMessage) => void,
  enabled = true,
) {
  const clientRef  = useRef<{ unsubscribe: () => void } | null>(null)
  const stableOnMessage = useRef(onMessage)
  stableOnMessage.current = onMessage

  const connect = useCallback(() => {
    if (typeof window === 'undefined' || !enabled) return

    const token = tokenStore.getAccess()
    if (!token) return

    // Use native WebSocket with STOMP-like framing for simplicity.
    // In production replace with @stomp/stompjs + SockJS.
    const ws = new WebSocket(
      `${import.meta.env.VITE_WS_URL}?token=${encodeURIComponent(token)}`
    )

    ws.onopen = () => {
      ws.send(JSON.stringify({ type: 'SUBSCRIBE', topic }))
    }

    ws.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data) as WsMessage
        if (parsed.type !== 'HEARTBEAT') {
          stableOnMessage.current(parsed)
        }
      } catch {
        // ignore malformed messages
      }
    }

    ws.onclose = () => {
      // Reconnect after 5s on unexpected close
      setTimeout(connect, 5_000)
    }

    clientRef.current = { unsubscribe: () => ws.close() }
  }, [topic, enabled])

  useEffect(() => {
    connect()
    return () => clientRef.current?.unsubscribe()
  }, [connect])
}
