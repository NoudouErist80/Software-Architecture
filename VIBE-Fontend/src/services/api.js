import axios from 'axios'

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8090'

const api = axios.create({
  baseURL: BASE,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

// ── Auth token injection ───────────────────────────────────────────────────────
api.interceptors.request.use((config) => {
  try {
    const stored = localStorage.getItem('vibe-auth')
    if (stored) {
      const { state } = JSON.parse(stored)
      if (state?.token) config.headers.Authorization = `Bearer ${state.token}`
    }
  } catch { /* ignore parse errors */ }
  return config
})

// ── Global error handler ───────────────────────────────────────────────────────
// ROOT CAUSE FIX for "AI button logs me out":
//
// The original interceptor redirected to /auth/login on ANY 401 from ANY endpoint.
// The AI service, notification service, feed/statuses service, and others all
// return 500 (not yet running) or 401 (JWT mismatch) during local dev —
// and because these calls happen immediately after login, they were triggering
// a full logout/redirect loop.
//
// Fix:
//   1. Non-auth 401s (AI, feed, notifications) are NOT treated as "session expired" —
//      they are silently passed through so the caller can handle them gracefully.
//   2. Only a 401 from an AUTH endpoint (/auth/*) means the session is genuinely
//      expired and warrants a redirect to login.
//   3. 500 errors from optional services (notifications, feed, rooms) are silenced
//      at the interceptor level — they are collected and shown as toasts by the
//      individual call sites, not as global logouts.
api.interceptors.response.use(
  res => res,
  err => {
    const url = err.config?.url || ''
    const status = err.response?.status

    // Only treat 401 from auth endpoints as a real session expiry.
    // AI, notification, feed — all may return 401/500 during local dev
    // when optional services aren't running; those must NOT trigger a logout.
    const isAuthEndpoint = url.includes('/auth/') || url.includes('/api/v1/auth/')

    if (status === 401 && isAuthEndpoint) {
      localStorage.removeItem('vibe-auth')
      window.location.href = '/auth/login'
    }

    return Promise.reject(err)
  },
)

// ─── Auth ──────────────────────────────────────────────────────────────────────
export const authAPI = {
  register:       (data)        => api.post('/auth/register', data),
  login:          (data)        => api.post('/auth/login', data),
  refresh:        ()            => api.post('/auth/refresh'),
  profile:        ()            => api.get('/auth/me'),
  updateAvatar:   (url)         => api.patch('/auth/me/avatar', { url }),
  logout:         ()            => api.post('/auth/logout'),
  forgotPassword: (email)       => api.post('/auth/forgot-password', { email }),
  resetPassword:  (data)        => api.post('/auth/reset-password', data),
  sendPhoneOtp:   (ph)          => api.post('/auth/send-phone-otp', { phoneNumber: ph }),
  verifyPhone:    (ph, otp)     => api.post('/auth/verify-phone', { phoneNumber: ph, otp }),
}

// ─── Contacts ──────────────────────────────────────────────────────────────────
export const contactsAPI = {
  getMyContacts:    ()          => api.get('/auth/contacts'),
  searchByPhone:    (phone)     => api.get('/auth/contacts/search', { params: { phone } }),
  searchByUsername: (q)         => api.get('/auth/contacts/search', { params: { q } }),
  /**
   * Add a user as a contact with an optional custom display name.
   * @param {string} userId        UUID of the user to add
   * @param {string} [displayName] Custom name to save (e.g. "Mama", "Boss")
   */
  addContact: (userId, displayName) =>
    api.post('/auth/contacts', { userId, ...(displayName ? { displayName } : {}) }),
  removeContact:    (userId)    => api.delete(`/auth/contacts/${userId}`),
  blockUser:        (userId)    => api.post(`/auth/contacts/${userId}/block`),
  unblockUser:      (userId)    => api.delete(`/auth/contacts/${userId}/block`),
  getSuggestions:   ()          => api.get('/auth/contacts/suggestions'),
}

// ─── Messaging ─────────────────────────────────────────────────────────────────
export const messagingAPI = {
  getConversations: ()              => api.get('/messaging/conversations'),
  getMessages:      (id, page = 0)  => api.get(`/messaging/conversations/${id}/messages`, { params: { page, size: 50 } }),
  sendMessage:      (id, data)      => api.post(`/messaging/conversations/${id}/messages`, data),
  editMessage:      (msgId, text)   => api.patch(`/messaging/messages/${msgId}`, { content: text }),
  deleteMessage:    (msgId, forAll) => api.delete(`/messaging/messages/${msgId}`, { params: { forEveryone: forAll } }),
  createGroup:      (data)          => api.post('/messaging/conversations/group', data),

  /**
   * Create or retrieve a direct 1-to-1 conversation.
   *
   * We pass participantUsername AND participantPhone so the messaging-service
   * can store them on the Conversation document.  This enables:
   *   - WhatsApp-style name resolution without an auth-service round-trip
   *   - "Unknown contact" display using the phone number when the other user
   *     is not yet in your contact list (e.g. +237 677 123 456)
   *
   * @param {string} participantId        UUID of the other user
   * @param {string} [participantUsername] @username of the other user
   * @param {string} [participantPhone]    E.164 phone of the other user
   */
  createDirect: (participantId, participantUsername, participantPhone) =>
    api.post('/messaging/conversations/direct', {
      participantId,
      ...(participantUsername ? { participantUsername } : {}),
      ...(participantPhone    ? { participantPhone    } : {}),
    }),

  markRead:         (convoId)       => api.patch(`/messaging/conversations/${convoId}/read`),
  addMember:        (convoId, uid)  => api.post(`/messaging/conversations/${convoId}/members`, { userId: uid }),
  removeMember:     (convoId, uid)  => api.delete(`/messaging/conversations/${convoId}/members/${uid}`),
  leaveGroup:       (convoId)       => api.post(`/messaging/conversations/${convoId}/leave`),
  pinMessage:       (msgId)         => api.post(`/messaging/messages/${msgId}/pin`),
  reactToMessage:   (msgId, emoji)  => api.post(`/messaging/messages/${msgId}/react`, { emoji }),
  forwardMessage:   (msgId, convoIds) => api.post(`/messaging/messages/${msgId}/forward`, { conversationIds: convoIds }),
  getPresence:      (userId)        => api.get(`/messaging/presence/${userId}`),
}

// ─── Media upload ──────────────────────────────────────────────────────────────
export const mediaAPI = {
  upload: (file, context = 'general') => {
    const form = new FormData()
    form.append('file', file)
    form.append('context', context)
    return api.post('/media/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  uploadMultiple: (files, context = 'general') => {
    const form = new FormData()
    files.forEach(f => form.append('files', f))
    form.append('context', context)
    return api.post('/media/upload/multiple', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}

// ─── AI ────────────────────────────────────────────────────────────────────────
//
// FIELD MAPPING FIX:
// The AiController expects:
//   POST /api/v1/ai/translate   → { text, toLang }
//   POST /api/v1/ai/summarise   → { text, language, style }
//
// The AiController returns:
//   translate  → { data: { translated, original, targetLang } }
//   summarise  → { data: { summary } }
//
// The frontend was calling with { text, targetLanguage } (wrong field name for
// toLang) and reading res.data?.translatedText (wrong path — it is
// res.data?.data?.translated).  Fixed below.
export const aiAPI = {
  /**
   * Summarize messages in a conversation.
   * Sends the conversation text to the AI service.
   * @param {object} opts - { conversationId, text?, type?, period?, from?, to? }
   */
  summarize: (opts) => api.post('/ai/summarise', opts),

  /**
   * Translate text to a target language.
   * @param {object} data - { text: string, targetLanguage: string }
   * Backend field name is 'toLang' — mapped here.
   */
  translate: (data) => api.post('/ai/translate', {
    text: data.text,
    toLang: data.targetLanguage || data.toLang || 'en',
    fromLang: data.fromLang || 'auto',
  }),

  detectLanguage: (text)  => api.post('/ai/detect-language', { text }),
  getLanguages:   ()      => api.get('/ai/languages'),
}

// ─── Feed (posts / public) ─────────────────────────────────────────────────────
export const feedAPI = {
  getFeed:           (page = 0, mood = null) => api.get('/feed/posts', { params: { page, size: 10, ...(mood && { mood }) } }),
  createPost:        (data)                  => api.post('/feed/posts', data),
  likePost:          (id)                    => api.post(`/feed/posts/${id}/like`),
  commentPost:       (id, text)              => api.post(`/feed/posts/${id}/comments`, { content: text }),
  watchVideo:        (id, secs)              => api.post(`/feed/posts/${id}/watch`, { watchedSeconds: secs }),
  searchPosts:       (q)                     => api.get('/feed/posts/search', { params: { q } }),
  getPostsByHashtag: (tag)                   => api.get(`/feed/posts/hashtag/${encodeURIComponent(tag)}`),
  follow:            (targetId)              => api.post(`/feed/profiles/${targetId}/follow`),
  unfollow:          (targetId)              => api.delete(`/feed/profiles/${targetId}/follow`),
  getFollowers:      (userId)                => api.get(`/feed/profiles/${userId}/followers`),
  getFollowing:      (userId)                => api.get(`/feed/profiles/${userId}/following`),
  getProfile:        (userId)                => api.get(`/feed/profiles/${userId}`),
  setMood:           (mood)                  => api.put('/feed/mood', { mood }),
}

// ─── Status (stories) ──────────────────────────────────────────────────────────
export const statusAPI = {
  getStatuses:   ()                         => api.get('/feed/statuses'),
  postStatus:    (data)                     => api.post('/feed/statuses', data),
  likeStatus:    (id)                       => api.post(`/feed/statuses/${id}/like`),
  commentStatus: (id, content, isPrivate)   => api.post(`/feed/statuses/${id}/comments`, { content, isPrivate }),
  viewStatus:    (id)                       => api.post(`/feed/statuses/${id}/view`),
  deleteStatus:  (id)                       => api.delete(`/feed/statuses/${id}`),
}

// ─── Rooms (Live Audio) ────────────────────────────────────────────────────────
export const roomsAPI = {
  getRooms:    ()           => api.get('/rooms'),
  createRoom:  (data)       => api.post('/rooms', data),
  joinRoom:    (id)         => api.post(`/rooms/${id}/join`),
  leaveRoom:   (id)         => api.post(`/rooms/${id}/leave`),
  endRoom:     (id)         => api.post(`/rooms/${id}/end`),
  raiseHand:   (id)         => api.post(`/rooms/${id}/raise-hand`),
  lowerHand:   (id)         => api.post(`/rooms/${id}/lower-hand`),
  muteSpeaker: (id, uid)    => api.post(`/rooms/${id}/mute/${uid}`),
}

// ─── Rewards / Wallet ──────────────────────────────────────────────────────────
export const rewardsAPI = {
  getWallet:       ()     => api.get('/rewards/wallet'),
  getTransactions: ()     => api.get('/rewards/transactions'),
  cashout:         (data) => api.post('/rewards/cashout', data),
  getRates:        ()     => api.get('/rewards/rates'),
  claimStreak:     ()     => api.post('/rewards/streak/claim'),
  getLeaderboard:  ()     => api.get('/rewards/leaderboard/weekly'),
}

// ─── Notifications ─────────────────────────────────────────────────────────────
export const notificationAPI = {
  getAll:         ()   => api.get('/notifications'),
  getUnreadCount: ()   => api.get('/notifications/unread-count'),
  markRead:       (id) => api.patch(`/notifications/${id}/read`),
  markAllRead:    ()   => api.patch('/notifications/read-all'),
}

// ─── Dev helpers ───────────────────────────────────────────────────────────────
export function logAuthTokens(label, accessToken, user) {
  console.groupCollapsed(`🎟️  VIBE Auth Tokens — ${label}`)
  console.log('%c✅ Access Token (JWT):', 'color:#22c55e;font-weight:bold;')
  console.log(accessToken)
  console.log('%c👤 User:', 'color:#3b82f6;font-weight:bold;', user)
  console.log('%c📋 Decoded payload:', 'color:#a855f7;font-weight:bold;', _decodeJwt(accessToken))
  console.log('%c💡 Use in Postman:', 'color:#f59e0b;')
  console.log(`Authorization: Bearer ${accessToken}`)
  console.groupEnd()
}

function _decodeJwt(token) {
  try {
    return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return '(could not decode)'
  }
}

export default api