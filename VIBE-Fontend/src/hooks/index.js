import { useEffect, useCallback, useRef } from 'react'
import { useAuthStore, useMessagingStore, useWalletStore, useStatusStore } from '../store'
import { messagingAPI, rewardsAPI, statusAPI } from '../services/api'
import { wsService } from '../services/websocket'

// ─── WebSocket connection ──────────────────────────────────────────────────────
/**
 * Manages the WS connection lifecycle.
 * Actual subscriptions are set up in App.jsx (AppShell) after connect,
 * so this hook just ensures connect/disconnect happens at the right time.
 */
export function useWebSocket() {
  const { token, isAuthenticated } = useAuthStore()
  const connected = useRef(false)

  useEffect(() => {
    if (!isAuthenticated || !token) return
    if (!wsService.connected && !connected.current) {
      connected.current = true
      wsService.connect(
        token,
        () => { connected.current = true },
        () => { connected.current = false }
      )
    }
    return () => {
      // Don't disconnect on re-render; App.jsx handles that on logout.
    }
  }, [isAuthenticated, token])
}

// ─── Load conversations ────────────────────────────────────────────────────────
export function useConversations() {
  const { setConversations } = useMessagingStore()
  const { isAuthenticated } = useAuthStore()
  const load = useCallback(async () => {
    if (!isAuthenticated) return
    try {
      const res = await messagingAPI.getConversations()
      const data = res.data?.data || res.data?.data?.content || []
      if (Array.isArray(data)) setConversations(data)
    } catch {}
  }, [isAuthenticated])
  useEffect(() => { load() }, [load])
  return { reload: load }
}

// ─── Load messages for a conversation ─────────────────────────────────────────
export function useMessages(conversationId) {
  const { setMessages, messages, addMessage } = useMessagingStore()
  const { isAuthenticated } = useAuthStore()
  useEffect(() => {
    if (!conversationId || !isAuthenticated) return
    const load = async () => {
      try {
        const res = await messagingAPI.getMessages(conversationId)
        const data = res.data?.data?.content || res.data?.data || []
        if (Array.isArray(data) && data.length > 0) {
          setMessages(conversationId, [...data].reverse())
        }
      } catch {}
    }
    load()
    // Use addConversationListener so it co-exists with the list-updater listener
    const unsub = wsService.addConversationListener(conversationId, 'hook', (payload) => {
      const envelope = payload?.message || payload
      if (envelope?.senderId) addMessage(conversationId, envelope)
    })
    return unsub
  }, [conversationId, isAuthenticated])
  return messages[conversationId] || []
}

// ─── Load wallet ───────────────────────────────────────────────────────────────
export function useWallet() {
  const { setBalance, setTransactions, setStreak } = useWalletStore()
  const { isAuthenticated } = useAuthStore()
  useEffect(() => {
    if (!isAuthenticated) return
    const load = async () => {
      try {
        const [wallet, tx] = await Promise.all([rewardsAPI.getWallet(), rewardsAPI.getTransactions()])
        setBalance(wallet.data?.data?.balance || wallet.data?.balance || 0)
        setTransactions(tx.data?.data?.content || tx.data?.content || [])
        setStreak(wallet.data?.data?.streakDays || wallet.data?.streakDays || 0)
      } catch {}
    }
    load()
  }, [isAuthenticated])
}

// ─── Load statuses ─────────────────────────────────────────────────────────────
export function useStatuses() {
  const { setStatuses } = useStatusStore()
  const { isAuthenticated } = useAuthStore()
  useEffect(() => {
    if (!isAuthenticated) return
    const load = async () => {
      try {
        const res = await statusAPI.getStatuses()
        const data = res.data?.data || res.data?.data?.content || []
        if (Array.isArray(data)) setStatuses(data)
      } catch {}
    }
    load()
  }, [isAuthenticated])
}

// ─── Typing indicator ──────────────────────────────────────────────────────────
export function useTypingIndicator(conversationId) {
  const { user } = useAuthStore()
  const timer = useRef(null)
  const send = useCallback(() => {
    if (!conversationId || !user?.id) return
    wsService.sendTyping(conversationId, user.id, true)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => wsService.sendTyping(conversationId, user.id, false), 2000)
  }, [conversationId, user?.id])
  useEffect(() => () => clearTimeout(timer.current), [])
  return send
}