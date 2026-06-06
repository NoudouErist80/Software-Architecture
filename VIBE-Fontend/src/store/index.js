import { create } from 'zustand'
import { persist } from 'zustand/middleware'

// ─── Auth Store ────────────────────────────────────────────────────────────────
export const useAuthStore = create(
  persist(
    (set) => ({
      user: null, token: null, isAuthenticated: false, hasPublicAccount: false,
      login: (user, token) => set({ user, token, isAuthenticated: true,
        hasPublicAccount: user?.role === 'CREATOR' || user?.role === 'ADMIN' }),
      logout: () => set({ user: null, token: null, isAuthenticated: false, hasPublicAccount: false }),
      updateUser: (updates) => set(s => ({ user: { ...s.user, ...updates } })),
      upgradeToCreator: () => set(s => ({ hasPublicAccount: true, user: { ...s.user, role: 'CREATOR' } })),
    }),
    { name: 'vibe-auth' }
  )
)

// ─── UI Store ──────────────────────────────────────────────────────────────────
export const useUIStore = create((set) => ({
  activeSection: 'messenger', activePage: 'home', sidebarOpen: true,
  composerOpen: false, composerType: null, toasts: [],
  setSection:    (s)    => set({ activeSection: s }),
  setPage:       (p)    => set({ activePage: p }),
  toggleSidebar: ()     => set(s => ({ sidebarOpen: !s.sidebarOpen })),
  openComposer:  (type) => set({ composerOpen: true, composerType: type }),
  closeComposer: ()     => set({ composerOpen: false, composerType: null }),
  addToast: (msg, type = 'info') => {
    const id = Date.now() + Math.random()
    set(s => ({ toasts: [...s.toasts, { id, msg, type }] }))
    setTimeout(() => set(s => ({ toasts: s.toasts.filter(t => t.id !== id) })), 4500)
  },
}))

// ─── Messaging Store ───────────────────────────────────────────────────────────
export const useMessagingStore = create((set, get) => ({
  conversations: [],
  selectedConversation: null,
  messages: {},
  onlineUsers: new Set(),
  lastSeen: {},
  typingUsers: {},
  blockedUsers: new Set(),
  readReceipts: {},

  setConversations: (list) => set({ conversations: Array.isArray(list) ? list : [] }),
  selectConvo: (convo) => set({ selectedConversation: convo }),

  bumpConversation: (convoId, lastMessagePreview, timestamp, userId) =>
    set(s => {
      const idx = s.conversations.findIndex(c => c.id === convoId)
      if (idx < 0) return s
      const existing = s.conversations[idx]
      const updated = {
        ...existing, lastMessagePreview, lastMessageAt: timestamp,
        unreadCounts: s.selectedConversation?.id !== convoId
          ? { ...(existing.unreadCounts || {}), [userId]: ((existing.unreadCounts || {})[userId] || 0) + 1 }
          : existing.unreadCounts,
      }
      return { conversations: [updated, ...s.conversations.filter((_, i) => i !== idx)] }
    }),

  addNewConversation: (convo) =>
    set(s => ({
      conversations: s.conversations.some(c => c.id === convo.id)
        ? s.conversations : [convo, ...s.conversations]
    })),

  // setMessages accepts array OR updater function
  setMessages: (convoId, msgs) =>
    set(s => ({
      messages: {
        ...s.messages,
        [convoId]: typeof msgs === 'function'
          ? msgs(s.messages[convoId] || [])
          : Array.isArray(msgs) ? msgs : []
      }
    })),

  addMessage: (convoId, msg) =>
    set(s => {
      const existing = s.messages[convoId] || []
      // Replace optimistic temp message with real one to avoid duplicates
      const withoutDupe = existing.filter(m => {
        if (m.id === msg.id) return false
        if (m.id?.startsWith('tmp-') && msg.id && !msg.id.startsWith('tmp-')
          && m.senderId === msg.senderId && m.content === msg.content
          && Math.abs(new Date(msg.createdAt) - new Date(m.createdAt)) < 8000) return false
        return true
      })
      return { messages: { ...s.messages, [convoId]: [...withoutDupe, msg] } }
    }),

  updateMessage: (convoId, msgId, updates) =>
    set(s => ({
      messages: {
        ...s.messages,
        [convoId]: (s.messages[convoId] || []).map(m =>
          m.id === msgId ? { ...m, ...updates } : m
        ),
      },
    })),

  deleteMessage: (convoId, msgId, forEveryone) =>
    set(s => ({
      messages: {
        ...s.messages,
        [convoId]: (s.messages[convoId] || []).map(m =>
          m.id === msgId
            ? { ...m, isDeleted: true, deleted: true, content: 'This message was deleted' }
            : m
        ),
      },
    })),

  markRead: (convoId) =>
    set(s => ({
      conversations: s.conversations.map(c =>
        c.id === convoId ? { ...c, unreadCounts: {} } : c
      ),
    })),

  // The other participant opened the chat → mark all MY sent messages READ
  // (blue ticks). The backend read-receipt event carries no messageId, so we
  // promote every message I sent in this conversation to READ.
  markMySentRead: (convoId, myUserId) =>
    set(s => ({
      messages: {
        ...s.messages,
        [convoId]: (s.messages[convoId] || []).map(m =>
          m.senderId === myUserId && m.status !== 'READ' ? { ...m, status: 'READ' } : m
        ),
      },
    })),

  setOnline: (userId, online) =>
    set(s => {
      const next = new Set(s.onlineUsers)
      online ? next.add(userId) : next.delete(userId)
      return { onlineUsers: next }
    }),

  setLastSeen: (userId, timestamp) =>
    set(s => ({ lastSeen: { ...s.lastSeen, [userId]: timestamp } })),

  setTyping: (convoId, userId, typing) =>
    set(s => {
      const current = s.typingUsers[convoId] || []
      const next = typing
        ? [...new Set([...current, userId])]
        : current.filter(u => u !== userId)
      return { typingUsers: { ...s.typingUsers, [convoId]: next } }
    }),

  blockUser: (userId) =>
    set(s => { const n = new Set(s.blockedUsers); n.add(userId); return { blockedUsers: n } }),
  unblockUser: (userId) =>
    set(s => { const n = new Set(s.blockedUsers); n.delete(userId); return { blockedUsers: n } }),

  setReadReceipt: (convoId, userId, messageId) =>
    set(s => ({
      readReceipts: {
        ...s.readReceipts,
        [convoId]: { ...(s.readReceipts[convoId] || {}), [userId]: messageId },
      },
    })),
}))

// ─── Contacts Store ────────────────────────────────────────────────────────────
export const useContactsStore = create((set) => ({
  contacts: [], pendingRequests: [], suggestions: [],
  setContacts:    (c) => set({ contacts: c }),
  setSuggestions: (s) => set({ suggestions: s }),
  addContact:     (c) => set(s => ({ contacts: [c, ...s.contacts.filter(x => x.id !== c.id)] })),
  removeContact:  (id) => set(s => ({ contacts: s.contacts.filter(c => c.id !== id) })),
  setPending:     (r) => set({ pendingRequests: r }),
  addPending:     (r) => set(s => ({ pendingRequests: [r, ...s.pendingRequests] })),
}))

// ─── Status Store ──────────────────────────────────────────────────────────────
export const useStatusStore = create((set) => ({
  statuses: [], myStatuses: [],
  setStatuses:   (s) => set({ statuses: s }),
  setMyStatuses: (s) => set({ myStatuses: s }),
  addStatus:     (s) => set(st => ({ myStatuses: [s, ...st.myStatuses] })),
  likeStatus: (id) =>
    set(s => ({
      statuses: s.statuses.map(st =>
        st.id === id
          ? { ...st, liked: !st.liked, likeCount: st.liked ? (st.likeCount || 1) - 1 : (st.likeCount || 0) + 1 }
          : st
      ),
    })),
}))

// ─── Wallet Store ──────────────────────────────────────────────────────────────
export const useWalletStore = create((set) => ({
  balance: 0, transactions: [], streakDays: 0,
  setBalance:      (b)  => set({ balance: b }),
  setTransactions: (tx) => set({ transactions: tx }),
  setStreak:       (d)  => set({ streakDays: d }),
  addTransaction:  (tx) => set(s => ({ transactions: [tx, ...s.transactions] })),
  addTokens:       (n)  => set(s => ({ balance: s.balance + n })),
}))

// ─── Feed Store ────────────────────────────────────────────────────────────────
export const useFeedStore = create((set) => ({
  posts: [], currentPostIndex: 0, rooms: [],
  setPosts: (p) => set({ posts: p }),
  setRooms: (r) => set({ rooms: r }),
  nextPost: () => set(s => ({ currentPostIndex: Math.min(s.currentPostIndex + 1, s.posts.length - 1) })),
  prevPost: () => set(s => ({ currentPostIndex: Math.max(s.currentPostIndex - 1, 0) })),
  likePost: (id) =>
    set(s => ({
      posts: s.posts.map(p =>
        p.id === id ? { ...p, liked: !p.liked, likes: p.liked ? p.likes - 1 : p.likes + 1 } : p
      ),
    })),
}))
