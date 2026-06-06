import React, { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore, useUIStore, useMessagingStore, useWalletStore } from './store'
import { wsService } from './services/websocket'
import { rewardsAPI } from './services/api'
import { LoginPage, RegisterPage } from './pages/AuthPage'
import VibeMessenger from './components/messenger/MessengerLayout'
import VibePublic from './components/public/VibePublic'
import ToastContainer from './components/shared/Toast'
import SectionSwitcher from './components/shared/SectionSwitcher'
import ComposerModal from './components/shared/ComposerModal'

function ProtectedRoute({ children }) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/auth/login" replace />
  return children
}

function AppShell() {
  const { activeSection } = useUIStore()
  const { user } = useAuthStore()
  const { addToast } = useUIStore()
  const { setBalance } = useWalletStore()
  const {
    addMessage, addNewConversation, setOnline, setConversations, conversations,
  } = useMessagingStore()

  // ── Global WS subscriptions after connect ────────────────────────────────
  useEffect(() => {
    if (!user?.id) return

    // ── Personal notification channel ─────────────────────────────────────
    wsService.subscribeToNotifications((notif) => {
      addToast(notif.message || 'New notification', 'info')
    })

    // ── Personal events (token rewards, contact requests, …) ──────────────
    wsService.subscribeToUserEvents(user.id, (event) => {
      if (event.type === 'TOKEN_REWARD') {
        rewardsAPI.getWallet()
          .then(r => setBalance && setBalance(r.data?.data?.balance || 0))
          .catch(() => {})
      }
      if (event.type === 'CONTACT_REQUEST') {
        addToast(`${event.senderName || 'Someone'} wants to connect with you 🤝`, 'info')
      }
    })

    // ── Personal message queue (/user/queue/messages) ─────────────────────
    //
    // CRITICAL: This is the "push notification" channel for messages.
    //
    // The backend's MessagingService pushes every new message to TWO places:
    //   1. /topic/conversation/{id}       — the open chat area subscribes here
    //   2. /user/{recipientId}/queue/messages — THIS subscription
    //
    // This personal queue ensures that when Daniel is connected but hasn't
    // opened Neymar's conversation yet, he still receives the message and
    // the conversation list updates (unread badge, preview, bump to top).
    //
    // Without this subscription the message was only received if Daniel had
    // that specific conversation open — causing "message not received" bugs.
    //
    wsService.subscribeToReceipts(() => {
      // Receipts (DELIVERY_RECEIPT) are handled per-conversation by ChatArea.
      // App-level receipt handling can be added here if needed.
    })

    const unsubMessages = wsService.subscribe('/user/queue/messages', (payload) => {
      const msgType  = payload?.type
      const envelope = payload?.message || payload

      if (msgType === 'NEW_MESSAGE' && envelope?.conversationId) {
        const convoId  = envelope.conversationId
        const senderId = envelope.senderId

        // Don't process our own messages — ChatArea handles those optimistically
        if (senderId === user.id) return

        // Normalise the message to the frontend shape
        const normalised = {
          id:             envelope.id,
          conversationId: convoId,
          senderId:       senderId,
          senderName:     envelope.senderUsername || envelope.senderName || 'User',
          content:        envelope.content || '',
          type:           envelope.type === 'NEW_MESSAGE' ? 'TEXT' : (envelope.type || 'TEXT'),
          mediaUrl:       envelope.mediaUrl || null,
          createdAt:      envelope.createdAt || new Date().toISOString(),
          status:         envelope.deliveryStatus || 'DELIVERED',
          deleted:        envelope.isDeleted || false,
          edited:         envelope.isEdited  || false,
        }

        // Add to message store so the conversation is ready if the user opens it
        addMessage(convoId, normalised)

        // Bump the conversation to the top and increment unread count
        setConversations(prev => {
          const idx = prev.findIndex(c => c.id === convoId)
          if (idx < 0) {
            // Unknown conversation — re-fetch the list to pick it up
            // (happens when a message arrives from a brand-new conversation)
            import('./services/api').then(({ messagingAPI }) => {
              messagingAPI.getConversations().then(res => {
                const data = res.data?.data || res.data?.data?.content || []
                if (Array.isArray(data)) setConversations(data)
              }).catch(() => {})
            })
            return prev
          }
          const existing = prev[idx]
          const updated = {
            ...existing,
            lastMessagePreview: normalised.content || existing.lastMessagePreview,
            lastMessageAt: normalised.createdAt,
            unreadCounts: {
              ...(existing.unreadCounts || {}),
              [user.id]: ((existing.unreadCounts || {})[user.id] || 0) + 1,
            },
          }
          return [updated, ...prev.filter((_, i) => i !== idx)]
        })
      }
    })

    // ── Global online-presence changes ────────────────────────────────────
    wsService.subscribeToPresence((event) => {
      setOnline(event.userId, event.status === 'online')
    })

    // Announce ourselves ONLINE and keep refreshing it. The backend expires a
    // user's presence after ~65s, so we re-send every 30s. Without this the
    // server never marks anyone online and presence always shows "offline".
    wsService.sendPresence(user.id, 'online')
    const presenceTimer = setInterval(() => {
      wsService.sendPresence(user.id, 'online')   // refreshes the Redis TTL
    }, 30_000)
    const goOffline = () => { try { wsService.sendPresence(user.id, 'offline') } catch { /* ignore */ } }
    window.addEventListener('beforeunload', goOffline)

    return () => {
      clearInterval(presenceTimer)
      window.removeEventListener('beforeunload', goOffline)
      goOffline()
      wsService.unsubscribe('/user/queue/notifications')
      wsService.unsubscribe(`/user/${user.id}/queue/events`)
      wsService.unsubscribe('/user/queue/messages')
      wsService.unsubscribe('/topic/presence')
    }
  }, [user?.id])

  return (
    <div className="h-screen w-screen flex flex-col bg-white overflow-hidden">
      <SectionSwitcher />
      <div className="flex-1 overflow-hidden">
        {activeSection === 'messenger' && <VibeMessenger />}
        {activeSection === 'public'    && <VibePublic />}
      </div>
      <ComposerModal />
      <ToastContainer />
    </div>
  )
}

export default function App() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated)
  const token = useAuthStore(s => s.token)

  // ── WebSocket lifecycle ───────────────────────────────────────────────────
  useEffect(() => {
    if (isAuthenticated && token) {
      if (!wsService.connected) {
        wsService.connect(
          token,
          () => console.info('[VIBE WS] ✅ Real-time connection established'),
          () => console.warn('[VIBE WS] ⚠️  Connection lost — reconnecting…')
        )
      }
    } else {
      wsService.disconnect()
    }
    // Don't return a cleanup that disconnects on re-render —
    // only disconnect on actual logout (isAuthenticated false above).
  }, [isAuthenticated, token])

  return (
    <Routes>
      <Route path="/"              element={isAuthenticated ? <Navigate to="/app" replace /> : <Navigate to="/auth/login" replace />} />
      <Route path="/auth/login"    element={<LoginPage />} />
      <Route path="/auth/register" element={<RegisterPage />} />
      <Route path="/app"           element={<ProtectedRoute><AppShell /></ProtectedRoute>} />
      <Route path="*"              element={<Navigate to="/" replace />} />
    </Routes>
  )
}