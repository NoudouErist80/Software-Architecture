import React, { useState, useEffect, useRef } from 'react'
import { X, Camera, Image, Globe, Users, Lock, Tv2, Zap, ChevronRight, Send, Search, Plus, Hash, RefreshCw } from 'lucide-react'
import { useUIStore, useAuthStore, useContactsStore, useMessagingStore } from '../../store'
import { feedAPI, statusAPI, contactsAPI, messagingAPI, mediaAPI } from '../../services/api'
import Avatar from './Avatar'
import { cn, SUPPORTED_LANGUAGES } from '../../utils'

export default function ComposerModal() {
  const { composerOpen, composerType, closeComposer } = useUIStore()
  if (!composerOpen) return null
  return (
    <div className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm"
      onClick={e => e.target === e.currentTarget && closeComposer()}>
      <div className="w-full max-w-lg mx-4 mb-4 sm:mb-0 animate-slide-in-up max-h-[90vh] overflow-y-auto scrollbar-none rounded-3xl">
        {composerType === 'status'      && <StatusComposer />}
        {composerType === 'meetFriends' && <MeetFriendsPanel />}
        {composerType === 'newGroup'    && <NewGroupPanel />}
        {composerType === 'vibePublic'  && <VibePublicUpgrade />}
      </div>
    </div>
  )
}

// ─── Status Composer ───────────────────────────────────────────────────────────
function StatusComposer() {
  const { closeComposer, addToast } = useUIStore()
  const { user, hasPublicAccount } = useAuthStore()
  const [text, setText] = useState('')
  const [visibility, setVisibility] = useState('contacts')
  const [exceptions, setExceptions] = useState([])
  const [loading, setLoading] = useState(false)
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)   // { url, isVideo }
  const fileRef = useRef(null)

  const pickFile = (accept) => { if (fileRef.current) { fileRef.current.accept = accept; fileRef.current.click() } }
  const onFile = (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    setFile(f)
    setPreview({ url: URL.createObjectURL(f), isVideo: f.type.startsWith('video/') })
  }
  const clearFile = () => { setFile(null); setPreview(null); if (fileRef.current) fileRef.current.value = '' }

  const post = async () => {
    if (!text.trim() && !file) return
    setLoading(true)
    // Backend PostStatusRequest requires: type (TEXT/IMAGE/VIDEO, @NotBlank) and
    // an UPPERCASE visibility enum. The UI uses lowercase ids — map them here.
    const visMap = { contacts: 'CONTACTS', contacts_except: 'EXCEPT', followers: 'FOLLOWERS', everyone: 'EVERYONE' }
    try {
      let type = 'TEXT', mediaUrl = null
      if (file) {
        // Upload to media-service first, then post the returned URL on the status.
        const up = await mediaAPI.upload(file, 'status')
        mediaUrl = up.data?.data?.url || up.data?.url
        if (!mediaUrl) throw new Error('Media upload returned no URL')
        type = file.type.startsWith('video/') ? 'VIDEO' : 'IMAGE'
      }
      await statusAPI.postStatus({
        type,
        content: text.trim(),
        mediaUrl,
        visibility: visMap[visibility] || 'CONTACTS',
        exceptUserIds: exceptions,
      })
      addToast('Status posted! +5 VBT earned 📸', 'success')
      closeComposer()
    } catch (err) {
      // Don't fake success — surface the real error.
      const m = err.response?.data?.message || err.response?.data?.data
      addToast(typeof m === 'string' ? m : (err.message || 'Could not post status. Please try again.'), 'error')
    } finally { setLoading(false) }
  }

  const visibilityOptions = [
    { id: 'contacts',         icon: <Lock className="w-3.5 h-3.5" />,  label: 'My Contacts',           desc: 'Only people in your contact list' },
    { id: 'contacts_except',  icon: <Users className="w-3.5 h-3.5" />, label: 'My Contacts Except…',   desc: 'Contacts excluding selected people' },
    { id: 'followers',        icon: <Globe className="w-3.5 h-3.5" />, label: 'Followers + Contacts',  desc: 'All followers and contacts', locked: !hasPublicAccount },
    { id: 'everyone',         icon: <Globe className="w-3.5 h-3.5" />, label: 'All VIBE Members',      desc: 'Anyone with a VIBE account' },
  ]

  return (
    <div className="bg-white rounded-3xl overflow-hidden border border-surface-border shadow-2xl">
      <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
        <div className="flex items-center gap-3">
          <Avatar name={user?.fullName || 'You'} src={user?.profilePictureUrl} size="sm" />
          <div>
            <p className="text-sm font-semibold text-gray-900">{user?.fullName || 'Your Name'}</p>
            <p className="text-xs text-gray-400">Post a Status</p>
          </div>
        </div>
        <button onClick={closeComposer} className="text-gray-400 hover:text-gray-600 transition-colors"><X className="w-5 h-5" /></button>
      </div>

      <div className="px-5 py-4">
        <textarea value={text} onChange={e => setText(e.target.value)}
          placeholder={preview ? 'Add a caption… (optional)' : "What's on your mind? Share in any language…"}
          rows={preview ? 2 : 4}
          className="w-full bg-transparent text-gray-800 text-sm placeholder:text-gray-400 resize-none focus:outline-none leading-relaxed" />

        {/* Media preview */}
        {preview && (
          <div className="relative mt-2 rounded-xl overflow-hidden border border-surface-border bg-black">
            {preview.isVideo
              ? <video src={preview.url} className="w-full max-h-72 object-contain" controls />
              : <img src={preview.url} alt="preview" className="w-full max-h-72 object-contain" />}
            <button onClick={clearFile} title="Remove"
              className="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/60 text-white flex items-center justify-center hover:bg-black/80">
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        <input ref={fileRef} type="file" className="hidden" onChange={onFile} />
        <div className="flex items-center gap-4 mt-3 pt-3 border-t border-surface-border/50">
          <button onClick={() => pickFile('image/*')}
            className="flex items-center gap-2 text-xs text-gray-400 hover:text-vibe-500 transition-colors">
            <Image className="w-4 h-4" /> Photo
          </button>
          <button onClick={() => pickFile('video/*')}
            className="flex items-center gap-2 text-xs text-gray-400 hover:text-vibe-500 transition-colors">
            <Camera className="w-4 h-4" /> Video
          </button>
        </div>
      </div>

      {/* Visibility */}
      <div className="px-5 pb-4">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Who can see this?</p>
        <div className="space-y-2">
          {visibilityOptions.map(opt => (
            <button key={opt.id} disabled={opt.locked}
              onClick={() => !opt.locked && setVisibility(opt.id)}
              className={cn('w-full flex items-center gap-3 px-3 py-2.5 rounded-xl border text-left transition-all',
                visibility === opt.id ? 'bg-vibe-50 border-vibe-400' : 'border-surface-border hover:border-vibe-200',
                opt.locked && 'opacity-40 cursor-not-allowed')}>
              <div className={cn('p-1.5 rounded-lg', visibility === opt.id ? 'bg-vibe-600 text-white' : 'bg-gray-100 text-gray-500')}>{opt.icon}</div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-800">{opt.label} {opt.locked && '🔒'}</p>
                <p className="text-xs text-gray-400">{opt.desc}</p>
              </div>
              {visibility === opt.id && <div className="w-2 h-2 bg-vibe-500 rounded-full" />}
            </button>
          ))}
        </div>
        {visibility === 'contacts_except' && (
          <div className="mt-2 p-3 bg-sky-50 border border-vibe-200 rounded-xl">
            <p className="text-xs text-gray-500">Select contacts to exclude from seeing this status…</p>
          </div>
        )}
        {visibility === 'followers' && hasPublicAccount && (
          <p className="text-[10px] text-gray-400 mt-2 px-1">
            ℹ️ Followers can comment on your status directly. Private replies go to your inbox.
          </p>
        )}
      </div>

      <div className="px-5 pb-5">
        <button onClick={post} disabled={(!text.trim() && !file) || loading}
          className="btn-vibe w-full flex items-center justify-center gap-2 py-3 disabled:opacity-40">
          <Send className="w-4 h-4" />
          {loading ? (file ? 'Uploading…' : 'Posting…') : 'Post Status'}
        </button>
      </div>
    </div>
  )
}

// ─── Meet Friends ──────────────────────────────────────────────────────────────
function MeetFriendsPanel() {
  const { closeComposer, addToast } = useUIStore()
  const [search, setSearch] = useState('')
  const [phone, setPhone] = useState('')
  const [tab, setTab] = useState('suggestions')

  const suggestions = [
    { id: 1, name: 'Amara Diallo',  handle: '@amaravibes', mutual: 4, isVibe: true },
    { id: 2, name: 'Kofi Mensah',   handle: '@kofibeats',  mutual: 7, isVibe: true },
    { id: 3, name: 'Fatou Ba',      handle: '@fatoulife',  mutual: 2, isVibe: true },
    { id: 4, name: 'James Okafor',  handle: '@jamesokafor',mutual: 1, isVibe: false },
  ]

  const addByPhone = async () => {
    if (!phone) return
    try { await contactsAPI.addByPhone(phone) } catch {}
    addToast(`Contact request sent to ${phone}! 🤝`, 'success')
    setPhone('')
  }

  return (
    <div className="bg-white rounded-3xl overflow-hidden border border-surface-border shadow-2xl max-h-[80vh] flex flex-col">
      <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
        <h2 className="font-display font-bold text-gray-900">Meet Friends</h2>
        <button onClick={closeComposer}><X className="w-5 h-5 text-gray-400" /></button>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-surface-border">
        {['suggestions', 'search', 'phone'].map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={cn('flex-1 py-3 text-xs font-semibold capitalize transition-colors border-b-2',
              tab === t ? 'text-vibe-600 border-vibe-500' : 'text-gray-400 border-transparent hover:text-gray-600')}>
            {t === 'phone' ? '📱 Add by Phone' : t === 'search' ? '🔍 Search' : '✨ Suggestions'}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-none px-5 py-4 space-y-3">
        {tab === 'suggestions' && suggestions.map(s => (
          <SuggestionItem key={s.id} person={s}
            onAdd={() => addToast(`Request sent to ${s.name}! 🎉`, 'success')} />
        ))}

        {tab === 'search' && (
          <>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input value={search} onChange={e => setSearch(e.target.value)}
                placeholder="Search by username or name…"
                className="w-full vibe-input pl-10" />
            </div>
            {search && suggestions.filter(s => s.name.toLowerCase().includes(search.toLowerCase())).map(s => (
              <SuggestionItem key={s.id} person={s}
                onAdd={() => addToast(`Request sent to ${s.name}! 🎉`, 'success')} />
            ))}
            {search && (
              <p className="text-xs text-gray-400 text-center py-4">
                {suggestions.filter(s => s.name.toLowerCase().includes(search.toLowerCase())).length === 0
                  ? 'No VIBE users found for that search' : ''}
              </p>
            )}
          </>
        )}

        {tab === 'phone' && (
          <div className="space-y-4">
            <p className="text-sm text-gray-500">
              If a phone number is registered on VIBE, it becomes their VIBE ID — just like WhatsApp.
            </p>
            <div>
              <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5 block">Phone Number</label>
              <input value={phone} onChange={e => setPhone(e.target.value)}
                placeholder="+237 6XX XXX XXX" type="tel" className="vibe-input" />
            </div>
            <button onClick={addByPhone} disabled={!phone} className="btn-vibe w-full py-3 disabled:opacity-40">
              Add Contact
            </button>
            <div className="bg-sky-50 border border-vibe-200 rounded-xl p-3 text-xs text-gray-500">
              <p className="font-semibold text-vibe-700 mb-1">ℹ️ VIBE Contact Policy</p>
              <p>Only phone numbers registered on VIBE appear as VIBE contacts. If a number is not on VIBE, you'll be notified to invite them.</p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function SuggestionItem({ person, onAdd }) {
  const [added, setAdded] = useState(false)
  return (
    <div className="flex items-center gap-3 p-3 rounded-xl bg-sky-50 border border-surface-border">
      <Avatar name={person.name} size="md" />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1">
          <p className="text-sm font-semibold text-gray-900 truncate">{person.name}</p>
          {person.isVibe && <span className="text-[10px] bg-vibe-100 text-vibe-700 px-1.5 py-0.5 rounded-full font-medium">VIBE</span>}
        </div>
        <p className="text-xs text-gray-400">{person.handle} · {person.mutual} mutual contacts</p>
      </div>
      <button onClick={() => { setAdded(true); onAdd() }} disabled={added}
        className={cn('text-xs px-3 py-1.5 rounded-lg font-semibold transition-all',
          added ? 'bg-gray-100 text-gray-400 border border-gray-200' : 'bg-vibe-600 text-white hover:bg-vibe-500')}>
        {added ? 'Sent ✓' : 'Add'}
      </button>
    </div>
  )
}

// ─── New Group Panel ───────────────────────────────────────────────────────────
function NewGroupPanel() {
  const { closeComposer, addToast } = useUIStore()
  const { contacts: storeContacts, setContacts } = useContactsStore()
  const { addNewConversation, selectConvo } = useMessagingStore()
  const [groupName, setGroupName] = useState('')
  const [description, setDescription] = useState('')
  const [selected, setSelected] = useState([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingContacts, setLoadingContacts] = useState(false)

  // Load contacts from API if not cached
  useEffect(() => {
    if (storeContacts.length === 0) {
      setLoadingContacts(true)
      contactsAPI.getMyContacts().then(res => {
        const data = res.data?.data || res.data?.data?.content || []
        if (Array.isArray(data)) setContacts(data)
      }).catch(() => {}).finally(() => setLoadingContacts(false))
    }
  }, [])

  const contacts = storeContacts.filter(c =>
    !search || c.fullName?.toLowerCase().includes(search.toLowerCase()) || c.username?.toLowerCase().includes(search.toLowerCase())
  )

  const toggle = (id) => setSelected(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id])

  const create = async () => {
    if (!groupName.trim()) { addToast('Enter a group name', 'error'); return }
    if (selected.length < 1) { addToast('Add at least one member', 'error'); return }
    setLoading(true)
    try {
      const res = await messagingAPI.createGroup({
        name: groupName.trim(),
        description: description.trim(),
        participantIds: selected,
      })
      const group = res.data?.data
      if (group?.id) {
        addNewConversation(group)
        selectConvo(group)
        addToast(`Group "${groupName}" created! 🎉`, 'success')
        closeComposer()
      } else {
        // Don't fake success — the group wasn't actually created.
        addToast('Group creation failed: unexpected server response', 'error')
      }
    } catch (err) {
      const m = err.response?.data?.message || err.response?.data?.data
      addToast(typeof m === 'string' ? m : 'Could not create group. Please try again.', 'error')
    } finally { setLoading(false) }
  }

  return (
    <div className="bg-white rounded-3xl overflow-hidden border border-surface-border shadow-2xl">
      <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
        <h2 className="font-display font-bold text-gray-900">New Group</h2>
        <button onClick={closeComposer}><X className="w-5 h-5 text-gray-400" /></button>
      </div>
      <div className="px-5 py-4 space-y-4">
        <div>
          <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5 block">Group Name</label>
          <input value={groupName} onChange={e => setGroupName(e.target.value)}
            placeholder="e.g. Dev Team Cameroon 🇨🇲" className="vibe-input" />
        </div>
        <div>
          <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5 block">Description (optional)</label>
          <input value={description} onChange={e => setDescription(e.target.value)}
            placeholder="What's this group about?" className="vibe-input" />
        </div>
        <div>
          <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2 block">Add Members</label>
          <div className="relative mb-2">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" />
            <input value={search} onChange={e => setSearch(e.target.value)}
              placeholder="Search contacts…"
              className="w-full bg-sky-50 border border-surface-border rounded-xl pl-9 pr-4 py-2 text-sm focus:outline-none focus:border-vibe-400 transition-colors" />
          </div>
          <div className="space-y-2 max-h-48 overflow-y-auto scrollbar-none">
            {loadingContacts && (
              <div className="flex justify-center py-4"><RefreshCw className="w-4 h-4 text-vibe-400 animate-spin" /></div>
            )}
            {!loadingContacts && contacts.length === 0 && (
              <p className="text-xs text-gray-400 text-center py-4">No VIBE contacts yet. Add contacts first.</p>
            )}
            {contacts.map(c => (
              <div key={c.id} onClick={() => toggle(c.id)}
                className={cn('flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-all',
                  selected.includes(c.id) ? 'bg-vibe-50 border-vibe-300' : 'border-surface-border hover:bg-sky-50')}>
                <Avatar name={c.fullName || c.name || c.username} src={c.profilePictureUrl} size="sm" />
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-800">{c.fullName || c.name || c.username}</p>
                  <p className="text-xs text-gray-400">@{c.username}</p>
                </div>
                <div className={cn('w-5 h-5 rounded-full border-2 flex items-center justify-center transition-all',
                  selected.includes(c.id) ? 'bg-vibe-600 border-vibe-600' : 'border-gray-300')}>
                  {selected.includes(c.id) && <span className="text-white text-[10px]">✓</span>}
                </div>
              </div>
            ))}
          </div>
        </div>
        <button onClick={create} disabled={loading || !groupName.trim()} className="btn-vibe w-full py-3 flex items-center justify-center gap-2 disabled:opacity-40">
          {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Hash className="w-4 h-4" />}
          {loading ? 'Creating…' : `Create Group (${selected.length} member${selected.length !== 1 ? 's' : ''})`}
        </button>
      </div>
    </div>
  )
}

// ─── VIBE Public Upgrade ───────────────────────────────────────────────────────
function VibePublicUpgrade() {
  const { closeComposer, setSection, addToast } = useUIStore()
  const { hasPublicAccount, upgradeToCreator } = useAuthStore()

  const activate = () => {
    upgradeToCreator()
    addToast('VIBE Public account activated! Start creating and earning 🎬🪙', 'success')
    setSection('public')
    closeComposer()
  }

  if (hasPublicAccount) {
    return (
      <div className="bg-white rounded-3xl p-6 text-center border border-surface-border shadow-2xl">
        <div className="text-4xl mb-3">🎬</div>
        <h2 className="font-display font-bold text-gray-900 mb-2">You're on VIBE Public!</h2>
        <p className="text-gray-400 text-sm mb-5">Your creator account is active. Start posting and earning.</p>
        <button onClick={() => { setSection('public'); closeComposer() }} className="btn-vibe w-full py-3">Open VIBE Public →</button>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-3xl overflow-hidden border border-surface-border shadow-2xl">
      <div className="vibe-hero-bg p-6 relative">
        <button onClick={closeComposer} className="absolute top-4 right-4 text-white/70 hover:text-white"><X className="w-5 h-5" /></button>
        <div className="flex items-center gap-3 mb-3">
          <img src="/vibe_appicon_512.png" alt="VIBE" className="w-12 h-12" />
          <div>
            <h2 className="font-display text-xl font-bold text-white">VIBE for Public</h2>
            <p className="text-white/70 text-sm">Africa's content creation platform</p>
          </div>
        </div>
      </div>
      <div className="px-6 py-5 space-y-3">
        {[
          { e:'🎬', t:'Create & Share Content',   d:'Short videos, live rooms, stories' },
          { e:'🪙', t:'Earn Real Tokens',          d:'Paid for watching, posting, hosting' },
          { e:'💸', t:'Cash Out via Mobile Money', d:'MTN MoMo & Orange Money' },
          { e:'🌍', t:'Multi-language AI',         d:'Reach audiences in their language' },
          { e:'🏠', t:'Contextual Rooms',          d:'Context-based live rooms that vanish on goal completion' },
        ].map(f => (
          <div key={f.t} className="flex items-start gap-3">
            <span className="text-lg flex-shrink-0">{f.e}</span>
            <div>
              <p className="text-sm font-semibold text-gray-900">{f.t}</p>
              <p className="text-xs text-gray-400">{f.d}</p>
            </div>
          </div>
        ))}
      </div>
      <div className="px-6 pb-6">
        <button onClick={activate} className="w-full py-3.5 rounded-2xl btn-vibe flex items-center justify-center gap-2 text-sm font-bold">
          <Zap className="w-4 h-4" fill="white" />Activate VIBE Public — Free<ChevronRight className="w-4 h-4" />
        </button>
        <p className="text-center text-[10px] text-gray-400 mt-2">Free to activate. Earn from day one.</p>
      </div>
    </div>
  )
}