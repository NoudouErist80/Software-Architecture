import React, { useState, useEffect, useRef, useCallback } from 'react'
import {
  Home, Bell, User, Plus, Search, MoreVertical, Phone, Video, Info,
  Smile, Paperclip, Send, Mic, MessageSquare, X, Zap, Bot, Globe,
  Check, CheckCheck, Image as ImageIcon, ArrowLeft, Edit2, Trash2,
  UserX, UserCheck, Hash, ChevronDown, Calendar, Clock, Users,
  Lock, Eye, EyeOff, Heart, Reply, Copy, Shield, AlertCircle,
  Camera, Coins, Settings, LogOut, BadgeCheck, Radio, UserPlus,
  Play, Pause, RefreshCw, AlertTriangle, ChevronRight
} from 'lucide-react'
import { useAuthStore, useUIStore, useMessagingStore, useStatusStore, useContactsStore, useWalletStore } from '../../store'
import { messagingAPI, statusAPI, contactsAPI, aiAPI, mediaAPI, notificationAPI, authAPI } from '../../services/api'
import { wsService } from '../../services/websocket'
import Avatar from '../shared/Avatar'
import MessageBubble from './MessageBubble'
import { cn, formatMessageTime, timeAgo, truncate, SUPPORTED_LANGUAGES, canEditOrDelete, formatTokens, getConversationDisplayName, formatLastSeen } from '../../utils'
import { isToday, isYesterday } from 'date-fns'

// ─── Emoji Data ────────────────────────────────────────────────────────────────
const EMOJI_CATEGORIES = [
  {
    id: 'smileys', label: '😊', title: 'Smileys & People',
    emojis: ['😀','😁','😂','🤣','😃','😄','😅','😆','😉','😊','😋','😎','😍','😘','🥰','😗','😙','😚',
              '🙂','🤗','🤩','🤔','🤨','😐','😑','😶','🙄','😏','😣','😥','😮','🤐','😯','😪','😫','😴',
              '😌','😛','😜','😝','🤤','😒','😓','😔','😕','🙃','🤑','😲','☹️','🙁','😖','😞','😟','😤',
              '😢','😭','😦','😧','😨','😩','🤯','😬','😰','😱','🥵','🥶','😳','🤪','😵','😡','😠','🤬',
              '😷','🤒','🤕','🤢','🤮','🤧','😇','🥳','🥺','🤠','🤡','🤥','🤫','🤭','🧐','🤓','😈','👿'],
  },
  {
    id: 'gestures', label: '👋', title: 'Gestures',
    emojis: ['👋','🤚','🖐','✋','🖖','👌','🤌','🤏','✌️','🤞','🤟','🤘','🤙','👈','👉','👆','🖕','👇',
              '☝️','👍','👎','✊','👊','🤛','🤜','👏','🙌','👐','🤲','🤝','🙏','✍️','💅','🤳','💪','🦾',
              '🦿','🦵','🦶','👂','🦻','👃','🫀','🫁','🧠','🦷','🦴','👀','👁','👅','👄'],
  },
  {
    id: 'animals', label: '🐶', title: 'Animals & Nature',
    emojis: ['🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🐔','🐧','🐦',
              '🦆','🦅','🦉','🦇','🐺','🐗','🐴','🦄','🐝','🐛','🦋','🐌','🐞','🐜','🦟','🦗','🕷','🦂',
              '🐢','🐍','🦎','🦖','🦕','🐙','🦑','🦐','🦞','🦀','🐡','🐠','🐟','🐬','🐳','🐋','🦈','🐊',
              '🐅','🐆','🦓','🦍','🦧','🦣','🐘','🦛','🦏','🐪','🦒','🦘','🦬','🐃','🐂','🐄','🐎','🐖'],
  },
  {
    id: 'food', label: '🍎', title: 'Food & Drink',
    emojis: ['🍎','🍊','🍋','🍇','🍓','🍒','🍑','🥭','🍍','🥝','🍅','🍆','🥑','🥦','🧄','🧅','🌽','🌶',
              '🥕','🥔','🍠','🧆','🥚','🍳','🥘','🍲','🥗','🥙','🌮','🌯','🥪','🥫','🍱','🍘','🍙','🍚',
              '🍛','🍜','🍝','🍞','🥐','🥖','🥨','🧀','🍖','🍗','🥩','🥓','🍔','🍟','🌭','🍕','🫔','🍣',
              '🍤','🍙','🍦','🍧','🍨','🍩','🍪','🎂','🍰','🧁','🥧','🍫','🍬','🍭','🍮','🍯','☕','🍵'],
  },
  {
    id: 'activity', label: '⚽', title: 'Activities',
    emojis: ['⚽','🏀','🏈','⚾','🥎','🎾','🏐','🏉','🥏','🎱','🏓','🏸','🏒','🏑','🥍','🏏','🪃','🥅',
              '⛳','🪁','🏹','🎣','🤿','🥊','🥋','🎽','🛹','🛷','⛸','🥌','🎿','⛷','🏂','🪂','🏋️','🤼',
              '🤸','🤺','🏇','⛹️','🤾','🏌️','🏄','🚣','🧘','🎖','🏆','🥇','🥈','🥉','⚽','🎮','🕹','🎲'],
  },
  {
    id: 'travel', label: '✈️', title: 'Travel & Places',
    emojis: ['🚗','🚕','🚙','🚌','🚎','🏎','🚓','🚑','🚒','🚐','🛻','🚚','🚛','🚜','🏍','🛵','🛺','🚲',
              '🛴','🛹','🛼','🚏','🛣','🛤','⛽','🛞','🚦','🚥','🗺','🗾','🏔','⛰','🌋','🗻','🏕','🏖',
              '🏜','🏝','🏞','🏟','🏛','🏗','🏘','🏚','🏠','🏡','🏢','🏣','🏤','🏥','🏦','🏨','🏩','🏪',
              '✈️','🛫','🛬','🛩','💺','🚀','🛸','🚁','🛶','⛵','🚤','🛥','🛳','⛴','🚢','🗼','🗽','⛪'],
  },
  {
    id: 'objects', label: '💡', title: 'Objects',
    emojis: ['⌚','📱','💻','⌨️','🖥','🖨','🖱','🖲','💾','💿','📀','📷','📸','📹','🎥','📽','🎞','📞',
              '☎️','📟','📠','📺','📻','🧭','⏱','⏲','⏰','🕰','⌛','⏳','📡','🔋','🔌','💡','🔦','🕯',
              '💸','💵','💴','💶','💷','💰','💳','🪙','💎','⚖️','🪜','🧰','🪛','🔧','🔨','⚒','🛠','⛏',
              '🔩','🗜','🔗','⛓','🧲','🔑','🗝','🔐','🔏','🔓','🔒','🚪','🪑','🛋','🛏','🛁','🪠','🧴'],
  },
  {
    id: 'symbols', label: '❤️', title: 'Symbols',
    emojis: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❣️','💕','💞','💓','💗','💖','💘','💝',
              '💟','☮️','✝️','☪️','🕉','☸️','✡️','🔯','🕎','☯️','☦️','🛐','⛎','♈','♉','♊','♋','♌','♍',
              '♎','♏','♐','♑','♒','♓','🆔','⚛️','🉑','☢️','☣️','📴','📳','🈶','🈚','🈸','🈺','🈷️','✴️',
              '🆚','💮','🉐','㊙️','㊗️','🈴','🈵','🈹','🈲','🅰️','🅱️','🆎','🆑','🆘','❌','⭕','🛑','⛔'],
  },
  {
    id: 'flags', label: '🌍', title: 'African & World Flags',
    emojis: ['🇨🇲','🇳🇬','🇬🇭','🇰🇪','🇿🇦','🇪🇹','🇸🇳','🇨🇮','🇹🇿','🇺🇬','🇲🇦','🇩🇿','🇹🇳','🇪🇬','🇱🇾',
              '🇸🇩','🇲🇱','🇧🇫','🇳🇪','🇹🇩','🇨🇫','🇨🇬','🇨🇩','🇬🇦','🇬🇶','🇦🇴','🇿🇲','🇿🇼','🇧🇯','🇹🇬',
              '🇸🇱','🇬🇳','🇬🇳','🇱🇷','🇲🇿','🇧🇮','🇷🇼','🇸🇴','🇩🇯','🇪🇷','🇫🇷','🇬🇧','🇺🇸','🇧🇷','🇨🇳',
              '🇯🇵','🇮🇳','🇷🇺','🇩🇪','🌍','🌎','🌏','🏳️','🏴','🏁','🚩','🏳️‍🌈','🏳️‍⚧️'],
  },
]

// ─── Emoji Picker Component ────────────────────────────────────────────────────
function EmojiPicker({ onSelect, onClose }) {
  const [activeCat, setActiveCat] = useState(EMOJI_CATEGORIES[0].id)
  const [search, setSearch]       = useState('')
  const pickerRef = useRef()

  useEffect(() => {
    const h = (e) => { if (pickerRef.current && !pickerRef.current.contains(e.target)) onClose() }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [onClose])

  const allEmojis = EMOJI_CATEGORIES.flatMap(c => c.emojis)
  const displayEmojis = search.trim()
    ? allEmojis.filter(e => e.includes(search))
    : EMOJI_CATEGORIES.find(c => c.id === activeCat)?.emojis || []

  return (
    <div
      ref={pickerRef}
      className="absolute bottom-full mb-2 left-0 bg-white border border-surface-border rounded-2xl shadow-2xl z-50 animate-fade-in w-[340px] overflow-hidden"
    >
      {/* Search */}
      <div className="p-2 border-b border-surface-border">
        <input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search emoji…"
          className="w-full text-sm bg-sky-50 border border-surface-border rounded-xl px-3 py-2 focus:outline-none focus:border-vibe-400 placeholder:text-gray-400"
          autoFocus
        />
      </div>

      {/* Category tabs */}
      {!search && (
        <div className="flex overflow-x-auto scrollbar-none border-b border-surface-border bg-gray-50">
          {EMOJI_CATEGORIES.map(cat => (
            <button
              key={cat.id}
              onClick={() => setActiveCat(cat.id)}
              title={cat.title}
              className={cn(
                'flex-shrink-0 w-9 h-9 flex items-center justify-center text-base transition-colors',
                activeCat === cat.id ? 'bg-vibe-50 border-b-2 border-vibe-500' : 'hover:bg-gray-100'
              )}
            >
              {cat.label}
            </button>
          ))}
        </div>
      )}

      {/* Category title */}
      {!search && (
        <p className="px-3 pt-2 pb-1 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
          {EMOJI_CATEGORIES.find(c => c.id === activeCat)?.title}
        </p>
      )}

      {/* Emoji grid */}
      <div className="grid grid-cols-8 gap-0.5 px-2 py-1 h-52 overflow-y-auto scrollbar-none">
        {displayEmojis.map((emoji, i) => (
          <button
            key={`${emoji}-${i}`}
            onClick={() => onSelect(emoji)}
            className="w-9 h-9 flex items-center justify-center text-xl hover:bg-sky-50 rounded-lg transition-colors active:scale-90"
          >
            {emoji}
          </button>
        ))}
        {displayEmojis.length === 0 && (
          <div className="col-span-8 flex items-center justify-center py-8 text-gray-400 text-sm">
            No emoji found
          </div>
        )}
      </div>

      {/* Quick reactions row */}
      <div className="flex items-center gap-1 px-3 py-2 border-t border-surface-border bg-gray-50">
        <p className="text-[10px] text-gray-400 mr-1">Quick:</p>
        {['❤️','😂','👍','😮','😢','🔥','🙏','🎉'].map(e => (
          <button key={e} onClick={() => onSelect(e)}
            className="w-7 h-7 flex items-center justify-center text-base hover:bg-white rounded-lg transition-colors">
            {e}
          </button>
        ))}
      </div>
    </div>
  )
}

// ─── Add Contact Modal (WhatsApp-style: name-first, then verify) ──────────────
/**
 * Flow:
 *  1. User fills: Display Name (how they want to save contact) + Phone/Username
 *  2. Click "Save Contact" → we first save locally and check if user exists on VIBE
 *  3. If user exists → show "VIBE Contact ✓" badge and offer to start chat
 *  4. If not found  → show friendly "Not on VIBE" advisory (WhatsApp-style amber card)
 *                     BUT contact is still saved in the user's address book with a non-VIBE flag
 */
/**
 * AddContactModal — WhatsApp-style contact addition with real-time auto-suggest.
 *
 * Flow:
 *  1. User starts typing a phone number or username
 *  2. After 2+ characters, search fires automatically (debounced 400ms)
 *  3. Matching VIBE users appear as suggestions in real-time (like WhatsApp)
 *  4. User sees suggested name from VIBE but can freely edit the display name
 *  5. "Save Contact" persists to VIBE backend with their chosen display name
 *  6. Option to "Start Chat" immediately after saving
 */
function AddContactModal({ onClose }) {
  const addToast       = useUIStore(s => s.addToast)
  const { addContact } = useContactsStore()
  const { addNewConversation, selectConvo } = useMessagingStore(s => ({
    addNewConversation: s.addNewConversation,
    selectConvo: s.selectConvo,
  }))
  const { user } = useAuthStore()

  // ── Form state ──────────────────────────────────────────────────────────────
  const [tab,         setTab]         = useState('phone')    // 'phone' | 'username'
  const [query,       setQuery]       = useState('')         // phone or username being typed
  const [displayName, setDisplayName] = useState('')         // how user wants to save this contact
  const [step,        setStep]        = useState('form')     // 'form' | 'saving' | 'saved'

  // ── Auto-suggest state ──────────────────────────────────────────────────────
  const [suggestions,    setSuggestions]    = useState([])    // search results list
  const [searching,      setSearching]      = useState(false) // spinner while searching
  const [selectedUser,   setSelectedUser]   = useState(null)  // user picked from suggestions
  const [startingChat,   setStartingChat]   = useState(false)

  const searchTimer = useRef(null)
  const nameInputRef = useRef(null)

  // ── Auto-search as user types ───────────────────────────────────────────────
  useEffect(() => {
    const val = query.trim()

    // Clear suggestions if query is too short
    if (val.length < 2) {
      setSuggestions([])
      setSearching(false)
      if (selectedUser) setSelectedUser(null)
      return
    }

    // Debounce: wait 400ms after the user stops typing before searching
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(async () => {
      setSearching(true)
      try {
        const res = tab === 'phone'
          ? await contactsAPI.searchByPhone(val)
          : await contactsAPI.searchByUsername(val)

        const list = res.data?.data
        const results = Array.isArray(list) ? list : (list ? [list] : [])

        // Backend already excludes the searching user, but add a client-side
        // guard too for defence in depth.
        const filtered = results.filter(u => u.id !== user?.id)
        setSuggestions(filtered)
      } catch {
        setSuggestions([])
      } finally {
        setSearching(false)
      }
    }, 400)

    return () => clearTimeout(searchTimer.current)
  }, [query, tab])

  // When tab changes, reset everything
  const switchTab = (newTab) => {
    setTab(newTab)
    setQuery('')
    setSuggestions([])
    setSelectedUser(null)
    setSearching(false)
  }

  // User taps a suggestion — pre-fill display name with VIBE name (editable)
  const pickSuggestion = (vibeUser) => {
    setSelectedUser(vibeUser)
    setSuggestions([])
    // Pre-fill display name with the VIBE username/fullName (user can change it)
    if (!displayName.trim()) {
      setDisplayName(vibeUser.fullName || vibeUser.username || '')
    }
    // Focus the name input so user can edit right away
    setTimeout(() => nameInputRef.current?.focus(), 50)
  }

  const reset = () => {
    setQuery(''); setDisplayName(''); setSelectedUser(null)
    setSuggestions([]); setStep('form')
  }

  // ── Save contact ─────────────────────────────────────────────────────────────
  const handleSave = async () => {
    const nameVal = displayName.trim()
    if (!nameVal) { addToast('Enter a name to save this contact as', 'error'); return }
    if (!selectedUser?.id) { addToast('Please select a contact from the suggestions', 'error'); return }

    // Client-side self-add guard
    if (selectedUser.id === user?.id) {
      addToast('You can\'t add yourself as a contact', 'error')
      return
    }

    setStep('saving')
    try {
      // Pass displayName so backend stores it and returns it in the response
      await contactsAPI.addContact(selectedUser.id, nameVal)
      addContact({ ...selectedUser, displayName: nameVal, fullName: nameVal })
      setStep('saved')
      addToast(`${nameVal} added to your VIBE contacts 🎉`, 'success')
    } catch (err) {
      const msg = err.response?.data?.message || 'Could not add contact. Try again.'
      addToast(msg, 'error')
      setStep('form')
    }
  }

  // ── Start chat after saving ──────────────────────────────────────────────────
  const handleStartChat = async () => {
    if (!selectedUser?.id) return
    setStartingChat(true)
    try {
      const res = await messagingAPI.createDirect(
        selectedUser.id,
        selectedUser.username || selectedUser.fullName,
        selectedUser.phoneNumber || ''
      )
      const convo = res.data?.data || res.data
      if (convo?.id) {
        addNewConversation(convo)
        selectConvo(convo)
        addToast(`Chat with ${displayName || selectedUser.fullName} opened!`, 'success')
      }
      onClose()
    } catch (err) {
      // Conversation may already exist
      try {
        const listRes = await messagingAPI.getConversations()
        const convos = listRes.data?.data || listRes.data?.data?.content || []
        const existing = convos.find(c =>
          c.type === 'DIRECT' && (
            c.participantIds?.includes(selectedUser.id) ||
            c.participants?.some(p => p.id === selectedUser.id)
          )
        )
        if (existing) {
          addNewConversation(existing)
          selectConvo(existing)
          onClose()
          return
        }
      } catch (_) {}
      addToast('Could not open chat. Try again.', 'error')
    } finally {
      setStartingChat(false)
    }
  }

  const canSave = selectedUser && displayName.trim().length > 0 && step !== 'saving'

  return (
    <div
      className="fixed inset-0 z-[200] bg-black/50 backdrop-blur-sm flex items-center justify-center"
      onClick={e => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-white rounded-3xl w-full max-w-sm mx-4 shadow-2xl overflow-hidden animate-fade-in">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
          <div className="flex items-center gap-2">
            <UserPlus className="w-5 h-5 text-vibe-600" />
            <h2 className="font-display font-bold text-gray-900">Add Contact</h2>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="w-5 h-5" /></button>
        </div>

        <div className="p-5 space-y-4">

          {/* ── STEP: form ── */}
          {step !== 'saved' && (
            <>
              {/* Tab: Phone / Username */}
              <div>
                <label className="text-xs font-semibold text-gray-500 mb-1.5 block">Find contact by</label>
                <div className="flex bg-gray-100 rounded-xl p-1 mb-3">
                  {[{ id: 'phone', label: '📱 Phone' }, { id: 'username', label: '🔍 Username' }].map(t => (
                    <button
                      key={t.id}
                      onClick={() => switchTab(t.id)}
                      className={cn(
                        'flex-1 py-1.5 text-xs font-semibold rounded-lg transition-all',
                        tab === t.id ? 'bg-white shadow text-vibe-700' : 'text-gray-500'
                      )}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>

                {/* Search input with live suggestions */}
                <div className="relative">
                  {tab === 'phone'
                    ? <input
                        value={query}
                        onChange={e => { setQuery(e.target.value); setSelectedUser(null) }}
                        placeholder="+237 6XX XXX XXX"
                        type="tel"
                        className="vibe-input pr-8"
                        autoFocus
                      />
                    : <input
                        value={query}
                        onChange={e => { setQuery(e.target.value); setSelectedUser(null) }}
                        placeholder="@username or display name"
                        type="text"
                        className="vibe-input pr-8"
                        autoFocus
                      />
                  }
                  {/* Spinner */}
                  {searching && (
                    <span className="absolute right-3 top-1/2 -translate-y-1/2">
                      <RefreshCw className="w-3.5 h-3.5 text-vibe-400 animate-spin" />
                    </span>
                  )}
                  {/* Selected indicator */}
                  {selectedUser && !searching && (
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-500">
                      <BadgeCheck className="w-4 h-4" />
                    </span>
                  )}
                </div>

                {/* Auto-suggest dropdown */}
                {suggestions.length > 0 && !selectedUser && (
                  <div className="mt-1 bg-white border border-surface-border rounded-xl shadow-lg overflow-hidden z-10">
                    {suggestions.map(vibeUser => (
                      <button
                        key={vibeUser.id}
                        onClick={() => pickSuggestion(vibeUser)}
                        className="w-full flex items-center gap-3 px-4 py-3 hover:bg-sky-50 transition-colors text-left border-b border-surface-border last:border-0"
                      >
                        <Avatar name={vibeUser.fullName || vibeUser.username} src={vibeUser.profilePictureUrl} size="sm" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-gray-800 truncate">
                            {vibeUser.fullName || vibeUser.username}
                          </p>
                          <p className="text-[11px] text-gray-400 truncate">
                            @{vibeUser.username}
                            {vibeUser.phoneNumber ? ` · ${vibeUser.phoneNumber}` : ''}
                          </p>
                        </div>
                        <span className="text-[10px] bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-semibold flex-shrink-0">
                          VIBE ✓
                        </span>
                      </button>
                    ))}
                  </div>
                )}

                {/* No results hint */}
                {query.length >= 2 && !searching && suggestions.length === 0 && !selectedUser && (
                  <div className="mt-2 bg-amber-50 border border-amber-200 rounded-xl p-3 flex items-start gap-2">
                    <AlertTriangle className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
                    <p className="text-xs text-amber-800">
                      No VIBE account found for <strong>{query}</strong>.
                      Only users with a VIBE account can be added.
                    </p>
                  </div>
                )}
              </div>

              {/* Selected user card */}
              {selectedUser && (
                <div className="bg-green-50 border border-green-200 rounded-2xl p-3 flex items-center gap-3 animate-fade-in">
                  <Avatar name={selectedUser.fullName || selectedUser.username} src={selectedUser.profilePictureUrl} size="sm" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-800 truncate">{selectedUser.fullName || selectedUser.username}</p>
                    <p className="text-[11px] text-gray-400">@{selectedUser.username}</p>
                  </div>
                  <span className="text-[10px] bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-semibold flex-shrink-0 flex items-center gap-1">
                    <BadgeCheck className="w-3 h-3" /> VIBE
                  </span>
                </div>
              )}

              {/* Display Name — shown once a user is selected */}
              {selectedUser && (
                <div className="animate-fade-in">
                  <label className="text-xs font-semibold text-gray-500 mb-1.5 block">
                    Save as (your display name for this contact)
                  </label>
                  <input
                    ref={nameInputRef}
                    value={displayName}
                    onChange={e => setDisplayName(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && canSave && handleSave()}
                    placeholder={`e.g. ${selectedUser.fullName?.split(' ')[0] || 'Mama'}, Boss, Jean-Pierre…`}
                    className="vibe-input"
                  />
                  <p className="text-[11px] text-gray-400 mt-1">
                    This name only appears in <em>your</em> contacts — just like WhatsApp.
                  </p>
                </div>
              )}

              {/* Info hint (shown before user selects anyone) */}
              {!selectedUser && (
                <div className="bg-sky-50 border border-surface-border rounded-xl p-3 flex items-start gap-2">
                  <Shield className="w-4 h-4 text-vibe-400 flex-shrink-0 mt-0.5" />
                  <p className="text-xs text-gray-500">
                    Start typing a phone number or username. VIBE contacts appear instantly as you type.
                  </p>
                </div>
              )}

              <button
                onClick={handleSave}
                disabled={!canSave}
                className="btn-vibe w-full flex items-center justify-center gap-2 py-3 disabled:opacity-40"
              >
                {step === 'saving'
                  ? <><RefreshCw className="w-4 h-4 animate-spin" /> Saving…</>
                  : <><UserPlus className="w-4 h-4" /> Save Contact</>
                }
              </button>
            </>
          )}

          {/* ── STEP: saved ── */}
          {step === 'saved' && selectedUser && (
            <div className="animate-fade-in space-y-4">
              <div className="bg-green-50 border border-green-200 rounded-2xl p-4">
                <div className="flex items-center gap-3 mb-3">
                  <Avatar name={displayName || selectedUser.fullName} src={selectedUser.profilePictureUrl} size="lg" />
                  <div className="flex-1 min-w-0">
                    <p className="font-bold text-gray-900">{displayName}</p>
                    <p className="text-xs text-gray-500">@{selectedUser.username}</p>
                    <span className="inline-flex items-center gap-1 mt-1 text-[10px] bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-semibold">
                      <BadgeCheck className="w-3 h-3" /> Saved as VIBE Contact
                    </span>
                  </div>
                </div>
                <p className="text-xs text-green-700 text-center">
                  ✅ <strong>{displayName}</strong> is on VIBE and has been added to your contacts.
                </p>
              </div>
              <button
                onClick={handleStartChat}
                disabled={startingChat}
                className="btn-vibe w-full py-3 flex items-center justify-center gap-2 disabled:opacity-60"
              >
                {startingChat
                  ? <><RefreshCw className="w-4 h-4 animate-spin" /> Opening chat…</>
                  : <><MessageSquare className="w-4 h-4" /> Start Chat with {displayName}</>
                }
              </button>
              <button onClick={onClose} className="w-full py-2 text-sm text-vibe-600 hover:underline">Done 🎉</button>
              <button onClick={reset} className="w-full py-2 text-sm text-gray-400 hover:underline text-xs">Add another contact</button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}


// ─── Main export ───────────────────────────────────────────────────────────────
export default function VibeMessenger() {
  const { activePage, setPage } = useUIStore()
  return (
    <div className="flex h-full w-full bg-surface-card overflow-hidden">
      <LeftRail />
      <div className="flex-1 flex flex-col min-w-0">
        {activePage === 'home'          && <MessengerHome />}
        {activePage === 'notifications' && <NotificationsPage />}
        {activePage === 'profile'       && <ProfilePage />}
      </div>
    </div>
  )
}

function LeftRail() {
  const { activePage, setPage, openComposer } = useUIStore()
  const { user } = useAuthStore()
  const conversations = useMessagingStore(s => s.conversations)
  const totalUnread = conversations.reduce((a, c) => a + (c.unreadCount || 0), 0)
  const [notifCount, setNotifCount]   = useState(0)
  const [plusOpen, setPlusOpen]       = useState(false)
  const [addContactOpen, setAddContactOpen] = useState(false)
  const plusRef = useRef()

  useEffect(() => {
    notificationAPI.getUnreadCount().then(res => setNotifCount(res.data?.data?.count || 0)).catch(() => {})
  }, [])

  const groups = conversations.filter(c => c.type === 'GROUP').slice(0, 3)

  useEffect(() => {
    const h = (e) => { if (!plusRef.current?.contains(e.target)) setPlusOpen(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  return (
    <>
      <div className="w-16 flex flex-col items-center bg-white border-r border-surface-border py-3 gap-2 flex-shrink-0 shadow-sm">
        <div className="w-10 h-10 rounded-xl flex items-center justify-center cursor-pointer hover:scale-105 transition-transform mb-1" onClick={() => setPage('home')}>
          <img src="/vibe_appicon_512.png" alt="VIBE" className="w-10 h-10" onError={e => { e.target.style.display='none' }} />
        </div>
        <div className="w-8 h-px bg-surface-border" />
        {groups.map((g) => <RailGroupBtn key={g.id} group={g} />)}
        <div ref={plusRef} className="relative">
          <button onClick={() => setPlusOpen(o => !o)}
            className={cn('w-10 h-10 rounded-xl flex items-center justify-center transition-all duration-200 border',
              plusOpen ? 'bg-vibe-600 text-white border-vibe-600 rotate-45' : 'bg-sky-50 text-vibe-600 border-surface-border hover:bg-vibe-50')}>
            <Plus className="w-5 h-5 transition-transform duration-200" />
          </button>
          {plusOpen && (
            <div className="absolute left-12 top-0 z-50 bg-white border border-surface-border rounded-2xl shadow-xl py-1 w-52 animate-fade-in">
              {[
                { label: 'Add Contact',     emoji: '👤', action: () => setAddContactOpen(true) },
                { label: 'Story / Status',  emoji: '📸', action: () => openComposer('status') },
                { label: 'New Group',       emoji: '👥', action: () => openComposer('newGroup') },
                { label: 'Meet Friends',    emoji: '🤝', action: () => openComposer('meetFriends') },
                { label: 'VIBE for Public', emoji: '🎬', action: () => openComposer('vibePublic') },
              ].map(item => (
                <button key={item.label} onClick={() => { item.action(); setPlusOpen(false) }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-gray-600 hover:text-vibe-700 hover:bg-sky-50 transition-colors text-left">
                  <span>{item.emoji}</span>{item.label}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="flex-1" />
        {[
          { icon: Home,  page: 'home',          tip: 'Home' },
          { icon: Bell,  page: 'notifications', tip: 'Notifications', badge: notifCount || totalUnread },
          { icon: User,  page: 'profile',        tip: 'Profile' },
        ].map(({ icon: Icon, page, tip, badge }) => (
          <button key={page} onClick={() => setPage(page)} title={tip}
            className={cn('relative w-10 h-10 rounded-xl flex items-center justify-center transition-all duration-200',
              activePage === page ? 'bg-vibe-50 text-vibe-600' : 'text-gray-400 hover:text-vibe-600 hover:bg-sky-50')}>
            <Icon className="w-5 h-5" />
            {badge > 0 && <span className="notif-dot">{badge > 9 ? '9+' : badge}</span>}
          </button>
        ))}
        <Avatar name={user?.fullName} src={user?.profilePictureUrl} size="sm"
          className="cursor-pointer hover:scale-105 transition-transform mt-1 border-2 border-vibe-200"
          onClick={() => setPage('profile')} />
      </div>
      {addContactOpen && <AddContactModal onClose={() => setAddContactOpen(false)} />}
    </>
  )
}

function RailGroupBtn({ group }) {
  const selectConvo = useMessagingStore(s => s.selectConvo)
  const { setPage } = useUIStore()
  return (
    <button title={group.name} onClick={() => { selectConvo(group); setPage('home') }}
      className="w-10 h-10 rounded-xl bg-sky-50 hover:bg-vibe-50 border border-surface-border hover:border-vibe-300 transition-all flex items-center justify-center text-xs font-display font-bold text-vibe-600 relative">
      {group.name?.[0]?.toUpperCase() || '#'}
      {group.unreadCount > 0 && <span className="notif-dot">{group.unreadCount}</span>}
    </button>
  )
}

function MessengerHome() {
  const { selectedConversation } = useMessagingStore()
  return (
    <div className="flex h-full">
      <ConversationList />
      <div className="flex-1 flex flex-col min-w-0">
        {selectedConversation ? <ChatArea conversation={selectedConversation} /> : <ChatWelcome />}
      </div>
    </div>
  )
}

function ConversationList() {
  const { conversations, setConversations, selectConvo, selectedConversation, addNewConversation } = useMessagingStore(s => ({
    conversations: s.conversations, setConversations: s.setConversations,
    selectConvo: s.selectConvo, selectedConversation: s.selectedConversation,
    addNewConversation: s.addNewConversation,
  }))
  const { contacts, setContacts } = useContactsStore(s => ({
    contacts: s.contacts, setContacts: s.setContacts,
  }))
  const { user } = useAuthStore()
  const [search, setSearch]           = useState('')
  const [filter, setFilter]           = useState('all')
  const [addContactOpen, setAddContactOpen] = useState(false)
  const [startingChatId, setStartingChatId] = useState(null)
  const addToast = useUIStore(s => s.addToast)

  // ── Load conversations on mount ──────────────────────────────────────────
  useEffect(() => {
    messagingAPI.getConversations().then(res => {
      const data = res.data?.data || res.data?.data?.content || []
      if (Array.isArray(data)) setConversations(data)
    }).catch(() => {})
  }, [])

  // ── Load contacts so they show in sidebar when no chats exist yet ─────────
  useEffect(() => {
    contactsAPI.getMyContacts().then(res => {
      const data = res.data?.data || res.data?.data?.content || []
      if (Array.isArray(data) && data.length > 0) setContacts(data)
    }).catch(() => {})
  }, [])

  /**
   * CRITICAL FIX — Global conversation subscriptions
   * ────────────────────────────────────────────────────────────────────────
   * Previously this only subscribed to /user/queue/conversations which the
   * backend never actually pushes to (it pushes to /topic/conversation/{id}).
   *
   * Now we subscribe to the /topic/conversation/{id} topic for EVERY
   * conversation the user is in.  This means:
   *   ✅ New messages are received even when the conversation is not open
   *   ✅ Unread badge increments on the conversation list item
   *   ✅ Last-message preview updates in real time
   *   ✅ Conversation is bumped to the top of the list
   *
   * This is how WhatsApp Web / Telegram Web work — they subscribe to all
   * conversation topics on load, not just the open one.
   *
   * The wsService.subscribeToConversationList() method in websocket.js
   * registers "list-updater" listeners that co-exist with the "chat-area"
   * listener when a conversation is open.
   */
  useEffect(() => {
    if (conversations.length === 0) return

    const ids = conversations.map(c => c.id).filter(Boolean)

    // Handler for any new message on any conversation
    const handleListUpdate = (convoId, payload) => {
      const msgType = payload?.type
      // Only process new message events for the list updater
      if (msgType && msgType !== 'NEW_MESSAGE') return

      const envelope = payload?.message || payload
      if (!envelope?.senderId && !envelope?.id) return

      // Don't bump/increment for our own messages (already handled optimistically)
      if (envelope.senderId === user?.id) return

      setConversations(prev => {
        const idx = prev.findIndex(c => c.id === convoId)
        if (idx < 0) return prev
        const existing = prev[idx]
        const isOpen = selectedConversation?.id === convoId
        const updated = {
          ...existing,
          lastMessagePreview: envelope.content || existing.lastMessagePreview,
          lastMessageAt: envelope.createdAt || new Date().toISOString(),
          // Only increment unread when this conversation is NOT the open one
          unreadCounts: isOpen
            ? existing.unreadCounts
            : {
                ...(existing.unreadCounts || {}),
                [user?.id]: ((existing.unreadCounts || {})[user?.id] || 0) + 1,
              },
        }
        return [updated, ...prev.filter((_, i) => i !== idx)]
      })
    }

    wsService.subscribeToConversationList(ids, handleListUpdate)

    // Cleanup: remove the 'list-updater' listener from all those conversations
    return () => {
      ids.forEach(id => wsService.removeConversationListener(id, 'list-updater'))
    }
  // Re-run when conversations list changes (new conversation added) or user changes
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversations.map(c => c.id).join(','), user?.id])

  /**
   * Start a direct conversation with a contact.
   * If one already exists we just select it; otherwise we create it.
   */
  const handleStartChatWithContact = async (contact) => {
    const contactId = contact.id || contact.contactId
    if (!contactId || startingChatId) return

    // Fast path: conversation already exists
    // Check participantIds (backend shape) OR legacy participants array
    const existing = conversations.find(c =>
      c.type === 'DIRECT' && (
        c.participantIds?.includes(contactId) ||
        c.participants?.some(p => p.id === contactId || p.userId === contactId)
      )
    )
    if (existing) { selectConvo(existing); return }

    setStartingChatId(contactId)
    try {
      const contactUsername = contact.username || contact.fullName || ''
      const contactPhone    = contact.phoneNumber || ''
      // Pass phone so the backend can store it on the Conversation and enable
      // WhatsApp-style "unknown contact" display for the recipient
      const res = await messagingAPI.createDirect(contactId, contactUsername, contactPhone)
      const convo = res.data?.data || res.data
      if (convo?.id) {
        addNewConversation(convo)
        selectConvo(convo)
      }
    } catch (err) {
      // 409 Conflict means it already exists — re-fetch to find it
      if (err.response?.status === 409 || err.response?.status === 200) {
        try {
          const listRes = await messagingAPI.getConversations()
          const convos  = listRes.data?.data || listRes.data?.data?.content || []
          if (Array.isArray(convos)) setConversations(convos)
          const found = convos.find(c =>
            c.type === 'DIRECT' && (
              c.participantIds?.includes(contactId) ||
              c.participants?.some(p => p.id === contactId || p.userId === contactId)
            )
          )
          if (found) selectConvo(found)
        } catch (_) {}
      } else {
        addToast('Could not open chat. Is the messaging service running?', 'error')
      }
    } finally {
      setStartingChatId(null)
    }
  }

  const filtered = conversations.filter(c => {
    // Resolve the display name the SAME way the row does (ConvoItem), so direct
    // chats — which have a null backend `name` — are matched by the other
    // participant's username/phone, NOT just saved contacts. Previously this
    // used `c.name` directly, so every direct conversation with an unsaved
    // sender was filtered out of the list entirely ("messages from non-contacts
    // don't show up"). Empty search now also shows everything.
    const display = (getConversationDisplayName(c, user?.id, contacts) || c.name || '').toLowerCase()
    const m = !search || display.includes(search.toLowerCase())
    if (filter === 'groups') return m && c.type === 'GROUP'
    if (filter === 'direct') return m && c.type === 'DIRECT'
    return m
  })

  // Contacts that don't already have a conversation (to show as "tap to start chat")
  const contactsWithoutChat = contacts.filter(contact => {
    const cid = contact.id || contact.contactId
    return !conversations.some(c =>
      c.type === 'DIRECT' && (
        c.participantIds?.includes(cid) ||
        c.participants?.some(p => p.id === cid || p.userId === cid)
      )
    )
  })

  // Tabs: all | direct | groups | contacts
  const tabs = ['all', 'direct', 'groups', 'contacts']

  return (
    <>
      <div className="w-72 xl:w-80 flex flex-col border-r border-surface-border bg-white flex-shrink-0">
        <div className="p-4 border-b border-surface-border">
          <div className="flex items-center justify-between mb-3">
            <h1 className="font-display font-bold text-gray-900 text-lg">Messages</h1>
            <div className="flex gap-1">
              <button className="p-1.5 rounded-lg text-gray-400 hover:text-vibe-600 hover:bg-sky-50 transition-all"><Search className="w-4 h-4" /></button>
              <button onClick={() => setAddContactOpen(true)} title="Add Contact"
                className="p-1.5 rounded-lg text-gray-400 hover:text-vibe-600 hover:bg-sky-50 transition-all">
                <Plus className="w-4 h-4" />
              </button>
            </div>
          </div>
          <div className="relative mb-3">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search conversations…"
              className="w-full bg-sky-50 border border-surface-border rounded-xl pl-9 pr-4 py-2 text-sm text-gray-700 placeholder:text-gray-400 focus:outline-none focus:border-vibe-400 transition-colors" />
          </div>
          <div className="flex gap-1">
            {tabs.map(f => (
              <button key={f} onClick={() => setFilter(f)}
                className={cn('flex-1 text-[11px] py-1.5 rounded-lg capitalize font-semibold transition-all',
                  filter === f ? 'bg-vibe-600 text-white' : 'text-gray-500 hover:text-gray-700 hover:bg-sky-50')}>
                {f === 'contacts' ? '👥' : f}
              </button>
            ))}
          </div>
        </div>
        <StatusStrip />
        <div className="flex-1 overflow-y-auto scrollbar-none py-2">

          {/* ── CONTACTS TAB — WhatsApp/Telegram-style contact directory ── */}
          {filter === 'contacts' && (
            <>
              {contacts.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-gray-400 px-4">
                  <UserPlus className="w-10 h-10 mb-3 opacity-20" />
                  <p className="text-sm font-medium text-gray-500">No contacts yet</p>
                  <p className="text-xs text-gray-400 text-center mt-1">Add your first VIBE contact!</p>
                  <button onClick={() => setAddContactOpen(true)} className="mt-4 btn-vibe text-xs px-4 py-2 flex items-center gap-2">
                    <UserPlus className="w-3.5 h-3.5" /> Add Contact
                  </button>
                </div>
              ) : (
                <div className="px-2">
                  <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2 px-2 pt-1">
                    {contacts.length} Contact{contacts.length !== 1 ? 's' : ''}
                  </p>
                  {contacts
                    .filter(c => {
                      const name = (c.displayName || c.fullName || c.username || '').toLowerCase()
                      return !search || name.includes(search.toLowerCase())
                    })
                    .map(contact => {
                      const cid = contact.id || contact.contactId
                      const name = contact.displayName || contact.fullName || contact.username || 'Contact'
                      const isStarting = startingChatId === cid
                      // Check if a conversation already exists with this contact
                      const existingConvo = conversations.find(c =>
                        c.type === 'DIRECT' && (
                          c.participantIds?.includes(cid) ||
                          c.participants?.some(p => p.id === cid || p.userId === cid)
                        )
                      )
                      return (
                        <button
                          key={cid}
                          onClick={() => existingConvo ? selectConvo(existingConvo) : handleStartChatWithContact(contact)}
                          disabled={!!startingChatId}
                          className={cn(
                            'w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all text-left mb-0.5',
                            isStarting ? 'bg-sky-50 opacity-70' : 'hover:bg-sky-50 active:bg-sky-100'
                          )}
                        >
                          <div className="relative flex-shrink-0">
                            <Avatar name={name} src={contact.profilePictureUrl} size="sm" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-gray-800 truncate">{name}</p>
                            <p className="text-[11px] text-gray-400 truncate">
                              {existingConvo ? '💬 Tap to open chat' : (contact.phoneNumber || `@${contact.username || 'vibe'}`)}
                            </p>
                          </div>
                          {isStarting
                            ? <RefreshCw className="w-3.5 h-3.5 text-vibe-400 animate-spin flex-shrink-0" />
                            : existingConvo
                              ? <MessageSquare className="w-3.5 h-3.5 text-vibe-400 flex-shrink-0" />
                              : <Plus className="w-3.5 h-3.5 text-gray-300 flex-shrink-0" />
                          }
                        </button>
                      )
                    })
                  }
                  <button
                    onClick={() => setAddContactOpen(true)}
                    className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl hover:bg-sky-50 text-left mt-2 border border-dashed border-surface-border"
                  >
                    <div className="w-9 h-9 rounded-full bg-sky-50 flex items-center justify-center flex-shrink-0">
                      <UserPlus className="w-4 h-4 text-vibe-400" />
                    </div>
                    <p className="text-sm text-vibe-600 font-semibold">Add New Contact</p>
                  </button>
                </div>
              )}
            </>
          )}

          {/* ── MESSAGES TABS (all / direct / groups) ── */}
          {filter !== 'contacts' && (
            <>
              {filtered.map(c => (
                <ConvoItem key={c.id} convo={c} active={selectedConversation?.id === c.id} onClick={() => selectConvo(c)} />
              ))}

              {/* Below conversations: show contacts that don't have a chat yet (like Telegram) */}
              {filter === 'all' && contactsWithoutChat.length > 0 && (
                <div className="px-3 mt-3">
                  <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5 px-1">Contacts — tap to start chat</p>
                  {contactsWithoutChat.map(contact => {
                    const cid = contact.id || contact.contactId
                    const name = contact.displayName || contact.fullName || contact.username || 'Contact'
                    const isStarting = startingChatId === cid
                    return (
                      <button
                        key={cid}
                        onClick={() => handleStartChatWithContact(contact)}
                        disabled={!!startingChatId}
                        className={cn(
                          'w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all text-left mb-0.5',
                          isStarting ? 'bg-sky-50 opacity-70' : 'hover:bg-sky-50 active:bg-sky-100'
                        )}
                      >
                        <Avatar name={name} src={contact.profilePictureUrl} size="sm" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-gray-800 truncate">{name}</p>
                          <p className="text-[11px] text-gray-400 truncate">{contact.phoneNumber || `@${contact.username || 'vibe'}`}</p>
                        </div>
                        {isStarting
                          ? <RefreshCw className="w-3.5 h-3.5 text-vibe-400 animate-spin flex-shrink-0" />
                          : <MessageSquare className="w-3.5 h-3.5 text-gray-300 flex-shrink-0" />
                        }
                      </button>
                    )
                  })}
                </div>
              )}

              {/* Empty state */}
              {conversations.length === 0 && contacts.length === 0 && (
                <div className="flex flex-col items-center justify-center py-12 text-gray-400 px-4">
                  <MessageSquare className="w-10 h-10 mb-3 opacity-20" />
                  <p className="text-sm font-medium text-gray-500">No conversations yet</p>
                  <p className="text-xs text-gray-400 text-center mt-1">Add contacts to start chatting!</p>
                  <button onClick={() => setAddContactOpen(true)} className="mt-4 btn-vibe text-xs px-4 py-2 flex items-center gap-2">
                    <UserPlus className="w-3.5 h-3.5" /> Add Contact
                  </button>
                </div>
              )}
              {filtered.length === 0 && conversations.length > 0 && (
                <div className="flex flex-col items-center justify-center py-12 text-gray-400">
                  <MessageSquare className="w-8 h-8 mb-2 opacity-30" />
                  <p className="text-sm">No conversations found</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>
      {addContactOpen && <AddContactModal onClose={() => setAddContactOpen(false)} />}
    </>
  )
}

function StatusStrip() {
  const { user } = useAuthStore()
  const { openComposer } = useUIStore()
  const [statuses, setStatuses] = useState([])
  useEffect(() => {
    statusAPI.getStatuses().then(res => {
      const data = res.data?.data || res.data?.data?.content || []
      if (Array.isArray(data) && data.length > 0) setStatuses(data)
    }).catch(() => {})
  }, [])
  return (
    <div className="px-3 py-3 border-b border-surface-border">
      <div className="flex gap-3 overflow-x-auto scrollbar-none">
        <div className="flex flex-col items-center gap-1 flex-shrink-0 cursor-pointer group" onClick={() => openComposer('status')}>
          <div className="w-11 h-11 rounded-full border-2 border-dashed border-vibe-400 flex items-center justify-center group-hover:border-vibe-600 transition-colors bg-sky-50">
            <Plus className="w-4 h-4 text-vibe-500" />
          </div>
          <span className="text-[9px] text-gray-500 whitespace-nowrap">My Status</span>
        </div>
        {statuses.map(s => <StatusAvatar key={s.id} status={s} />)}
      </div>
    </div>
  )
}

function StatusAvatar({ status }) {
  const [open, setOpen] = useState(false)
  const name = status.authorName || status.name || status.authorUsername || 'User'
  return (
    <>
      <div className="flex flex-col items-center gap-1 flex-shrink-0 cursor-pointer" onClick={() => setOpen(true)}>
        <div className={cn('w-11 h-11 rounded-full p-0.5', status.viewed ? 'bg-gray-200' : 'bg-gradient-to-br from-vibe-500 to-sky-400')}>
          <div className="w-full h-full rounded-full bg-white overflow-hidden">
            <Avatar name={name} src={status.avatar} size="md" />
          </div>
        </div>
        <span className="text-[9px] text-gray-500 whitespace-nowrap truncate max-w-[44px]">{name.split(' ')[0]}</span>
      </div>
      {open && <StatusViewer status={status} onClose={() => setOpen(false)} />}
    </>
  )
}

function StatusViewer({ status, onClose }) {
  const { user } = useAuthStore()
  const addToast = useUIStore(s => s.addToast)
  const [comment, setComment] = useState('')
  const [liked, setLiked]     = useState(false)
  const [progress, setProgress] = useState(0)
  useEffect(() => {
    statusAPI.viewStatus(status.id).catch(() => {})
    const interval = setInterval(() => setProgress(p => { if (p >= 100) { onClose(); return 100 } return p + 2 }), 100)
    return () => clearInterval(interval)
  }, [])
  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="relative w-full max-w-sm bg-gray-900 rounded-3xl overflow-hidden" style={{ height: '85vh' }}>
        <div className="absolute top-3 left-3 right-3 z-10">
          <div className="h-1 bg-white/30 rounded-full overflow-hidden">
            <div className="h-full bg-white rounded-full" style={{ width: `${progress}%`, transition: 'width 100ms linear' }} />
          </div>
        </div>
        <div className="absolute top-8 left-4 right-4 z-10 flex items-center gap-3">
          <Avatar name={status.authorName || status.name || status.authorUsername || 'User'} size="sm" className="border-2 border-white" />
          <div className="flex-1">
            <p className="text-white text-sm font-semibold">{status.authorName || status.name || status.authorUsername || 'User'}</p>
            <p className="text-white/60 text-xs">{timeAgo(status.createdAt || status.time)}</p>
          </div>
          <button onClick={onClose} className="text-white/80 hover:text-white"><X className="w-5 h-5" /></button>
        </div>
        <div className="w-full h-full bg-gradient-to-br from-vibe-700 to-sky-800 flex items-center justify-center">
          {status.mediaUrl
            ? <img src={status.mediaUrl} alt="status" className="w-full h-full object-cover" />
            : <p className="text-white text-xl font-medium text-center px-8">{status.content || 'Living the VIBE! 🚀'}</p>}
        </div>
        <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/60 to-transparent">
          <div className="flex items-center gap-2 mb-3">
            <Avatar name={user?.fullName || 'Me'} size="sm" />
            <input value={comment} onChange={e => setComment(e.target.value)} placeholder="Reply to status…"
              className="flex-1 bg-white/20 border border-white/30 rounded-full px-4 py-2 text-white text-sm placeholder:text-white/50 focus:outline-none" />
          </div>
          <div className="flex gap-2">
            <button onClick={async () => { if (!comment.trim()) return; try { await statusAPI.commentStatus(status.id, comment, false) } catch {} addToast('Commented!', 'success'); setComment('') }}
              className="flex-1 py-2 rounded-full bg-white/20 border border-white/30 text-white text-xs font-medium">
              💬 Comment
            </button>
            <button onClick={async () => { if (!comment.trim()) return; try { await statusAPI.commentStatus(status.id, comment, true) } catch {} addToast('Private reply sent!', 'info'); setComment('') }}
              className="flex-1 py-2 rounded-full bg-vibe-600/80 text-white text-xs font-medium">
              📩 Reply inbox
            </button>
            <button onClick={async () => { setLiked(l => !l); try { await statusAPI.likeStatus(status.id) } catch {} if (!liked) addToast('+1 VBT! 🪙', 'success') }}
              className={cn('w-10 h-10 rounded-full flex items-center justify-center', liked ? 'bg-red-500 text-white' : 'bg-white/20 border border-white/30 text-white')}>
              <Heart className="w-4 h-4" fill={liked ? 'currentColor' : 'none'} />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function ConvoItem({ convo, active, onClick }) {
  const { user } = useAuthStore()
  const contacts = useContactsStore(s => s.contacts)
  const displayName = getConversationDisplayName(convo, user?.id, contacts)
  // Backend field names: unreadCounts (per-user map), lastMessagePreview, lastMessageAt.
  // Fall back to legacy single-value names for safety.
  const unread  = (convo.unreadCounts && convo.unreadCounts[user?.id]) || convo.unreadCount || 0
  const preview = convo.lastMessagePreview || convo.lastMessage || 'Start a conversation…'
  const time    = convo.lastMessageAt || convo.lastMessageTime
  return (
    <div onClick={onClick}
      className={cn('flex items-center gap-3 px-3 py-3 cursor-pointer transition-all duration-150 mx-2 rounded-xl',
        active ? 'bg-sky-50 border border-vibe-200' : 'hover:bg-gray-50')}>
      <div className="relative flex-shrink-0">
        <Avatar name={displayName} src={convo.avatarUrl || convo.avatar} size="md" online={convo.online} />
        {convo.type === 'GROUP' && (
          <div className="absolute -bottom-0.5 -right-0.5 w-4 h-4 bg-vibe-600 rounded-full flex items-center justify-center">
            <Hash className="w-2.5 h-2.5 text-white" />
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-0.5">
          <span className={cn('text-sm font-semibold truncate', active ? 'text-vibe-700' : 'text-gray-800')}>{displayName}</span>
          <span className="text-[10px] text-gray-400 flex-shrink-0 ml-1">{time && formatMessageTime(time)}</span>
        </div>
        <div className="flex items-center justify-between">
          <p className="text-xs text-gray-500 truncate flex-1">{preview}</p>
          {unread > 0 && (
            <span className="ml-2 min-w-[18px] h-[18px] bg-vibe-600 rounded-full flex items-center justify-center text-[10px] font-bold text-white px-1 flex-shrink-0">
              {unread}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}

function isSameDayFn(d1, d2) {
  if (!d1 || !d2) return false
  const a = new Date(d1), b = new Date(d2)
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function DateDivider({ date }) {
  if (!date) return null
  const d = new Date(date)
  let label = ''
  try {
    if (isToday(d)) label = 'Today'
    else if (isYesterday(d)) label = 'Yesterday'
    else label = d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
  } catch { return null }
  return (
    <div className="flex items-center gap-3 my-3">
      <div className="flex-1 h-px bg-surface-border" />
      <span className="text-[10px] text-gray-400 font-medium px-3 py-1 bg-white border border-surface-border rounded-full whitespace-nowrap">{label}</span>
      <div className="flex-1 h-px bg-surface-border" />
    </div>
  )
}

function ChatArea({ conversation }) {
  const { user } = useAuthStore()
  const contacts = useContactsStore(s => s.contacts)
  // Derive a safe display name — direct chats have no backend `name` field
  // (by design, same as WhatsApp/Messenger). getConversationDisplayName resolves
  // from participantUsernames/phones or contacts store as a fallback.
  const conversationDisplayName = getConversationDisplayName(conversation, user?.id, contacts)
  const { messages, addMessage, updateMessage, deleteMessage, markRead, setMessages, markMySentRead } = useMessagingStore(s => ({
    messages: s.messages[conversation.id] || [],
    addMessage: s.addMessage, updateMessage: s.updateMessage,
    deleteMessage: s.deleteMessage, markRead: s.markRead, setMessages: s.setMessages,
    markMySentRead: s.markMySentRead,
  }))
  const addToast = useUIStore(s => s.addToast)
  const isGroup  = conversation.type === 'GROUP'

  const [text, setText]               = useState('')
  const [sending, setSending]         = useState(false)
  const [emojiOpen, setEmojiOpen]     = useState(false)
  const [aiMenuOpen, setAiMenuOpen]   = useState(false)
  const [aiLoading, setAiLoading]     = useState(false)
  const [aiSummary, setAiSummary]     = useState(null)
  const [showCalendar, setShowCalendar] = useState(false)
  const [calFrom, setCalFrom]         = useState('')
  const [calTo, setCalTo]             = useState('')
  const [translatedMsgs, setTranslatedMsgs] = useState({})
  const [translateTarget, setTranslateTarget] = useState(null)
  const [editingMsg, setEditingMsg]   = useState(null)
  const [editText, setEditText]       = useState('')
  const [replyTo, setReplyTo]         = useState(null)
  const [isRecording, setIsRecording] = useState(false)
  const [mediaRecorder, setMediaRecorder] = useState(null)
  const [recordDuration, setRecordDuration] = useState(0)
  const [typingTimeout, setTypingTimeout]   = useState(null)
  const [typingIndicator, setTypingIndicator] = useState(null)
  const [loadingMsgs, setLoadingMsgs] = useState(false)
  const [reactions, setReactions]     = useState({})
  const [reactionTarget, setReactionTarget] = useState(null)
  const [showGroupInfo, setShowGroupInfo]   = useState(false)

  // ── Presence state (WhatsApp/Telegram: Online / Last seen X) ──────────────
  const [presenceStatus, setPresenceStatus] = useState(null)  // 'online' | null
  const [lastSeen, setLastSeen]             = useState(null)  // ISO timestamp

  const recordTimer  = useRef(null)
  const fileInputRef = useRef()
  const imageInputRef = useRef()
  const endRef       = useRef()
  const inputRef     = useRef()
  const aiRef        = useRef()
  const emojiRef     = useRef()

  useEffect(() => {
    setLoadingMsgs(true)
    setAiSummary(null); setTranslatedMsgs({}); setReplyTo(null); setEmojiOpen(false)
    messagingAPI.getMessages(conversation.id, 0).then(res => {
      const data = res.data?.data?.content || res.data?.data || []
      // Order oldest → newest so the latest message sits at the BOTTOM. We can't
      // trust the backend order (its @Query ignores the OrderByCreatedAtDesc in
      // the method name), so sort explicitly by createdAt.
      if (Array.isArray(data) && data.length > 0) {
        const ordered = [...data].sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0))
        setMessages(conversation.id, ordered)
      }
    }).catch(() => {}).finally(() => setLoadingMsgs(false))
    markRead(conversation.id)
    messagingAPI.markRead(conversation.id).catch(() => {})

    // Subscribe to incoming messages.
    // Backend sends: { type:"NEW_MESSAGE", message:{...}, conversationId:"..." }
    // Also handles MESSAGE_EDITED and MESSAGE_DELETED envelope types.
    //
    // IMPORTANT: wsService.subscribeToConversation() now uses addConversationListener()
    // with key='chat-area', which co-exists with the 'list-updater' listener
    // registered by ConversationList. Both get called for every message on this topic.
    const unsubMsg = wsService.subscribeToConversation(conversation.id, (payload) => {
      const msgType  = payload?.type
      const envelope = payload?.message || payload   // fallback for raw objects

      // ── Edit notification ────────────────────────────────────────────────
      if (msgType === 'MESSAGE_EDITED' && envelope?.id) {
        updateMessage(conversation.id, envelope.id, {
          content: envelope.content, edited: true, editedAt: envelope.editedAt,
        })
        return
      }

      // ── Delete notification ──────────────────────────────────────────────
      if (msgType === 'MESSAGE_DELETED' && payload?.messageId) {
        updateMessage(conversation.id, payload.messageId, {
          deleted: true, content: 'This message was deleted',
        })
        return
      }

      // ── Delivery receipt ─────────────────────────────────────────────────
      if (msgType === 'DELIVERY_RECEIPT') return   // handled by receipt subscriber

      // ── New message ──────────────────────────────────────────────────────
      if (!envelope?.senderId && !envelope?.id) return  // guard empty payloads

      const normalised = {
        id:             envelope.id,
        conversationId: envelope.conversationId || conversation.id,
        senderId:       envelope.senderId,
        senderName:     envelope.senderUsername || envelope.senderName || 'User',
        content:        envelope.content || '',
        type:           (envelope.type === 'NEW_MESSAGE' ? 'TEXT' : (envelope.type || 'TEXT')),
        mediaUrl:       envelope.mediaUrl || null,
        createdAt:      envelope.createdAt || new Date().toISOString(),
        status:         envelope.deliveryStatus || envelope.status || 'DELIVERED',
        deleted:        envelope.deleted || envelope.isDeleted || false,
        edited:         envelope.edited  || envelope.isEdited  || false,
        replyTo:        envelope.replyToMessageId ? { id: envelope.replyToMessageId } : null,
      }

      // Only add if it is NOT from the current user (they already have the optimistic copy)
      if (normalised.senderId !== user?.id) {
        addMessage(conversation.id, normalised)
        messagingAPI.markRead(conversation.id).catch(() => {})
        if (normalised.id) wsService.sendReadReceipt(conversation.id, normalised.id)
      }
    })

    // Subscribe to typing. The backend sends { userId, isTyping } with no name,
    // so when senderName is absent we resolve it from the conversation's parallel
    // participantUsernames (or the chat's display name for direct chats) instead
    // of showing a raw UUID.
    const unsubTyping = wsService.subscribeToTyping(conversation.id, ({ userId: uid, isTyping, senderName }) => {
      if (uid !== user?.id) {
        let who = senderName
        if (!who) {
          const idx = conversation.participantIds?.indexOf(uid) ?? -1
          who = (idx >= 0 && conversation.participantUsernames?.[idx])
            || (!isGroup ? conversationDisplayName : null)
            || 'Someone'
        }
        setTypingIndicator(isTyping ? who : null)
        if (isTyping) {
          clearTimeout(window._vibeTC)
          window._vibeTC = setTimeout(() => setTypingIndicator(null), 3000)
        }
      }
    })

    // Subscribe to read receipts. The backend sends { readBy, conversationId }
    // with NO messageId, so when the OTHER participant reads, mark every message
    // I sent in this conversation as READ (blue ticks).
    const unsubRead = wsService.subscribeToReadReceipts(conversation.id, ({ readBy }) => {
      if (readBy && readBy !== user?.id) {
        markMySentRead(conversation.id, user?.id)
      }
    })

    // ── Presence subscription (WhatsApp/Telegram style) ──────────────────────
    // Fetch initial presence for direct conversations
    if (!conversation.isGroup && conversation.participantIds) {
      const otherId = conversation.participantIds.find(id => id !== user?.id)
      if (otherId) {
        import('../../services/api').then(({ messagingAPI: mAPI }) => {
          mAPI.getPresence(otherId)
          .then(r => r.data)
          .then(data => {
            const info = data?.data || data
            if (info?.status === 'online') {
              setPresenceStatus('online'); setLastSeen(null)
            } else if (info?.lastSeen) {
              setPresenceStatus('offline'); setLastSeen(info.lastSeen)
            }
          })
          .catch(() => {})
        }).catch(() => {})
      }
    }

    // Real-time presence changes from /topic/presence
    const unsubPresence = wsService.subscribeToPresence((info) => {
      if (!conversation.isGroup && conversation.participantIds) {
        const otherId = conversation.participantIds.find(id => id !== user?.id)
        if (info.userId === otherId) {
          if (info.status === 'online') {
            setPresenceStatus('online'); setLastSeen(null)
          } else {
            setPresenceStatus('offline'); setLastSeen(info.lastSeen || null)
          }
        }
      }
    })

    return () => {
      unsubMsg?.()
      unsubTyping?.()
      unsubRead?.()
      unsubPresence?.()
      clearTimeout(window._vibeTC)
    }
  }, [conversation.id])

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages.length])

  useEffect(() => {
    const h = (e) => { if (!aiRef.current?.contains(e.target)) setAiMenuOpen(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const sendMessage = async (e) => {
    e?.preventDefault()
    if (!text.trim() || sending) return
    const msgText = text.trim()
    setText(''); setSending(true); setEmojiOpen(false)
    const opt = {
      id: `tmp-${Date.now()}`, content: msgText,
      senderId: user?.id || 'me', senderName: user?.fullName || 'You',
      createdAt: new Date().toISOString(), type: 'TEXT', status: 'SENDING',
      replyTo: replyTo ? { id: replyTo.id, content: truncate(replyTo.content, 40), senderName: replyTo.senderName } : null,
    }
    addMessage(conversation.id, opt)
    setReplyTo(null)
    try {
      const res = await messagingAPI.sendMessage(conversation.id, { content: msgText, type: 'TEXT', replyToId: replyTo?.id || null })
      const saved = res.data?.data
      // Update the optimistic message with the real server-assigned ID and status.
      // DO NOT call wsService.sendMessage() here — the REST handler in the backend
      // already broadcasts the message to /topic/conversation/{id} via WebSocket.
      // Calling it again would cause double delivery for all recipients.
      updateMessage(conversation.id, opt.id, { ...(saved || {}), status: 'DELIVERED' })
    } catch { addToast('Failed to send', 'error') }
    finally { setSending(false) }
  }

  const handleEmojiSelect = (emoji) => {
    const el = inputRef.current
    if (!el) { setText(t => t + emoji); return }
    const start = el.selectionStart ?? text.length
    const end   = el.selectionEnd   ?? text.length
    const newText = text.slice(0, start) + emoji + text.slice(end)
    setText(newText)
    // Restore cursor position after re-render
    requestAnimationFrame(() => {
      el.selectionStart = el.selectionEnd = start + emoji.length
      el.focus()
    })
  }

  const handleAISummarize = async (opts) => {
    setAiLoading(true); setAiMenuOpen(false); setShowCalendar(false)
    try {
      // Build a local transcript from the messages already in the store —
      // this avoids a round-trip to the messaging service and works even when
      // the AI service is the only optional service running.
      const localMessages = messages.filter(m => !m.deleted && m.content?.trim())
      const transcript = localMessages
        .slice(-60) // last 60 messages — enough context for a useful summary
        .map(m => `[${formatMessageTime(m.createdAt)}] ${m.senderName || 'User'}: ${m.content}`)
        .join('\n')

      const res = await aiAPI.summarize({
        conversationId: conversation.id,
        text: transcript || 'No messages to summarize.',
        language: 'en',
        style: 'BULLET',
        ...opts,
      })
      // AiController returns { data: { summary } } wrapped in ApiResponse
      const summary =
        res.data?.data?.summary ||   // ApiResponse<{ summary }> shape
        res.data?.summary      ||   // flat shape
        res.data?.data         ||   // raw string
        'No summary available.'
      setAiSummary(typeof summary === 'string' ? summary : JSON.stringify(summary))
    } catch (err) {
      const status = err?.response?.status
      if (status === 401 || status === 403) {
        setAiSummary('⚠️ AI service authentication error — check that the AI service JWT secret matches auth-service.')
      } else if (!status) {
        setAiSummary('⚠️ AI service is not reachable. Start the ai-service and try again.')
      } else {
        setAiSummary('⚠️ AI summary unavailable — ensure the AI service is running.')
      }
    } finally { setAiLoading(false) }
  }

  const handleTranslate = async (msgId, content, lang) => {
    try {
      const res = await aiAPI.translate({ text: content, targetLanguage: lang })
      // AiController returns ApiResponse<{ translated, original, targetLang }>
      // The api.js interceptor maps targetLanguage → toLang, and the response
      // comes back as res.data.data.translated (ApiResponse wrapper).
      const t =
        res.data?.data?.translated   || // ApiResponse<{translated}> shape
        res.data?.data?.translatedText || // alternate key
        res.data?.translated          || // flat shape (no ApiResponse wrapper)
        res.data?.translatedText      || // original flat key
        null
      setTranslatedMsgs(prev => ({ ...prev, [msgId]: t || `[Translation to ${lang} unavailable]` }))
    } catch (err) {
      const status = err?.response?.status
      const errMsg = status === 401 || status === 403
        ? '[AI auth error — check JWT secret]'
        : '[Translation unavailable]'
      setTranslatedMsgs(prev => ({ ...prev, [msgId]: errMsg }))
    }
    setTranslateTarget(null)
  }

  const handleEditSave = async (msgId) => {
    if (!editText.trim()) return
    updateMessage(conversation.id, msgId, { content: editText, edited: true })
    try { await messagingAPI.editMessage(msgId, editText) } catch {}
    setEditingMsg(null); setEditText('')
  }

  const handleDelete = async (msg, forAll) => {
    if (!canEditOrDelete(msg.createdAt) && forAll) { addToast('Cannot delete for everyone after 15 minutes', 'error'); return }
    deleteMessage(conversation.id, msg.id, forAll)
    try { await messagingAPI.deleteMessage(msg.id, forAll) } catch {}
  }

  const handleReaction = (msgId, emoji) => {
    setReactions(r => ({ ...r, [msgId]: { ...(r[msgId] || {}), [emoji]: ((r[msgId] || {})[emoji] || 0) + 1 } }))
    setReactionTarget(null)
    wsService.sendReaction(msgId, emoji, user?.id)
  }

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const rec    = new MediaRecorder(stream)
      const chunks = []
      rec.ondataavailable = e => chunks.push(e.data)
      rec.onstop = async () => {
        const blob = new Blob(chunks, { type: 'audio/webm' })
        const file = new File([blob], 'voice.webm', { type: 'audio/webm' })
        clearInterval(recordTimer.current); setRecordDuration(0)
        stream.getTracks().forEach(t => t.stop())
        try {
          const res = await mediaAPI.upload(file, 'voice')
          const url = res.data?.data?.url
          const dur = Math.round(blob.size / 16000)
          const opt = { id: `tmp-${Date.now()}`, type: 'VOICE', mediaUrl: url, content: '', durationSeconds: dur, senderId: user?.id, senderName: user?.fullName, createdAt: new Date().toISOString(), status: 'SENDING' }
          addMessage(conversation.id, opt)
          await messagingAPI.sendMessage(conversation.id, { type: 'VOICE', mediaUrl: url, content: '', durationSeconds: dur })
          updateMessage(conversation.id, opt.id, { status: 'DELIVERED' })
          addToast('Voice note sent! 🎙️', 'success')
        } catch { addToast('Voice upload failed', 'error') }
      }
      rec.start(); setMediaRecorder(rec); setIsRecording(true); setRecordDuration(0)
      recordTimer.current = setInterval(() => setRecordDuration(d => d + 1), 1000)
    } catch { addToast('Microphone access denied', 'error') }
  }

  const stopRecording = () => { mediaRecorder?.stop(); setIsRecording(false); setMediaRecorder(null) }

  const handleTyping = (val) => {
    setText(val)
    if (wsService.connected) {
      wsService.sendTyping(conversation.id, user?.id, true)
      clearTimeout(typingTimeout)
      setTypingTimeout(setTimeout(() => wsService.sendTyping(conversation.id, user?.id, false), 2000))
    }
  }

  const QUICK_REACTIONS = ['❤️', '😂', '👍', '😮', '😢', '🔥']

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-3 border-b border-surface-border bg-white shadow-sm flex-shrink-0">
        <div className="flex items-center gap-3">
          {/* Avatar with live green dot when online */}
          <div className="relative flex-shrink-0">
            <Avatar name={conversationDisplayName} src={conversation.avatar} size="md" />
            {!isGroup && presenceStatus === 'online' && (
              <span className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 border-2 border-white rounded-full" />
            )}
          </div>
          <div>
            <h2 className="font-display font-bold text-gray-900 text-sm">{conversationDisplayName}</h2>
            {/* Presence line — WhatsApp / Telegram style */}
            <p className={[
              'text-xs font-medium',
              !isGroup && presenceStatus === 'online' ? 'text-green-500' : 'text-gray-400',
            ].join(' ')}>
              {isGroup
                ? `${conversation.memberCount || conversation.participantIds?.length || '?'} members`
                : presenceStatus === 'online'
                  ? 'Online'
                  : lastSeen
                    ? `Last seen ${formatLastSeen(lastSeen)}`
                    : 'Last seen recently'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <div ref={aiRef} className="relative">
            <button onClick={() => setAiMenuOpen(o => !o)}
              className={cn('flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all',
                aiMenuOpen || aiLoading ? 'bg-vibe-100 text-vibe-700 border border-vibe-300' : 'text-gray-500 hover:text-vibe-600 hover:bg-sky-50')}>
              <Bot className="w-3.5 h-3.5" />
              <span className="hidden sm:block">AI Recap</span>
              {aiLoading && <span className="animate-spin text-xs ml-1">⟳</span>}
            </button>
            {aiMenuOpen && (
              <div className="absolute right-0 top-full mt-2 bg-white border border-surface-border rounded-2xl shadow-xl py-1 w-60 z-50 animate-fade-in">
                <p className="px-4 py-2 text-[10px] text-gray-400 uppercase tracking-wider font-semibold">🤖 VIBE AI Recap</p>
                {[
                  { label: 'Unread messages',  opts: { type: 'UNREAD' } },
                  { label: "Today's messages", opts: { period: 'TODAY' } },
                  { label: 'Yesterday',        opts: { period: 'YESTERDAY' } },
                  { label: 'Last 2 days',      opts: { period: '2DAYS' } },
                  { label: 'This week',        opts: { period: 'WEEK' } },
                  { label: 'This month',       opts: { period: 'MONTH' } },
                ].map(item => (
                  <button key={item.label} onClick={() => handleAISummarize(item.opts)}
                    className="w-full flex items-center gap-3 px-4 py-2 text-sm text-gray-600 hover:text-vibe-700 hover:bg-sky-50 transition-colors text-left">
                    <Bot className="w-3.5 h-3.5 text-vibe-400" />{item.label}
                  </button>
                ))}
                <div className="border-t border-surface-border mx-3 my-1" />
                <button onClick={() => { setAiMenuOpen(false); setShowCalendar(true) }}
                  className="w-full flex items-center gap-3 px-4 py-2 text-sm text-gray-600 hover:text-vibe-700 hover:bg-sky-50 transition-colors text-left">
                  <Calendar className="w-3.5 h-3.5 text-vibe-400" />Pick date range…
                </button>
              </div>
            )}
          </div>
          <button className="p-2 rounded-lg text-gray-400 hover:text-vibe-600 hover:bg-sky-50 transition-all" title="Voice call"><Phone className="w-4 h-4" /></button>
          <button className="p-2 rounded-lg text-gray-400 hover:text-vibe-600 hover:bg-sky-50 transition-all" title="Video call"><Video className="w-4 h-4" /></button>
          {isGroup && <button onClick={() => setShowGroupInfo(true)} className="p-2 rounded-lg text-gray-400 hover:text-vibe-600 hover:bg-sky-50 transition-all" title="Group info"><Info className="w-4 h-4" /></button>}
        </div>
      </div>

      {showCalendar && (
        <div className="mx-4 mt-2 p-3 bg-sky-50 border border-vibe-200 rounded-xl flex flex-wrap items-center gap-2 animate-fade-in flex-shrink-0">
          <Calendar className="w-4 h-4 text-vibe-500 flex-shrink-0" />
          <span className="text-sm text-gray-600">From:</span>
          <input type="date" value={calFrom} onChange={e => setCalFrom(e.target.value)} className="text-xs border border-surface-border rounded-lg px-2 py-1 focus:outline-none focus:border-vibe-400" />
          <span className="text-xs text-gray-400">to</span>
          <input type="date" value={calTo} onChange={e => setCalTo(e.target.value)} className="text-xs border border-surface-border rounded-lg px-2 py-1 focus:outline-none focus:border-vibe-400" />
          <button onClick={() => handleAISummarize({ type: 'CUSTOM', from: calFrom, to: calTo })} disabled={!calFrom || !calTo} className="btn-vibe text-xs px-3 py-1.5 disabled:opacity-40">Recap</button>
          <button onClick={() => setShowCalendar(false)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
        </div>
      )}

      {aiSummary && (
        <div className="mx-4 mt-2 p-3 rounded-xl bg-vibe-50 border border-vibe-200 flex items-start gap-3 animate-fade-in flex-shrink-0">
          <Bot className="w-4 h-4 text-vibe-500 mt-0.5 flex-shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-xs font-semibold text-vibe-600 mb-1">🤖 AI Recap</p>
            <p className="text-xs text-gray-600 leading-relaxed">{aiSummary}</p>
          </div>
          <button onClick={() => setAiSummary(null)} className="text-gray-400 hover:text-gray-600 flex-shrink-0"><X className="w-4 h-4" /></button>
        </div>
      )}

      {replyTo && (
        <div className="mx-4 mt-2 p-2 bg-sky-50 border-l-4 border-vibe-400 rounded-r-xl flex items-center gap-2 animate-fade-in flex-shrink-0">
          <Reply className="w-3.5 h-3.5 text-vibe-500 flex-shrink-0" />
          <div className="flex-1 min-w-0">
            <span className="text-xs font-semibold text-vibe-600">{replyTo.senderName}</span>
            <p className="text-xs text-gray-500 truncate">{replyTo.content}</p>
          </div>
          <button onClick={() => setReplyTo(null)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
        </div>
      )}

      {/* Messages */}
      <div className="flex-1 overflow-y-auto scrollbar-none px-4 py-4 bg-gradient-to-b from-sky-50/30 to-white">
        {loadingMsgs && <div className="flex justify-center py-8"><div className="w-6 h-6 border-2 border-vibe-400 border-t-transparent rounded-full animate-spin"/></div>}
        {!loadingMsgs && messages.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 text-gray-400">
            <MessageSquare className="w-10 h-10 mb-3 opacity-20" />
            <p className="text-sm font-medium">No messages yet</p>
            <p className="text-xs mt-1">Say hello! 👋</p>
          </div>
        )}
        {messages.map((msg, i) => {
          const isMine = !!(user?.id && msg.senderId === user.id)
          const prev   = messages[i - 1]
          const next   = messages[i + 1]
          const showSender    = isGroup && (prev?.senderId !== msg.senderId || i === 0)
          const isLastInGroup = !next || next.senderId !== msg.senderId
          const showDate      = i === 0 || !isSameDayFn(msg.createdAt, messages[i-1].createdAt)
          return (
            <React.Fragment key={msg.id || `tmp-${i}`}>
              {showDate && <DateDivider date={msg.createdAt} />}
              <MessageBubble
                msg={msg} isMine={isMine} isGroup={isGroup}
                showSender={showSender} isLastInGroup={isLastInGroup}
                translated={translatedMsgs[msg.id]}
                translateTarget={translateTarget}
                onTranslate={(lang) => handleTranslate(msg.id, msg.content, lang)}
                onOpenTranslate={() => setTranslateTarget(translateTarget === msg.id ? null : msg.id)}
                editingMsg={editingMsg} editText={editText} setEditText={setEditText}
                onEditStart={() => { setEditingMsg(msg.id); setEditText(msg.content) }}
                onEditSave={() => handleEditSave(msg.id)}
                onEditCancel={() => setEditingMsg(null)}
                onDelete={(forAll) => handleDelete(msg, forAll)}
                onReply={() => setReplyTo(msg)}
                reactions={reactions[msg.id] || {}}
                onReaction={(emoji) => handleReaction(msg.id, emoji)}
                reactionTarget={reactionTarget}
                onOpenReaction={() => setReactionTarget(reactionTarget === msg.id ? null : msg.id)}
                QUICK_REACTIONS={QUICK_REACTIONS}
              />
            </React.Fragment>
          )
        })}
        <div ref={endRef} />
      </div>

      {typingIndicator && (
        <div className="px-6 py-2 bg-white border-t border-surface-border flex-shrink-0">
          <div className="flex items-center gap-2">
            <div className="flex gap-1">
              {[0, 150, 300].map(delay => (
                <span key={delay} className="w-1.5 h-1.5 bg-vibe-400 rounded-full animate-bounce" style={{ animationDelay: `${delay}ms` }} />
              ))}
            </div>
            <p className="text-xs text-gray-400 italic">{typingIndicator} is typing…</p>
          </div>
        </div>
      )}

      {isRecording && (
        <div className="mx-4 mb-2 p-3 bg-red-50 border border-red-200 rounded-xl flex items-center gap-3 flex-shrink-0">
          <div className="w-3 h-3 bg-red-500 rounded-full animate-pulse" />
          <span className="text-sm font-medium text-red-600">Recording… {recordDuration}s</span>
          <button onClick={() => { mediaRecorder?.stop(); setIsRecording(false); setMediaRecorder(null); clearInterval(recordTimer.current); setRecordDuration(0) }}
            className="ml-auto text-red-400 hover:text-red-600 text-xs border border-red-200 rounded-lg px-2 py-1">Cancel</button>
        </div>
      )}

      {/* ── Input Bar ── */}
      <div className="p-4 border-t border-surface-border bg-white flex-shrink-0">
        <div className="relative">
          {/* Emoji Picker (above input) */}
          {emojiOpen && (
            <EmojiPicker
              onSelect={handleEmojiSelect}
              onClose={() => setEmojiOpen(false)}
            />
          )}

          <div className="flex items-end gap-3 bg-sky-50 border border-surface-border rounded-2xl px-4 py-2.5 focus-within:border-vibe-400 transition-colors">
            {/* Emoji button */}
            <button
              type="button"
              onClick={() => setEmojiOpen(o => !o)}
              className={cn(
                'transition-colors pb-0.5 flex-shrink-0',
                emojiOpen ? 'text-vibe-600' : 'text-gray-400 hover:text-vibe-500'
              )}
              title="Emoji"
            >
              <Smile className="w-5 h-5" />
            </button>

            <input ref={fileInputRef} type="file" accept="image/*,video/*,audio/*,.pdf,.doc,.docx" className="hidden"
              onChange={async (e) => {
                const file = e.target.files?.[0]; if (!file) return
                try {
                  const res = await mediaAPI.upload(file)
                  const url = res.data?.data?.url
                  const type = file.type.startsWith('image/') ? 'IMAGE' : file.type.startsWith('video/') ? 'VIDEO' : 'FILE'
                  const opt = { id: `tmp-${Date.now()}`, type, mediaUrl: url, content: file.name, senderId: user?.id, senderName: user?.fullName, createdAt: new Date().toISOString(), status: 'SENDING' }
                  addMessage(conversation.id, opt)
                  await messagingAPI.sendMessage(conversation.id, { type, mediaUrl: url, content: file.name })
                  updateMessage(conversation.id, opt.id, { status: 'DELIVERED' })
                  addToast('File sent!', 'success')
                } catch { addToast('Upload failed', 'error') }
                e.target.value = ''
              }} />
            <input ref={imageInputRef} type="file" accept="image/*" className="hidden"
              onChange={async (e) => {
                const file = e.target.files?.[0]; if (!file) return
                try {
                  const res = await mediaAPI.upload(file, 'chat-image')
                  const url = res.data?.data?.url
                  const opt = { id: `tmp-${Date.now()}`, type: 'IMAGE', mediaUrl: url, content: '', senderId: user?.id, senderName: user?.fullName, createdAt: new Date().toISOString(), status: 'SENDING' }
                  addMessage(conversation.id, opt)
                  await messagingAPI.sendMessage(conversation.id, { type: 'IMAGE', mediaUrl: url, content: '' })
                  updateMessage(conversation.id, opt.id, { status: 'DELIVERED' })
                  addToast('Image sent! 📸', 'success')
                } catch { addToast('Upload failed', 'error') }
                e.target.value = ''
              }} />

            <button type="button" onClick={() => fileInputRef.current?.click()} className="text-gray-400 hover:text-vibe-500 transition-colors pb-0.5 flex-shrink-0" title="Attach file"><Paperclip className="w-5 h-5" /></button>
            <button type="button" onClick={() => imageInputRef.current?.click()} className="text-gray-400 hover:text-vibe-500 transition-colors pb-0.5 flex-shrink-0" title="Send image"><ImageIcon className="w-5 h-5" /></button>

            <textarea
              ref={inputRef}
              value={text}
              onChange={e => handleTyping(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(e) } }}
              placeholder={`Message ${conversationDisplayName}…`}
              rows={1}
              className="flex-1 bg-transparent text-gray-800 text-sm placeholder:text-gray-400 resize-none focus:outline-none max-h-32 py-0.5"
            />

            {!text.trim() ? (
              <button type="button"
                onMouseDown={startRecording} onMouseUp={stopRecording}
                onMouseLeave={() => { if (isRecording) stopRecording() }}
                onTouchStart={startRecording} onTouchEnd={stopRecording}
                className={`transition-colors pb-0.5 flex-shrink-0 ${isRecording ? 'text-red-500 animate-pulse' : 'text-gray-400 hover:text-vibe-500'}`}
                title="Hold to record voice note">
                <Mic className="w-5 h-5" />
              </button>
            ) : (
              <button
                onClick={sendMessage}
                disabled={!text.trim() || sending}
                className="w-8 h-8 rounded-xl bg-vibe-600 hover:bg-vibe-500 flex items-center justify-center transition-all disabled:opacity-30 active:scale-95 flex-shrink-0"
              >
                <Send className="w-4 h-4 text-white" />
              </button>
            )}
          </div>
        </div>
      </div>

      {showGroupInfo && <GroupInfoModal conversation={conversation} onClose={() => setShowGroupInfo(false)} />}
    </div>
  )
}

// VoiceMessage, MessageBubble, and MsgBtn have been moved to:
// src/components/messenger/MessageBubble.jsx
// They are imported at the top of this file.

function GroupInfoModal({ conversation, onClose }) {
  return (
    <div className="fixed inset-0 z-[100] bg-black/50 flex items-center justify-center" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="bg-white rounded-3xl w-full max-w-sm mx-4 overflow-hidden shadow-2xl animate-fade-in">
        <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
          <h2 className="font-display font-bold text-gray-900">Group Info</h2>
          <button onClick={onClose}><X className="w-5 h-5 text-gray-400" /></button>
        </div>
        <div className="p-5 space-y-4">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-vibe-100 flex items-center justify-center text-2xl font-bold text-vibe-600">
              {(conversation.name || 'G')[0]}
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">{conversation.name || 'Group Chat'}</h3>
              <p className="text-xs text-gray-500">{conversation.memberCount || '?'} members</p>
            </div>
          </div>
          <div className="space-y-1">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">VIBE Features</p>
            {[
              { icon: '🤖', label: 'AI Recap',             desc: 'Summarize any period instantly' },
              { icon: '🌐', label: 'Real-time Translation', desc: 'Hold message → translate to any language' },
              { icon: '🎙️', label: 'Voice Notes',          desc: 'Hold mic button to record, release to send' },
              { icon: '📎', label: 'File Sharing',          desc: 'Images, videos, documents' },
              { icon: '😊', label: 'Emoji Keyboard',        desc: 'Full emoji picker with 9 categories' },
              { icon: '💬', label: 'Reactions',             desc: 'Hover message to react with emoji' },
            ].map(f => (
              <div key={f.label} className="flex items-center gap-3 p-2 rounded-xl hover:bg-sky-50">
                <span className="text-lg">{f.icon}</span>
                <div>
                  <p className="text-xs font-medium text-gray-800">{f.label}</p>
                  <p className="text-[10px] text-gray-400">{f.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function ChatWelcome() {
  const { openComposer } = useUIStore()
  return (
    <div className="flex-1 flex flex-col items-center justify-center text-center p-8 bg-gradient-to-b from-sky-50/50 to-white">
      <div className="w-20 h-20 rounded-3xl bg-vibe-50 border border-vibe-200 flex items-center justify-center mb-5">
        <img src="/vibe-logo.svg" alt="VIBE" className="w-12 h-12" onError={e => { e.target.style.display='none' }} />
      </div>
      <h2 className="font-display text-2xl font-bold text-gray-900 mb-2">Welcome to VIBE Messenger</h2>
      <p className="text-gray-400 text-sm max-w-xs leading-relaxed">Select a conversation to start messaging, or tap + to connect with friends.</p>
      <div className="mt-6 flex gap-3 text-xs text-gray-400">
        <span>🔒 Encrypted</span><span>·</span><span>🌍 Multilingual AI</span><span>·</span><span>🪙 Earn tokens</span>
      </div>
      <button onClick={() => openComposer('meetFriends')} className="mt-8 btn-vibe flex items-center gap-2 px-6 py-3">
        <UserPlus className="w-4 h-4" /> Add Your First Contact
      </button>
    </div>
  )
}

function NotificationsPage() {
  const addToast = useUIStore(s => s.addToast)
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading]             = useState(true)
  const load = async () => {
    try {
      const res = await notificationAPI.getAll()
      const data = res.data?.data || res.data?.data?.content || []
      setNotifications(Array.isArray(data) ? data : [])
    } catch { setNotifications([]) }
    finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  useEffect(() => {
    // wsService.subscribe() stores the subscription and re-registers it on
    // every reconnect, so we don't need to guard with wsService.connected.
    const unsub = wsService.subscribe('/user/queue/notifications', (msg) => setNotifications(prev => [msg, ...prev]))
    return unsub
  }, [])
  const emojiFor = (type) => ({ LIKE:'❤️', COMMENT:'💬', FOLLOW:'👤', MESSAGE:'📩', REWARD:'🪙', ROOM:'🎙️', MENTION:'@', CONTACT_REQUEST:'🤝' }[type] || '🔔')
  return (
    <div className="flex-1 overflow-y-auto bg-white">
      <div className="px-6 py-4 border-b border-surface-border flex items-center justify-between">
        <h1 className="font-display text-xl font-bold text-gray-900">Notifications</h1>
        <button onClick={async () => { try { await notificationAPI.markAllRead(); setNotifications(prev => prev.map(n => ({ ...n, read: true }))); addToast('All marked as read', 'success') } catch {} }} className="text-xs text-vibe-600 font-medium">Mark all read</button>
      </div>
      <div className="p-4 space-y-2 max-w-2xl mx-auto">
        {loading && <div className="flex justify-center py-8"><div className="w-6 h-6 border-2 border-vibe-400 border-t-transparent rounded-full animate-spin"/></div>}
        {!loading && notifications.length === 0 && (
          <div className="text-center py-12 text-gray-400"><p className="text-4xl mb-3">🔔</p><p className="text-sm">No notifications yet</p></div>
        )}
        {notifications.map(n => (
          <div key={n.id} onClick={() => notificationAPI.markRead(n.id).catch(() => {})}
            className={cn('flex items-start gap-4 p-4 rounded-2xl border cursor-pointer transition-all hover:shadow-sm', n.read ? 'bg-white border-surface-border' : 'bg-sky-50 border-vibe-200')}>
            <div className="w-10 h-10 rounded-full bg-sky-100 flex items-center justify-center flex-shrink-0 text-lg">{emojiFor(n.type)}</div>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-gray-700"><span className="font-semibold text-gray-900">{n.actorId || n.actor || 'Someone'}</span>{' '}{n.message || n.action || 'sent you a notification'}</p>
              <p className="text-xs text-gray-400 mt-1">{timeAgo(n.createdAt || n.time)}</p>
            </div>
            {!n.read && <div className="w-2 h-2 bg-vibe-500 rounded-full mt-2 flex-shrink-0" />}
          </div>
        ))}
      </div>
    </div>
  )
}

function ProfilePage() {
  const { user, hasPublicAccount, logout, upgradeToCreator, updateUser } = useAuthStore()
  const { setSection, addToast } = useUIStore()
  const balance = useWalletStore(s => s.balance) || 0
  const [showWallet, setShowWallet] = useState(false)
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const avatarRef = useRef(null)

  const onAvatarPick = async (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    if (!f.type.startsWith('image/')) { addToast('Please choose an image', 'error'); return }
    setUploadingAvatar(true)
    try {
      const up = await mediaAPI.upload(f, 'avatar')
      const url = up.data?.data?.url || up.data?.url
      if (!url) throw new Error('Upload returned no URL')
      await authAPI.updateAvatar(url)
      updateUser({ profilePictureUrl: url })   // reflect instantly in the UI
      addToast('Profile photo updated 📸', 'success')
    } catch (err) {
      const m = err.response?.data?.message || err.response?.data?.data
      addToast(typeof m === 'string' ? m : (err.message || 'Could not update photo'), 'error')
    } finally {
      setUploadingAvatar(false)
      if (avatarRef.current) avatarRef.current.value = ''
    }
  }

  return (
    <div className="flex-1 overflow-y-auto bg-gray-50">
      <div className="h-28 vibe-hero-bg relative">
        <div className="absolute inset-0 opacity-20" style={{ backgroundImage: 'radial-gradient(circle at 70% 50%, rgba(255,255,255,0.3) 0%, transparent 60%)' }} />
      </div>
      <div className="px-6 -mt-12 pb-8">
        <div className="flex items-end justify-between mb-5">
          <div className="relative">
            <div className="w-20 h-20 rounded-2xl border-4 border-white shadow-lg overflow-hidden">
              <Avatar name={user?.fullName || 'User'} src={user?.profilePictureUrl} size="2xl" />
            </div>
            {/* Change-photo button */}
            <input ref={avatarRef} type="file" accept="image/*" className="hidden" onChange={onAvatarPick} />
            <button
              onClick={() => avatarRef.current?.click()}
              disabled={uploadingAvatar}
              title="Change photo"
              className="absolute -bottom-1 -right-1 w-7 h-7 bg-vibe-600 hover:bg-vibe-700 rounded-full border-2 border-white flex items-center justify-center text-white shadow-md disabled:opacity-60">
              {uploadingAvatar ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Camera className="w-3.5 h-3.5" />}
            </button>
          </div>
          <button onClick={() => { logout(); addToast('Signed out', 'info') }}
            className="flex items-center gap-1.5 text-xs text-gray-500 hover:text-red-500 transition-colors px-3 py-1.5 rounded-lg border border-gray-200">
            <LogOut className="w-3.5 h-3.5" /> Sign Out
          </button>
        </div>
        <h1 className="font-display text-2xl font-bold text-gray-900">{user?.fullName || 'VIBE User'}</h1>
        <p className="text-gray-500 text-sm mt-0.5">@{user?.username || 'vibeuser'} · {user?.role || 'USER'}</p>
        <div className="grid grid-cols-3 gap-3 mt-5">
          {[{ label: 'Messages', value: '—' }, { label: 'Groups', value: '—' }, { label: 'Streak', value: `${user?.streakDays || 0}d 🔥` }].map(s => (
            <div key={s.label} className="card-white p-4 text-center">
              <p className="font-display font-bold text-xl text-gray-900">{s.value}</p>
              <p className="text-xs text-gray-500 mt-0.5">{s.label}</p>
            </div>
          ))}
        </div>
        <div className="mt-4 card-white p-5 cursor-pointer hover:border-vibe-300 transition-colors" onClick={() => setShowWallet(true)}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center">
                <Coins className="w-5 h-5 text-amber-600" />
              </div>
              <div>
                <p className="text-xs text-gray-500 font-medium">VIBE Token Balance</p>
                <p className="font-display font-bold text-xl text-gray-900">{formatTokens(balance)} <span className="text-sm text-gray-400 font-normal">VBT</span></p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-xs text-amber-600 font-medium">≈ {(balance * 10).toLocaleString('fr-CM')} FCFA</p>
              <button className="text-xs text-vibe-600 font-semibold mt-1">Cash Out →</button>
            </div>
          </div>
        </div>
        {!hasPublicAccount ? (
          <div className="mt-4 p-5 rounded-2xl bg-gradient-to-r from-vibe-50 to-sky-50 border border-vibe-200">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-9 h-9 rounded-xl bg-vibe-600 flex items-center justify-center"><Radio className="w-4 h-4 text-white" /></div>
              <div><h3 className="font-display font-bold text-gray-900 text-sm">VIBE for Public</h3><p className="text-xs text-gray-500">Unlock content creation & earning</p></div>
            </div>
            <button onClick={() => { upgradeToCreator(); setSection('public'); addToast('VIBE Public activated! 🎬', 'success') }} className="btn-vibe w-full text-sm py-2.5">
              Activate VIBE Public — Free →
            </button>
          </div>
        ) : (
          <button onClick={() => setSection('public')} className="mt-4 w-full py-3 rounded-2xl bg-gradient-to-r from-vibe-600 to-sky-500 text-white font-display font-bold text-sm">
            Open VIBE for Public 🎬
          </button>
        )}
        <div className="mt-5 space-y-2">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Account</h3>
          {['🌍 Language Preferences', '🔔 Notifications', '🔒 Privacy & Security', '🎨 Appearance', '📱 Connected Devices'].map(item => (
            <div key={item} className="flex items-center gap-3 p-3.5 rounded-xl card-white cursor-pointer hover:border-vibe-200 hover:bg-sky-50 transition-all group">
              <span className="text-lg">{item.split(' ')[0]}</span>
              <span className="text-sm text-gray-700 group-hover:text-vibe-700 flex-1">{item.split(' ').slice(1).join(' ')}</span>
              <ChevronRight className="w-4 h-4 text-gray-400" />
            </div>
          ))}
        </div>
      </div>
      {showWallet && <WalletModal balance={balance} onClose={() => setShowWallet(false)} />}
    </div>
  )
}

function WalletModal({ balance, onClose }) {
  const addToast = useUIStore(s => s.addToast)
  const [step, setStep]     = useState(1)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('MTN_MOMO')
  const [phone, setPhone]   = useState('')
  const cfaAmount = amount ? (+amount * 10).toLocaleString('fr-CM') : '0'
  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-end sm:items-center justify-center" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="bg-white rounded-t-3xl sm:rounded-3xl w-full max-w-md p-6 sm:m-4 shadow-2xl">
        <div className="flex items-center justify-between mb-5">
          <h2 className="font-display font-bold text-gray-900 text-lg">Token Wallet</h2>
          <button onClick={onClose}><X className="w-5 h-5 text-gray-400" /></button>
        </div>
        <div className="bg-gradient-to-r from-vibe-600 to-sky-500 rounded-2xl p-5 mb-5 text-white">
          <p className="text-sm text-white/80 mb-1">Available Balance</p>
          <p className="font-display text-4xl font-extrabold">{formatTokens(balance)} <span className="text-xl font-normal opacity-80">VBT</span></p>
          <p className="text-white/70 text-sm mt-1">≈ {(balance * 10).toLocaleString('fr-CM')} FCFA</p>
        </div>
        <div className="border-t border-surface-border pt-4">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Cash Out</p>
          {step === 1 ? (
            <div className="space-y-3">
              <input type="number" value={amount} onChange={e => setAmount(e.target.value)} placeholder="Min. 500 VBT" className="vibe-input" />
              {amount && <p className="text-xs text-amber-600">≈ {cfaAmount} FCFA</p>}
              <div className="flex gap-2">{[500,1000,2000].map(a => (
                <button key={a} onClick={() => setAmount(String(a))} className={cn('flex-1 py-2 rounded-xl text-xs font-semibold border', amount === String(a) ? 'bg-vibe-50 border-vibe-400 text-vibe-700' : 'border-surface-border text-gray-500')}>{formatTokens(a)}</button>
              ))}</div>
              <div className="grid grid-cols-2 gap-2">{[{id:'MTN_MOMO',label:'MTN MoMo',e:'📱'},{id:'ORANGE_MONEY',label:'Orange Money',e:'🟠'}].map(m => (
                <button key={m.id} onClick={() => setMethod(m.id)} className={cn('p-3 rounded-xl border text-sm font-medium flex items-center gap-2', method === m.id ? 'bg-vibe-50 border-vibe-400 text-vibe-700' : 'border-surface-border text-gray-500')}>{m.e} {m.label}</button>
              ))}</div>
              <button disabled={!amount || +amount < 500} onClick={() => setStep(2)} className="btn-vibe w-full py-3 disabled:opacity-40">Continue →</button>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="bg-sky-50 border border-vibe-200 rounded-xl p-4 text-center">
                <p className="font-display text-3xl font-bold text-gray-900">{formatTokens(+amount)} VBT</p>
                <p className="text-amber-600 text-sm">≈ {cfaAmount} FCFA</p>
              </div>
              <input type="tel" value={phone} onChange={e => setPhone(e.target.value)} placeholder="+237 6XX XXX XXX" className="vibe-input" />
              <div className="flex gap-3">
                <button onClick={() => setStep(1)} className="btn-vibe-outline flex-1 py-3">← Back</button>
                <button onClick={() => { if (!phone) { addToast('Enter phone number', 'error'); return }; onClose(); addToast(`Withdrawal initiated! Sent to ${phone} 🎉`, 'success') }} disabled={!phone}
                  className="flex-1 py-3 rounded-xl bg-amber-500 hover:bg-amber-600 text-white font-bold text-sm disabled:opacity-40">Confirm</button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}