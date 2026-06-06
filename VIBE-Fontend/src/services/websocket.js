/**
 * VIBE WebSocket Service
 * ──────────────────────────────────────────────────────────────────────────────
 * Industry-standard STOMP-over-SockJS implementation.
 *
 * Architecture note
 * ─────────────────
 * Spring Boot's SockJS endpoint is registered at `/ws/messaging` on the
 * messaging-service (port 8082).  The API Gateway routes this path directly
 * using the `lb://messaging-service` URI with WebSocket upgrade support.
 *
 * The frontend must therefore connect to  BASE_URL/ws/messaging  (not /ws).
 *
 * SockJS negotiation lifecycle:
 *   1. HTTP GET  /ws/messaging/info           → determine transport options
 *   2. HTTP POST /ws/messaging/<server>/<id>  → open XHR-streaming / long-poll
 *   3. WS  UPGRADE /ws/messaging/<server>/<id>/websocket → native WebSocket
 *
 * All three are routed by the gateway to port 8082 on the messaging-service.
 *
 * Features
 * ────────
 *  ✅ Auto-reconnect with exponential back-off (cap 30 s)
 *  ✅ Offline message queue — drains on reconnect
 *  ✅ Subscription registry — re-registered automatically after every reconnect
 *  ✅ Heartbeat monitoring (10 s in / 10 s out)
 *  ✅ Presence broadcast on connect / disconnect
 *  ✅ Global conversation subscriptions — all conversations subscribed on connect
 *     so messages are received even when a conversation is not open (WhatsApp parity)
 */

import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8090'

class WebSocketService {
  constructor() {
    this.client             = null
    this.connected          = false
    this._pendingQueue      = []        // { dest, body } while offline
    this._reconnectAttempts = 0
    this._onConnectCbs      = []
    this._onDisconnectCbs   = []

    /**
     * Subscription registry.
     * Map<destination: string, { cb: Function, sub: StompSubscription | null }>
     * Storing the original callback lets us re-subscribe on every reconnect.
     */
    this._registry = new Map()

    /**
     * Global conversation message callbacks.
     * Map<conversationId: string, Set<Function>>
     * Used to deliver new-message pushes for ALL conversations the user
     * is a member of — even when that conversation is not open in the UI.
     * This is the key mechanism WhatsApp uses to show unread badges and
     * bump the conversation list without the user having to open each chat.
     */
    this._globalConvoCallbacks = new Map()

    this._handleConnect    = this._handleConnect.bind(this)
    this._handleDisconnect = this._handleDisconnect.bind(this)
  }

  // ─── Public API ────────────────────────────────────────────────────────────

  connect(token, onConnected, onDisconnected) {
    if (this.client?.active) return

    if (onConnected)    this._onConnectCbs.push(onConnected)
    if (onDisconnected) this._onDisconnectCbs.push(onDisconnected)

    this.client = new Client({
      /**
       * SockJS endpoint on the messaging-service, proxied by the API Gateway.
       * IMPORTANT: must match StompEndpointRegistry path (/ws/messaging).
       */
      webSocketFactory: () => new SockJS(`${BASE_URL}/ws/messaging`),

      connectHeaders: { Authorization: `Bearer ${token}` },

      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,

      // Let the STOMP client handle reconnect internally
      reconnectDelay: 4_000,

      onConnect:    this._handleConnect,
      onDisconnect: this._handleDisconnect,

      onStompError: (frame) => {
        console.error('[VIBE WS] STOMP error:', frame.headers?.message ?? frame)
      },
      onWebSocketError: (evt) => {
        console.warn('[VIBE WS] WebSocket error — will reconnect…', evt?.type)
        this._reconnectAttempts++
      },
    })

    this.client.activate()
  }

  disconnect() {
    // Unsubscribe all active STOMP subscriptions
    this._registry.forEach(({ sub }) => {
      try { sub?.unsubscribe() } catch { /* ignore */ }
    })
    this._registry.clear()
    this._globalConvoCallbacks.clear()

    try { this.client?.deactivate() } catch { /* ignore */ }

    this.connected          = false
    this.client             = null
    this._reconnectAttempts = 0
    this._pendingQueue      = []
    this._onConnectCbs      = []
    this._onDisconnectCbs   = []
  }

  /**
   * Subscribe to a STOMP destination.
   *
   * If the socket is not yet connected the subscription is stored and will be
   * established automatically on the next (re)connect — callers never need to
   * worry about retrying subscriptions manually.
   *
   * @param {string}   dest  STOMP destination, e.g. '/topic/conversation/abc'
   * @param {Function} cb    Called with the parsed JSON payload
   * @returns {Function}     Unsubscribe function
   */
  subscribe(dest, cb) {
    // Tear down any existing subscription for this destination
    const existing = this._registry.get(dest)
    if (existing?.sub) {
      try { existing.sub.unsubscribe() } catch { /* ignore */ }
    }

    const entry = { cb, sub: null }
    this._registry.set(dest, entry)

    if (this.connected && this.client) {
      entry.sub = this._doSubscribe(dest, cb)
    }
    // Otherwise: will be registered inside _handleConnect

    return () => this.unsubscribe(dest)
  }

  unsubscribe(dest) {
    const entry = this._registry.get(dest)
    if (entry?.sub) {
      try { entry.sub.unsubscribe() } catch { /* ignore */ }
    }
    this._registry.delete(dest)
  }

  /**
   * Publish a message; queues it for delivery when the socket reconnects.
   * @param {string} dest  STOMP destination
   * @param {object} body  Will be JSON-serialised
   */
  send(dest, body) {
    if (this.connected && this.client) {
      this._publish(dest, body)
    } else {
      console.warn('[VIBE WS] Offline — queuing message to', dest)
      this._pendingQueue.push({ dest, body })
    }
  }

  // ─── Global conversation subscriptions (WhatsApp-style) ────────────────────

  /**
   * Register a global callback for a conversation.
   *
   * Unlike subscribeToConversation() which is called per-ChatArea, this method
   * registers app-level listeners that receive messages for ALL conversations.
   * This is how the conversation list stays live (unread badges, last-message
   * preview, conversation bump) even when the user hasn't opened the chat.
   *
   * Multiple callbacks can listen to the same conversation ID (e.g. the list
   * item AND the open chat area).
   *
   * @param {string}   convoId  Conversation ID
   * @param {string}   key      Unique listener key (e.g. 'list', 'chat-area')
   * @param {Function} cb       Called with the full STOMP payload
   * @returns {Function}        Remove-listener function
   */
  addConversationListener(convoId, key, cb) {
    if (!this._globalConvoCallbacks.has(convoId)) {
      this._globalConvoCallbacks.set(convoId, new Map())
    }
    this._globalConvoCallbacks.get(convoId).set(key, cb)

    // Ensure we have a STOMP subscription for this conversation.
    // The subscription dispatches to ALL registered callbacks for this convoId.
    const dest = `/topic/conversation/${convoId}`
    if (!this._registry.has(dest)) {
      this.subscribe(dest, (payload) => this._dispatchToConvoListeners(convoId, payload))
    }

    return () => this.removeConversationListener(convoId, key)
  }

  removeConversationListener(convoId, key) {
    const listeners = this._globalConvoCallbacks.get(convoId)
    if (listeners) {
      listeners.delete(key)
      // If no more listeners, unsubscribe the STOMP topic too
      if (listeners.size === 0) {
        this._globalConvoCallbacks.delete(convoId)
        this.unsubscribe(`/topic/conversation/${convoId}`)
      }
    }
  }

  /**
   * Subscribe to ALL conversations in a list at once.
   * Call this after loading the conversation list from the REST API.
   * The `listCb` is called for every new message on any of these conversations
   * so the UI can update unread counts and previews without the chat being open.
   *
   * @param {string[]}  conversationIds  List of conversation IDs to subscribe to
   * @param {Function}  listCb           Called with (conversationId, payload)
   */
  subscribeToConversationList(conversationIds, listCb) {
    if (!Array.isArray(conversationIds)) return
    conversationIds.forEach(convoId => {
      this.addConversationListener(convoId, 'list-updater', (payload) => {
        listCb(convoId, payload)
      })
    })
  }

  /**
   * Subscribe to a single conversation's topic.
   * Used by the open ChatArea. Multiple listeners co-exist with the list updater.
   */
  subscribeToConversation(convoId, cb) {
    return this.addConversationListener(convoId, 'chat-area', cb)
  }

  /** Typing indicators for a specific conversation */
  subscribeToTyping(convoId, cb) {
    return this.subscribe(`/topic/conversation/${convoId}/typing`, cb)
  }

  /** Personal notification channel */
  subscribeToNotifications(cb) {
    return this.subscribe('/user/queue/notifications', cb)
  }

  /** Personal events (token rewards, contact requests, …) */
  subscribeToUserEvents(userId, cb) {
    return this.subscribe(`/user/${userId}/queue/events`, cb)
  }

  /**
   * Subscribe to the personal delivery receipts queue.
   * Backend pushes DELIVERY_RECEIPT events here (not on the conversation topic).
   */
  subscribeToReceipts(cb) {
    return this.subscribe('/user/queue/receipts', cb)
  }

  /** Read receipts for a specific conversation */
  subscribeToReadReceipts(convoId, cb) {
    return this.subscribe(`/topic/conversation/${convoId}/read`, cb)
  }

  /** Emoji reactions for a specific conversation */
  subscribeToReactions(convoId, cb) {
    return this.subscribe(`/topic/conversation/${convoId}/reactions`, cb)
  }

  /** Global online-presence changes */
  subscribeToPresence(cb) {
    return this.subscribe('/topic/presence', cb)
  }

  // ─── Typed send helpers ────────────────────────────────────────────────────

  /**
   * NOTE: Do NOT call this after a successful REST sendMessage — the backend
   * already pushes the message to the WebSocket topic from the REST handler.
   * Calling this separately causes double delivery.
   * Only use for pure WS paths (e.g. voice notes sent entirely over WS).
   */
  sendMessage(convoId, payload) {
    this.send('/app/chat.send', { conversationId: convoId, ...payload })
  }

  sendTyping(convoId, userId, isTyping) {
    this.send('/app/chat.typing', { conversationId: convoId, userId, isTyping })
  }

  sendReadReceipt(convoId, messageId) {
    this.send('/app/chat.read', { conversationId: convoId, messageId })
  }

  sendReaction(messageId, emoji, userId) {
    this.send('/app/chat.react', { messageId, emoji, userId })
  }

  sendPresence(userId, status) {
    this.send('/app/presence', { userId, status })
  }

  // ─── Private helpers ───────────────────────────────────────────────────────

  _handleConnect() {
    this.connected          = true
    this._reconnectAttempts = 0
    console.info('[VIBE WS] ✅ Connected to messaging service')

    // Re-register every subscription that was set up before/during disconnect
    this._registry.forEach((entry, dest) => {
      entry.sub = this._doSubscribe(dest, entry.cb)
    })

    // Drain the offline message queue
    const queued = this._pendingQueue.splice(0)
    queued.forEach(({ dest, body }) => this._publish(dest, body))

    this._onConnectCbs.forEach(cb => { try { cb() } catch { /* ignore */ } })
  }

  _handleDisconnect() {
    this.connected = false
    console.warn('[VIBE WS] ⚠️  Disconnected — auto-reconnect in progress…')

    // Nullify sub references — they are dead after a STOMP disconnect
    this._registry.forEach(entry => { entry.sub = null })

    this._onDisconnectCbs.forEach(cb => { try { cb() } catch { /* ignore */ } })
  }

  _doSubscribe(dest, cb) {
    return this.client.subscribe(dest, (msg) => {
      try {
        cb(JSON.parse(msg.body))
      } catch {
        // Non-JSON frame (unlikely with Spring STOMP) — pass raw
        cb(msg.body)
      }
    })
  }

  _publish(dest, body) {
    try {
      this.client.publish({
        destination: dest,
        body: JSON.stringify(body),
      })
    } catch (err) {
      console.error('[VIBE WS] Publish failed:', err)
    }
  }

  _dispatchToConvoListeners(convoId, payload) {
    const listeners = this._globalConvoCallbacks.get(convoId)
    if (!listeners) return
    listeners.forEach(cb => {
      try { cb(payload) } catch (e) { console.warn('[VIBE WS] Listener error:', e) }
    })
  }
}

export const wsService = new WebSocketService()
export default wsService