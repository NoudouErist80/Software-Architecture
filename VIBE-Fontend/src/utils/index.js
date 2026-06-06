import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'
import { formatDistanceToNow, format, isToday, isYesterday } from 'date-fns'

export function cn(...inputs) { return twMerge(clsx(inputs)) }

export function formatMessageTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (isToday(date))     return format(date, 'HH:mm')
  if (isYesterday(date)) return 'Yesterday'
  return format(date, 'dd/MM/yyyy')
}

export function timeAgo(dateStr) {
  if (!dateStr) return ''
  return formatDistanceToNow(new Date(dateStr), { addSuffix: true })
}

/**
 * Format a last-seen timestamp as WhatsApp / Telegram does:
 *  - Within last minute  → "just now"
 *  - Within last hour    → "X minutes ago"
 *  - Today               → "today at HH:mm"
 *  - Yesterday           → "yesterday at HH:mm"
 *  - Older               → "dd/MM/yyyy at HH:mm"
 *
 * @param {string} isoTimestamp ISO-8601 string from the backend
 * @returns {string}
 */
export function formatLastSeen(isoTimestamp) {
  if (!isoTimestamp) return 'recently'
  const date = new Date(isoTimestamp)
  if (isNaN(date.getTime())) return 'recently'
  const diffMs  = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60_000)
  if (diffMin < 1)    return 'just now'
  if (diffMin < 60)   return `${diffMin} minute${diffMin === 1 ? '' : 's'} ago`
  if (isToday(date))     return `today at ${format(date, 'HH:mm')}`
  if (isYesterday(date)) return `yesterday at ${format(date, 'HH:mm')}`
  return `${format(date, 'dd/MM/yyyy')} at ${format(date, 'HH:mm')}`
}

/**
 * Returns up to two initials from a display name.
 * Gracefully handles null / undefined / empty strings.
 */
export function getInitials(name) {
  if (!name || typeof name !== 'string') return '?'
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(n => n[0])
    .join('')
    .toUpperCase() || '?'
}

/**
 * Deterministically maps a string to one of eight brand colours.
 * Null-safe: falls back to the first palette colour if name is empty/null.
 */
export function stringToColor(str) {
  const PALETTE = [
    '#0077b6', '#0d94d4', '#38aee8', '#10b981',
    '#8b5cf6', '#f59e0b', '#ef4444', '#06b6d4',
  ]
  if (!str || typeof str !== 'string' || str.length === 0) return PALETTE[0]
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

export function formatTokens(amount) {
  if (!amount) return '0'
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1)}M`
  if (amount >= 1_000)     return `${(amount / 1_000).toFixed(1)}K`
  return amount.toLocaleString()
}

export function tokensToCFA(tokens, rate = 10) {
  return (tokens * rate).toLocaleString('fr-CM') + ' FCFA'
}

export function truncate(str, len = 60) {
  if (!str) return ''
  return str.length > len ? str.slice(0, len) + '…' : str
}

export function canEditOrDelete(createdAt) {
  if (!createdAt) return false
  const diff = Date.now() - new Date(createdAt).getTime()
  return diff < 15 * 60 * 1000 // 15 minutes
}

export function debounce(fn, delay) {
  let timer
  return (...args) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
}

/**
 * Derive a human-readable display name for a conversation.
 *
 * The backend for DIRECT conversations stores:
 *   - participantIds:      [userId1, userId2]
 *   - participantUsernames:[username1, username2]  (parallel array, same order)
 *   - participantPhones:   [phone1, phone2]         (parallel array, same order)
 *
 * WhatsApp-style logic:
 *   1. If the other participant is in the user's contacts store → use saved name
 *   2. Otherwise use participantUsernames (show @username)
 *   3. If username is missing/is the userId → show phone number (unknown contact)
 *   4. Fallback to "Chat"
 *
 * @param {object}  conversation    Conversation object from the store
 * @param {string}  currentUserId   The logged-in user's UUID
 * @param {Array}   contacts        (optional) contacts array from the contacts store
 * @returns {string}
 */
export function getConversationDisplayName(conversation, currentUserId, contacts = []) {
  if (!conversation) return 'Chat'

  // Groups always have an explicit name set by the creator
  if (conversation.type === 'GROUP' || conversation.isGroup) {
    return conversation.name || 'Group Chat'
  }
  if (conversation.type === 'COMMUNITY') {
    return conversation.name || 'Community'
  }

  // Direct: use an explicit name if set (rare — backend doesn't set it for DIRECT)
  if (conversation.name) return conversation.name

  // ── Find the other participant ────────────────────────────────────────────

  const participantIds       = conversation.participantIds       || []
  const participantUsernames = conversation.participantUsernames || []
  const participantPhones    = conversation.participantPhones    || []

  // Index of the OTHER participant in the parallel arrays
  const otherIndex = participantIds.findIndex(id => id !== currentUserId)
  const otherId    = otherIndex >= 0 ? participantIds[otherIndex] : null

  // ── 1. Check contacts store for a saved display name ─────────────────────
  if (otherId && contacts.length > 0) {
    const contact = contacts.find(c =>
      (c.id && c.id === otherId) ||
      (c.contactId && c.contactId === otherId)
    )
    if (contact) {
      const saved = contact.displayName || contact.fullName
      if (saved) return saved
    }
  }

  // ── 2. Use participantUsernames from the Conversation entity ─────────────
  const otherUsername = otherIndex >= 0 ? participantUsernames[otherIndex] : null

  // A username that equals a UUID (legacy / fallback value) is not useful —
  // treat it the same as missing and fall through to phone.
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  const usernameIsUuid = otherUsername && UUID_RE.test(otherUsername)

  if (otherUsername && !usernameIsUuid) {
    // Usernames that look like phone numbers stay as-is; others get @-prefixed
    const looksLikePhone = /^\+?[\d\s\-()]{7,}$/.test(otherUsername)
    return looksLikePhone ? otherUsername : `@${otherUsername}`
  }

  // ── 3. WhatsApp-style: show phone number for unknown contacts ─────────────
  const otherPhone = otherIndex >= 0 ? participantPhones[otherIndex] : null
  if (otherPhone && otherPhone.trim()) return otherPhone.trim()

  // ── 4. Try the legacy participants array shape (some API responses) ───────
  const participants = conversation.participants || []
  const other = participants.find(
    p => p.id !== currentUserId && p.userId !== currentUserId
  )
  if (other) {
    return other.fullName || other.displayName ||
           (other.username ? `@${other.username}` : null) ||
           other.phoneNumber || 'Chat'
  }

  return 'Chat'
}

// ─── App constants ─────────────────────────────────────────────────────────────

export const SUPPORTED_LANGUAGES = [
  { code: 'en',  label: 'English',          flag: '🇬🇧' },
  { code: 'fr',  label: 'Français',          flag: '🇫🇷' },
  { code: 'pcm', label: 'Pidgin English',    flag: '🌍' },
  { code: 'ha',  label: 'Hausa',             flag: '🌍' },
  { code: 'tw',  label: 'Twi / Fante / Ga',  flag: '🇬🇭' },
  { code: 'bm',  label: 'Bamanankan',        flag: '🇲🇱' },
  { code: 'ln',  label: 'Lingala',           flag: '🇨🇩' },
  { code: 'ewo', label: 'Ewondo',            flag: '🇨🇲' },
  { code: 'yo',  label: 'Yorùbá',            flag: '🇳🇬' },
  { code: 'fat', label: 'Fante',             flag: '🇬🇭' },
  { code: 'gaa', label: 'Ga',                flag: '🇬🇭' },
]

export const STATUS_VISIBILITY = [
  { id: 'contacts',           label: 'My Contacts',              desc: 'Only people in your contacts',           icon: '👥' },
  { id: 'contacts_followers', label: 'Contacts & Followers',     desc: 'Contacts + people who follow you',       icon: '🔓' },
  { id: 'vibe_friends',       label: 'All VIBE Friends',         desc: 'Anyone on VIBE you are connected with',  icon: '🌐' },
  { id: 'except',             label: 'My Contacts Except…',      desc: 'Hide from specific contacts',            icon: '🚫' },
]

export const TOKEN_RATES = [
  { vbt: 100,  cfa: '1,000'  },
  { vbt: 500,  cfa: '5,000'  },
  { vbt: 1000, cfa: '10,000' },
  { vbt: 5000, cfa: '50,000' },
]

export const EARN_METHODS = [
  { emoji: '👀',  label: 'Watch Videos',    tokens: 2,   desc: 'Per video watched' },
  { emoji: '❤️', label: 'Like Content',    tokens: 1,   desc: 'Per reaction' },
  { emoji: '📤',  label: 'Post Content',    tokens: 10,  desc: 'Per post published' },
  { emoji: '🎙️', label: 'Host a Room',    tokens: 20,  desc: 'Per room hosted' },
  { emoji: '🗓️', label: 'Daily Streak',   tokens: 50,  desc: 'Log in every day' },
  { emoji: '👥',  label: 'Invite Friends', tokens: 100, desc: 'Per friend who joins' },
  { emoji: '💬',  label: 'Send Messages',  tokens: 1,   desc: 'Per 10 messages' },
  { emoji: '📸',  label: 'Post Status',    tokens: 5,   desc: 'Per status posted' },
]