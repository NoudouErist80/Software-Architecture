import React, { useState, useEffect } from 'react'
import {
  Heart, MessageCircle, Share2, Bookmark, Volume2, VolumeX,
  Plus, Search, Bell, User, Home, ChevronUp, ChevronDown,
  X, Coins, Radio, Users, ArrowLeft, Star, Crown, BarChart2,
  Wallet, Send, BadgeCheck, Globe, Play, TrendingUp, Clock,
  Zap, Shield
} from 'lucide-react'
import { useAuthStore, useUIStore, useFeedStore, useWalletStore } from '../../store'
import { feedAPI, rewardsAPI } from '../../services/api'
import Avatar from '../shared/Avatar'
import { cn, formatTokens, tokensToCFA, timeAgo } from '../../utils'

// ─── Root ─────────────────────────────────────────────────────────────────────
export default function VibePublic() {
  const [activePage, setActivePage] = useState('feed')
  const { setSection } = useUIStore()

  return (
    <div className="flex flex-col h-full w-full bg-black overflow-hidden relative">
      <PublicTopBar activePage={activePage} setActivePage={setActivePage} goToMessenger={() => setSection('messenger')} />
      <div className="flex-1 overflow-hidden">
        {activePage === 'feed'     && <VideoFeed />}
        {activePage === 'discover' && <DiscoverPage />}
        {activePage === 'rooms'    && <RoomsPage />}
        {activePage === 'wallet'   && <WalletPage />}
        {activePage === 'profile'  && <PublicProfilePage />}
      </div>
      <PublicBottomNav activePage={activePage} setActivePage={setActivePage} />
    </div>
  )
}

// ─── Top Bar ───────────────────────────────────────────────────────────────────
function PublicTopBar({ activePage, setActivePage, goToMessenger }) {
  const balance = useWalletStore(s => s.balance) || 2840
  return (
    <div className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-4 pt-4 pb-2">
      <button onClick={goToMessenger} className="flex items-center gap-1.5 text-white/70 hover:text-white transition-colors text-xs bg-black/30 backdrop-blur-sm px-3 py-1.5 rounded-full">
        <ArrowLeft className="w-3.5 h-3.5" /><span>Messenger</span>
      </button>
      {activePage === 'feed' && (
        <div className="flex items-center gap-6">
          {['Following','For You'].map((tab, i) => (
            <button key={tab} className={cn('font-display font-bold text-sm transition-all',
              i === 1 ? 'text-white border-b-2 border-white pb-0.5' : 'text-white/50')}>
              {tab}
            </button>
          ))}
        </div>
      )}
      {activePage !== 'feed' && <h1 className="font-display font-bold text-white capitalize">{activePage}</h1>}
      <div className="token-badge bg-black/40 border-amber-400/50 text-amber-300">
        <Coins className="w-3 h-3" /><span>{formatTokens(balance)}</span>
      </div>
    </div>
  )
}

// ─── Video Feed ────────────────────────────────────────────────────────────────
// ─── Video Feed ────────────────────────────────────────────────────────────────
function VideoFeed() {
  const [posts, setPosts] = useState([])
  const [currentIndex, setCurrentIndex] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [watchStart, setWatchStart] = useState(Date.now())
  const addToast = useUIStore(s => s.addToast)
  const addTokens = useWalletStore(s => s.addTokens)

  const loadPosts = async (p = 0) => {
    try {
      const res = await feedAPI.getFeed(p)
      const data = res.data?.data?.content || res.data?.data || []
      if (Array.isArray(data) && data.length > 0) {
        setPosts(prev => p === 0 ? data : [...prev, ...data])
      } else if (p === 0) {
        // Fallback demo posts if backend returns empty
        setPosts(DEMO_POSTS)
      }
    } catch {
      if (p === 0) setPosts(DEMO_POSTS)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadPosts(0) }, [])

  const goNext = async () => {
    const watched = Math.round((Date.now() - watchStart) / 1000)
    const curr = posts[currentIndex]
    if (curr?.id && watched >= 5) {
      try { await feedAPI.watchVideo(curr.id, watched) } catch {}
      addTokens(2)
      addToast('+2 VBT earned! 🪙', 'success')
    }
    if (currentIndex >= posts.length - 2) {
      const nextPage = page + 1
      setPage(nextPage)
      loadPosts(nextPage)
    }
    setCurrentIndex(i => Math.min(i + 1, posts.length - 1))
    setWatchStart(Date.now())
  }
  const goPrev = () => {
    setCurrentIndex(i => Math.max(i - 1, 0))
    setWatchStart(Date.now())
  }

  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'ArrowDown') goNext()
      if (e.key === 'ArrowUp') goPrev()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [currentIndex, posts])

  const handleWheel = (e) => {
    if (e.deltaY > 50) goNext()
    if (e.deltaY < -50) goPrev()
  }

  if (loading) return (
    <div className="relative h-full w-full flex items-center justify-center bg-black">
      <div className="w-8 h-8 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />
    </div>
  )

  return (
    <div className="relative h-full w-full overflow-hidden" onWheel={handleWheel}>
      {posts.map((post, i) => (
        <div key={post.id || i} className="absolute inset-0 transition-transform duration-500 ease-in-out"
          style={{ transform: `translateY(${(i - currentIndex) * 100}%)` }}>
          <VideoCard post={post} active={i === currentIndex} />
        </div>
      ))}
      <div className="absolute right-3 top-1/2 -translate-y-1/2 flex flex-col gap-3 z-10">
        <button onClick={goPrev} disabled={currentIndex === 0}
          className="w-8 h-8 rounded-full bg-white/10 backdrop-blur-sm flex items-center justify-center text-white disabled:opacity-20 hover:bg-white/20 transition-all">
          <ChevronUp className="w-5 h-5" />
        </button>
        <button onClick={goNext} disabled={currentIndex === posts.length - 1}
          className="w-8 h-8 rounded-full bg-white/10 backdrop-blur-sm flex items-center justify-center text-white disabled:opacity-20 hover:bg-white/20 transition-all">
          <ChevronDown className="w-5 h-5" />
        </button>
      </div>
      <div className="absolute right-1.5 top-1/2 -translate-y-1/2 flex flex-col gap-1.5 z-10">
        {posts.map((_, i) => (
          <div key={i} className={cn('rounded-full transition-all duration-300 mx-auto',
            i === currentIndex ? 'w-1.5 h-4 bg-white' : 'w-1 h-1 bg-white/30')} />
        ))}
      </div>
    </div>
  )
}

function VideoCard({ post, active }) {
  const [muted, setMuted] = useState(true)
  const [liked, setLiked] = useState(false)
  const [likes, setLikes] = useState(post.likes)
  const [saved, setSaved] = useState(false)
  const [showComments, setShowComments] = useState(false)
  const addToast = useUIStore(s => s.addToast)
  const addTokens = useWalletStore(s => s.addTokens)
  const { user } = useAuthStore()

  const handleLike = () => {
    setLiked(l => !l)
    setLikes(l => liked ? l - 1 : l + 1)
    if (!liked) { addTokens(1); addToast('+1 VBT earned! 🪙', 'success') }
  }

  return (
    <div className="relative h-full w-full overflow-hidden" style={{ background: post.gradient }}>
      <div className="absolute inset-0 flex items-center justify-center opacity-10">
        <span className="text-[120px] select-none">{post.emoji}</span>
      </div>
      <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-transparent to-black/20" />

      {active && (
        <div className="absolute top-14 left-4 flex items-center gap-2 bg-black/30 backdrop-blur-sm rounded-full px-3 py-1.5 z-10 animate-fade-in">
          <span className="w-2 h-2 bg-amber-400 rounded-full animate-pulse" />
          <span className="text-xs text-amber-300 font-medium">Watching · earning tokens</span>
        </div>
      )}

      {/* Right action bar */}
      <div className="absolute right-3 bottom-28 flex flex-col items-center gap-4 z-10">
        <div className="flex flex-col items-center gap-1">
          <div className="w-11 h-11 rounded-full border-2 border-white/60 overflow-hidden cursor-pointer">
            <Avatar name={post.creator} size="md" />
          </div>
          <div className="w-5 h-5 bg-vibe-500 rounded-full flex items-center justify-center -mt-2 cursor-pointer hover:bg-vibe-400 transition-colors">
            <Plus className="w-3 h-3 text-white" />
          </div>
        </div>
        <ActionCol icon={<Heart className={cn('w-7 h-7 transition-all', liked ? 'text-red-500 fill-red-500 scale-110' : 'text-white')} />}
          count={formatTokens(likes)} onClick={handleLike} />
        <ActionCol icon={<MessageCircle className="w-7 h-7 text-white" />}
          count={formatTokens(post.comments)} onClick={() => setShowComments(true)} />
        <ActionCol icon={<Bookmark className={cn('w-7 h-7 transition-all', saved ? 'text-amber-400 fill-amber-400' : 'text-white')} />}
          count={formatTokens(post.saves)}
          onClick={() => { setSaved(s => !s); if (!saved) { addTokens(1); addToast('+1 VBT earned! 🪙', 'success') } }} />
        <ActionCol icon={<Share2 className="w-7 h-7 text-white" />}
          count={formatTokens(post.shares)} onClick={() => addToast('Share link copied!', 'info')} />
        <button onClick={() => setMuted(m => !m)}
          className="w-9 h-9 rounded-full bg-white/15 backdrop-blur-sm flex items-center justify-center">
          {muted ? <VolumeX className="w-4 h-4 text-white" /> : <Volume2 className="w-4 h-4 text-white" />}
        </button>
      </div>

      {/* Bottom info */}
      <div className="absolute bottom-20 left-4 right-16 z-10">
        <div className="flex items-center gap-2 mb-1.5">
          <span className="font-display font-bold text-white text-base">@{post.creatorHandle}</span>
          {post.verified && <BadgeCheck className="w-4 h-4 text-sky-300" />}
        </div>
        <p className="text-white/90 text-sm leading-relaxed mb-2 line-clamp-2">{post.caption}</p>
        <div className="flex flex-wrap gap-1.5 mb-2">
          {post.tags.map(tag => <span key={tag} className="text-xs text-sky-300 font-medium">#{tag}</span>)}
        </div>
        <div className="flex items-center gap-2">
          <div className="bg-white/15 backdrop-blur-sm rounded-full px-3 py-1.5 text-xs text-white/80 flex items-center gap-1.5">
            🎵 {post.music}
          </div>
          <div className="bg-white/15 backdrop-blur-sm rounded-full px-3 py-1.5 text-xs text-white/80 flex items-center gap-1.5">
            <Globe className="w-3 h-3" />{post.language}
          </div>
        </div>
      </div>

      {showComments && <CommentsPanel post={post} onClose={() => setShowComments(false)} user={user} />}
    </div>
  )
}

function ActionCol({ icon, count, onClick }) {
  return (
    <button onClick={onClick} className="flex flex-col items-center gap-1 group">
      <div className="w-12 h-12 flex items-center justify-center group-active:scale-90 transition-transform">{icon}</div>
      <span className="text-white text-xs font-semibold">{count}</span>
    </button>
  )
}

function CommentsPanel({ post, onClose, user }) {
  const [comment, setComment] = useState('')
  const addToast = useUIStore(s => s.addToast)
  return (
    <div className="absolute inset-x-0 bottom-0 z-20 bg-gray-900/95 backdrop-blur-xl rounded-t-3xl border-t border-white/10 animate-slide-in-up max-h-[65%] flex flex-col">
      <div className="flex items-center justify-between px-5 py-4 border-b border-white/10">
        <h3 className="font-display font-bold text-white">{formatTokens(post.comments)} Comments</h3>
        <button onClick={onClose} className="text-white/60 hover:text-white"><X className="w-5 h-5" /></button>
      </div>
      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4 scrollbar-none">
        {DEMO_COMMENTS.map(c => (
          <div key={c.id} className="flex gap-3">
            <Avatar name={c.name} size="sm" className="flex-shrink-0" />
            <div>
              <span className="text-xs font-semibold text-sky-400">@{c.handle}</span>
              <p className="text-sm text-white/90 mt-0.5">{c.text}</p>
              <div className="flex items-center gap-4 mt-1">
                <span className="text-xs text-white/40">{timeAgo(c.time)}</span>
                <button className="text-xs text-white/40 hover:text-white">Reply</button>
                <button className="flex items-center gap-1 text-xs text-white/40 hover:text-red-400">
                  <Heart className="w-3 h-3" />{c.likes}
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="px-4 py-3 border-t border-white/10">
        <div className="flex gap-3 items-center">
          <Avatar name={user?.fullName || 'Me'} size="sm" />
          <div className="flex-1 bg-white/10 rounded-xl flex items-center gap-2 px-4 py-2.5">
            <input value={comment} onChange={e => setComment(e.target.value)}
              placeholder="Add a comment…"
              className="flex-1 bg-transparent text-sm text-white placeholder:text-white/40 focus:outline-none" />
            <button onClick={() => { addToast('Comment posted! +1 VBT', 'success'); setComment('') }}
              className="text-sky-400 hover:text-sky-300"><Send className="w-4 h-4" /></button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Discover Page ─────────────────────────────────────────────────────────────
// ─── Discover Page ─────────────────────────────────────────────────────────────
function DiscoverPage() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [searching, setSearching] = useState(false)
  const [followState, setFollowState] = useState({})
  const addToast = useUIStore(s => s.addToast)

  useEffect(() => {
    if (!query.trim()) { setResults([]); return }
    const timer = setTimeout(async () => {
      setSearching(true)
      try {
        const res = await feedAPI.searchPosts(query)
        setResults(res.data?.data || [])
      } catch { setResults([]) }
      finally { setSearching(false) }
    }, 400)
    return () => clearTimeout(timer)
  }, [query])

  const handleHashtag = async (tag) => {
    setQuery(tag)
    setSearching(true)
    try {
      const t = tag.replace('#','')
      const res = await feedAPI.getPostsByHashtag(t)
      setResults(res.data?.data || [])
    } catch { setResults([]) }
    finally { setSearching(false) }
  }

  const toggle = async (userId, name) => {
    const isFollowing = followState[userId]
    try {
      if (isFollowing) { await feedAPI.unfollow(userId); addToast('Unfollowed ' + name, 'info') }
      else             { await feedAPI.follow(userId);   addToast('Now following ' + name + '!', 'success') }
      setFollowState(s => ({ ...s, [userId]: !isFollowing }))
    } catch { addToast('Action failed', 'error') }
  }

  return (
    <div className="h-full overflow-y-auto scrollbar-none pt-14 pb-20 px-4 bg-black">
      <div className="relative mb-5">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
        <input value={query} onChange={e => setQuery(e.target.value)}
          placeholder="Search posts, hashtags…"
          className="w-full bg-white/10 border border-white/20 rounded-2xl pl-11 pr-4 py-3 text-sm text-white placeholder:text-white/40 focus:outline-none focus:border-white/40" />
        {searching && <div className="absolute right-4 top-1/2 -translate-y-1/2 w-4 h-4 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />}
      </div>

      {results.length > 0 && (
        <section className="mb-6">
          <h2 className="font-display font-bold text-white mb-3">Search Results ({results.length})</h2>
          <div className="space-y-3">
            {results.map((p, i) => (
              <div key={p.id || i} className="bg-white/8 border border-white/10 rounded-xl p-3">
                <p className="text-sm text-white font-medium">{p.caption || 'No caption'}</p>
                {p.hashtags?.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-1">
                    {p.hashtags.map(h => (
                      <span key={h} className="text-xs text-sky-400">#{h}</span>
                    ))}
                  </div>
                )}
                <span className="text-xs text-white/40 mt-1 block">{p.authorUsername || 'Unknown'}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {!query && (
        <>
          <section className="mb-6">
            <h2 className="font-display font-bold text-white mb-3 flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-red-400" />Trending
            </h2>
            <div className="flex flex-wrap gap-2">
              {['#VibeChallenge','#AfricaRises','#CamerounTalent','#Lagos2025','#PidginVibes','#GhanaHits','#VIBE'].map(tag => (
                <button key={tag} onClick={() => handleHashtag(tag)}
                  className="px-3 py-1.5 rounded-xl bg-white/10 border border-white/20 text-sm text-white/80 hover:text-white hover:border-white/40 transition-all">
                  {tag}
                </button>
              ))}
            </div>
          </section>

          <section className="mb-6">
            <h2 className="font-display font-bold text-white mb-3 flex items-center gap-2">
              <Crown className="w-4 h-4 text-amber-400" />Top Creators
            </h2>
            <div className="flex flex-col gap-3">
              {DEMO_CREATORS.map(c => (
                <div key={c.id} className="flex items-center gap-3 bg-white/8 border border-white/10 rounded-xl p-3">
                  <Avatar name={c.name} size="md" />
                  <div className="flex-1">
                    <div className="flex items-center gap-1">
                      <span className="text-sm font-bold text-white">{c.name}</span>
                      {c.verified && <BadgeCheck className="w-3.5 h-3.5 text-sky-400" />}
                    </div>
                    <span className="text-xs text-white/50">{formatTokens(c.followers)} followers</span>
                  </div>
                  <button onClick={() => toggle(c.id, c.name)}
                    className={cn('text-xs px-3 py-1.5 rounded-lg font-semibold transition-all',
                      followState[c.id] ? 'bg-white/10 border border-white/20 text-white/60' : 'bg-sky-500 text-white hover:bg-sky-400')}>
                    {followState[c.id] ? 'Following' : 'Follow'}
                  </button>
                </div>
              ))}
            </div>
          </section>

          <section>
            <h2 className="font-display font-bold text-white mb-3">Categories</h2>
            <div className="grid grid-cols-3 gap-3">
              {[['🎵','Music'],['🎭','Comedy'],['📰','News'],['🍲','Food'],['⚽','Sports'],['💃','Dance'],['🏫','Education'],['💻','Tech'],['🌍','Culture']].map(([e,n]) => (
                <div key={n} className="bg-white/8 border border-white/10 rounded-xl p-3 text-center cursor-pointer hover:bg-white/15 transition-all">
                  <span className="text-2xl">{e}</span>
                  <p className="text-xs text-white/60 mt-2">{n}</p>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  )
}

// ─── Rooms Page (Contextual Rooms) ─────────────────────────────────────────────
function RoomsPage() {
  const addToast = useUIStore(s => s.addToast)

  return (
    <div className="h-full overflow-y-auto scrollbar-none pt-14 pb-20 px-4 bg-black">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="font-display font-bold text-white">Contextual Rooms</h1>
          <p className="text-white/40 text-xs mt-0.5">Join a vibe, not a performance</p>
        </div>
        <button onClick={() => addToast('Create a contextual room — earn +20 VBT/min! 🎙️', 'success')}
          className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-sky-500 text-white text-xs font-bold hover:bg-sky-400 transition-colors">
          <Plus className="w-3.5 h-3.5" />Create Room
        </button>
      </div>

      {/* How rooms differ */}
      <div className="mb-5 p-4 bg-white/8 border border-white/10 rounded-2xl">
        <p className="text-xs text-white/60 font-semibold uppercase tracking-wider mb-2">What makes VIBE Rooms different</p>
        <p className="text-sm text-white/80 leading-relaxed">
          Traditional lives are about <span className="text-sky-300">watching someone</span>. VIBE Contextual Rooms are about
          <span className="text-sky-300"> being somewhere together</span>. No performance pressure — the shared context brings everyone in.
          Rooms vanish automatically when their goal is achieved or deadline passes.
        </p>
      </div>

      <section className="mb-5">
        <h2 className="text-xs font-bold text-white/50 uppercase tracking-wider mb-3 flex items-center gap-2">
          <span className="w-2 h-2 bg-red-500 rounded-full animate-pulse" />Live Now
        </h2>
        <div className="space-y-3">
          {DEMO_ROOMS.filter(r => r.live).map(r => <RoomCard key={r.id} room={r} />)}
        </div>
      </section>

      <section>
        <h2 className="text-xs font-bold text-white/50 uppercase tracking-wider mb-3">Scheduled</h2>
        <div className="space-y-3">
          {DEMO_ROOMS.filter(r => !r.live).map(r => <RoomCard key={r.id} room={r} />)}
        </div>
      </section>
    </div>
  )
}

function RoomCard({ room }) {
  const addToast = useUIStore(s => s.addToast)
  return (
    <div className="bg-white/8 border border-white/10 rounded-2xl p-4 cursor-pointer hover:border-sky-500/50 transition-all"
      onClick={() => addToast(room.live ? `Joined "${room.title}" 🎙️ +${room.tokensPerMinute} VBT/min` : 'Reminder set! We\'ll notify you.', 'info')}>
      <div className="flex items-start gap-3">
        <div className="w-12 h-12 rounded-2xl bg-sky-500/20 border border-sky-500/30 flex items-center justify-center flex-shrink-0">
          <Radio className="w-5 h-5 text-sky-400" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            {room.live && <span className="text-[10px] font-bold text-white bg-red-500 px-2 py-0.5 rounded-full">LIVE</span>}
            <span className="text-xs text-white/40">{room.category}</span>
            {room.goalDate && <span className="text-[10px] text-amber-400 flex items-center gap-1"><Clock className="w-2.5 h-2.5" />Ends {room.goalDate}</span>}
          </div>
          <h3 className="font-display font-bold text-white text-sm truncate">{room.title}</h3>
          <p className="text-xs text-white/50 mt-0.5">{room.context}</p>
          <div className="flex items-center gap-3 mt-2">
            <span className="flex items-center gap-1 text-xs text-white/40"><Users className="w-3 h-3" />{formatTokens(room.listeners)}</span>
            <span className="flex items-center gap-1 text-xs text-amber-400"><Coins className="w-3 h-3" />+{room.tokensPerMinute} VBT/min</span>
          </div>
        </div>
        <div className={cn('text-xs px-3 py-1.5 rounded-xl font-semibold flex-shrink-0',
          room.live ? 'bg-sky-500/20 border border-sky-500/30 text-sky-300' : 'bg-white/10 text-white/60')}>
          {room.live ? 'Join' : room.startsIn}
        </div>
      </div>
    </div>
  )
}

// ─── Wallet Page ───────────────────────────────────────────────────────────────
function WalletPage() {
  const { balance: storeBalance, setBalance, setTransactions } = useWalletStore()
  const [balance, setLocalBalance] = useState(storeBalance || 0)
  const [cashoutOpen, setCashoutOpen] = useState(false)
  const [step, setStep] = useState(1)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('MTN_MOMO')
  const [phone, setPhone] = useState('')
  const [leaderboard, setLeaderboard] = useState([])
  const [showLeaderboard, setShowLeaderboard] = useState(false)
  const addToast = useUIStore(s => s.addToast)

  useEffect(() => {
    // Load real wallet data
    rewardsAPI.getWallet().then(res => {
      const w = res.data?.data
      if (w) { setLocalBalance(w.balance); setBalance(w.balance) }
    }).catch(() => {})
    // Load leaderboard
    rewardsAPI.getLeaderboard().then(res => {
      setLeaderboard(res.data?.data?.entries || res.data?.data || [])
    }).catch(() => {})
    // Refresh leaderboard every 60s
    const t = setInterval(() => {
      rewardsAPI.getLeaderboard().then(res => {
        setLeaderboard(res.data?.data?.entries || res.data?.data || [])
      }).catch(() => {})
    }, 60000)
    return () => clearInterval(t)
  }, [])

  const confirm = async () => {
    if (!phone) { addToast('Enter your phone number', 'error'); return }
    if (+amount < 500) { addToast('Minimum cashout is 500 tokens', 'error'); return }
    try {
      await rewardsAPI.cashout({ tokenAmount: +amount, provider: method, phoneNumber: phone })
      setLocalBalance(b => b - +amount)
      setCashoutOpen(false); setStep(1); setAmount(''); setPhone('')
      addToast('Withdrawal of ' + formatTokens(+amount) + ' VBT sent to ' + phone + ' 🎉', 'success')
    } catch (e) {
      addToast(e.response?.data?.message || 'Cashout failed', 'error')
    }
  }

  return (
    <div className="h-full overflow-y-auto scrollbar-none pt-14 pb-20 px-4 bg-black">
      {/* Balance */}
      <div className="bg-gradient-to-br from-sky-600 to-vibe-700 rounded-3xl p-6 mb-5 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-32 h-32 bg-white/5 rounded-full -translate-y-1/2 translate-x-1/2" />
        <p className="text-white/70 text-sm mb-1 flex items-center gap-2"><Coins className="w-4 h-4" />VIBE Token Balance</p>
        <p className="font-display text-5xl font-extrabold text-white">{formatTokens(balance)}<span className="text-2xl font-normal text-white/70 ml-2">VBT</span></p>
        <p className="text-amber-300 text-sm mt-1">≈ {tokensToCFA(balance)}</p>
        <div className="flex gap-3 mt-5">
          <button onClick={() => setCashoutOpen(true)}
            className="flex-1 py-3 rounded-xl bg-amber-400 text-black font-bold text-sm hover:bg-amber-300 transition-colors flex items-center justify-center gap-2">
            <Wallet className="w-4 h-4" />Cash Out
          </button>
          <button className="flex-1 py-3 rounded-xl bg-white/15 border border-white/20 text-white font-medium text-sm hover:bg-white/20 transition-colors">
            History
          </button>
        </div>
      </div>

      {/* Earn methods */}
      <div className="mb-5">
        <p className="text-xs font-bold text-white/50 uppercase tracking-wider mb-3 flex items-center gap-2">
          <Star className="w-3.5 h-3.5 text-amber-400" />How to Earn
        </p>
        <div className="grid grid-cols-2 gap-3">
          {[['👀','Watch Videos','+2'],['❤️','Like & Engage','+1'],['📤','Post Content','+10'],['🎙️','Host a Room','+20'],['📅','Daily Streak','+50'],['👥','Invite Friends','+100']].map(([e,l,t]) => (
            <div key={l} className="bg-white/8 border border-white/10 rounded-xl p-4 flex items-center gap-3">
              <span className="text-xl">{e}</span>
              <div>
                <p className="text-sm font-semibold text-white">{l}</p>
                <p className="text-xs text-amber-400 font-bold">{t} VBT</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Streak */}
      <div className="mb-5 bg-white/8 border border-white/10 rounded-2xl p-5">
        <div className="flex items-center gap-3 mb-3">
          <span className="text-2xl">🔥</span>
          <div><h3 className="font-bold text-white">Daily Streak</h3><p className="text-xs text-white/50">Active 7 days in a row</p></div>
          <div className="ml-auto token-badge bg-amber-500/20 border-amber-400/40 text-amber-300">+50 VBT/day</div>
        </div>
        <div className="flex gap-1">
          {Array(7).fill(0).map((_,i) => (
            <div key={i} className={cn('flex-1 h-2 rounded-full', i < 7 ? 'bg-amber-400' : 'bg-white/10')} />
          ))}
        </div>
      </div>

      {/* Exchange rate */}
      <div className="bg-white/8 border border-white/10 rounded-2xl p-5">
        <p className="text-xs font-bold text-white/50 uppercase tracking-wider mb-4 flex items-center gap-2">
          <BarChart2 className="w-3.5 h-3.5" />Exchange Rate
        </p>
        {[[100,'1,000'],[500,'5,000'],[1000,'10,000'],[5000,'50,000']].map(([vbt,cfa]) => (
          <div key={vbt} className="flex items-center justify-between py-2.5 border-b border-white/10 last:border-0">
            <span className="text-sm text-white flex items-center gap-2"><Coins className="w-3.5 h-3.5 text-amber-400" />{formatTokens(vbt)} VBT</span>
            <span className="text-sm text-amber-400 font-bold">{cfa} FCFA</span>
          </div>
        ))}
        <p className="text-xs text-white/30 mt-3 flex items-center gap-1">
          <Shield className="w-3 h-3" />Fixed rate by VIBE. Not fluctuating like crypto. Min: 500 VBT.
        </p>
      </div>

      {/* Cashout modal */}

      {/* Leaderboard */}
      <div className="mb-5 bg-white/8 border border-white/10 rounded-2xl p-5">
        <div className="flex items-center justify-between mb-3">
          <p className="text-xs font-bold text-white/50 uppercase tracking-wider flex items-center gap-2">
            <Crown className="w-3.5 h-3.5 text-amber-400" />Weekly Leaderboard
          </p>
          <button onClick={() => setShowLeaderboard(s => !s)}
            className="text-xs text-sky-400 hover:text-sky-300">
            {showLeaderboard ? 'Hide' : 'Show Top 50'}
          </button>
        </div>
        {showLeaderboard && (
          <div className="space-y-2 max-h-64 overflow-y-auto scrollbar-none">
            {leaderboard.length === 0 && (
              <p className="text-xs text-white/40 text-center py-4">No data yet this week</p>
            )}
            {leaderboard.slice(0, 50).map((entry, i) => (
              <div key={entry.userId || i} className="flex items-center gap-3 py-1.5 border-b border-white/5 last:border-0">
                <span className={cn('text-xs font-bold w-6 text-center',
                  i === 0 ? 'text-amber-400' : i === 1 ? 'text-gray-300' : i === 2 ? 'text-amber-700' : 'text-white/40')}>
                  {i + 1}
                </span>
                <span className="flex-1 text-sm text-white truncate">{entry.username || entry.userId || 'User'}</span>
                <span className="text-xs text-amber-400 font-bold">{formatTokens(entry.tokenCount || entry.score || 0)} VBT</span>
              </div>
            ))}
          </div>
        )}
      </div>

            {cashoutOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 flex items-end" onClick={e => e.target === e.currentTarget && setCashoutOpen(false)}>
          <div className="bg-gray-900 border border-white/10 rounded-t-3xl w-full p-6 animate-slide-in-up">
            <div className="flex items-center justify-between mb-5">
              <h2 className="font-display font-bold text-white">Cash Out Tokens</h2>
              <button onClick={() => setCashoutOpen(false)}><X className="w-5 h-5 text-white/60" /></button>
            </div>
            <div className="flex gap-2 mb-5">
              {[1,2].map(s => <div key={s} className={cn('flex-1 h-1.5 rounded-full transition-all', step >= s ? 'bg-sky-500' : 'bg-white/20')} />)}
            </div>
            {step === 1 && (
              <div className="space-y-4">
                <div>
                  <label className="text-xs text-white/50 mb-1.5 block font-semibold">Amount (min. 500 VBT)</label>
                  <input type="number" value={amount} onChange={e => setAmount(e.target.value)}
                    placeholder="500" className="w-full bg-white/10 border border-white/20 rounded-xl px-4 py-3 text-white focus:outline-none focus:border-sky-400" />
                  {amount && <p className="text-xs text-amber-400 mt-1.5">≈ {(+amount * 10).toLocaleString('fr-CM')} FCFA</p>}
                </div>
                <div className="flex gap-2">
                  {[500,1000,2000].map(a => (
                    <button key={a} onClick={() => setAmount(String(a))}
                      className={cn('flex-1 py-2 rounded-xl text-xs font-bold border transition-all',
                        amount === String(a) ? 'bg-sky-500/20 border-sky-500 text-sky-300' : 'border-white/20 text-white/50 hover:border-white/40')}>
                      {formatTokens(a)}
                    </button>
                  ))}
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {[{id:'MTN_MOMO',l:'MTN MoMo',e:'📱'},{id:'ORANGE_MONEY',l:'Orange Money',e:'🟠'}].map(m => (
                    <button key={m.id} onClick={() => setMethod(m.id)}
                      className={cn('p-3 rounded-xl border font-medium text-sm flex items-center gap-2 transition-all',
                        method === m.id ? 'bg-sky-500/20 border-sky-500 text-sky-300' : 'border-white/20 text-white/50 hover:border-white/40')}>
                      {m.e}{m.l}
                    </button>
                  ))}
                </div>
                <button disabled={!amount || +amount < 500} onClick={() => setStep(2)}
                  className="w-full py-3 rounded-xl bg-sky-500 text-white font-bold disabled:opacity-30 hover:bg-sky-400 transition-colors">
                  Continue →
                </button>
              </div>
            )}
            {step === 2 && (
              <div className="space-y-4">
                <div className="bg-white/10 rounded-2xl p-4 text-center">
                  <p className="text-white/50 text-xs mb-1">Cashing out</p>
                  <p className="font-display text-4xl font-extrabold text-white">{formatTokens(+amount)} VBT</p>
                  <p className="text-amber-400 text-sm mt-1">≈ {(+amount * 10).toLocaleString('fr-CM')} FCFA</p>
                </div>
                <div>
                  <label className="text-xs text-white/50 mb-1.5 block font-semibold">{method === 'MTN_MOMO' ? 'MTN' : 'Orange'} Phone Number</label>
                  <input type="tel" value={phone} onChange={e => setPhone(e.target.value)}
                    placeholder="+237 6XX XXX XXX" className="w-full bg-white/10 border border-white/20 rounded-xl px-4 py-3 text-white focus:outline-none focus:border-sky-400" />
                </div>
                <p className="text-xs text-white/30">⚠️ Please verify the number. VIBE is not responsible for funds sent to wrong numbers. Processing: instant–24h.</p>
                <div className="flex gap-3">
                  <button onClick={() => setStep(1)} className="flex-1 py-3 rounded-xl border border-white/20 text-white/70 hover:text-white font-medium">← Back</button>
                  <button onClick={confirm} disabled={!phone} className="flex-2 flex-1 py-3 rounded-xl bg-amber-400 text-black font-bold disabled:opacity-40 hover:bg-amber-300 transition-colors">Confirm</button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Public Profile ────────────────────────────────────────────────────────────
function PublicProfilePage() {
  const { user } = useAuthStore()
  const [tab, setTab] = useState('videos')
  return (
    <div className="h-full overflow-y-auto scrollbar-none pt-12 pb-20 bg-black">
      <div className="flex flex-col items-center py-6 px-4">
        <div className="relative mb-3">
          <div className="w-20 h-20 rounded-2xl border-3 border-sky-400 overflow-hidden">
            <Avatar name={user?.fullName || 'Creator'} size="2xl" />
          </div>
          <div className="absolute -bottom-1 -right-1 w-6 h-6 bg-sky-500 rounded-full flex items-center justify-center border-2 border-black">
            <BadgeCheck className="w-3.5 h-3.5 text-white" />
          </div>
        </div>
        <h1 className="font-display text-xl font-bold text-white">{user?.fullName || 'VIBE Creator'}</h1>
        <p className="text-white/50 text-sm">@{user?.username || 'vibecreator'}</p>
        <div className="flex gap-10 mt-5">
          {[['284','Following'],['12.4K','Followers'],[`${formatTokens(2840)}`,'Tokens']].map(([v,l]) => (
            <div key={l} className="flex flex-col items-center">
              <span className="font-display font-bold text-white text-xl">{v}</span>
              <span className="text-xs text-white/50">{l}</span>
            </div>
          ))}
        </div>
        <button className="mt-4 px-8 py-2.5 rounded-xl border border-white/20 text-white text-sm font-medium hover:bg-white/10 transition-colors">Edit Profile</button>
      </div>

      <div className="flex border-b border-white/10">
        {['videos','liked','saved'].map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={cn('flex-1 py-3 text-sm font-medium capitalize transition-colors border-b-2',
              tab === t ? 'text-white border-sky-500' : 'text-white/40 border-transparent')}>
            {t}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-3 gap-0.5">
        {DEMO_POSTS.concat(DEMO_POSTS).concat(DEMO_POSTS).slice(0,9).map((p, i) => (
          <div key={i} className="aspect-[9/16] relative overflow-hidden cursor-pointer group"
            style={{ background: p.gradient }}>
            <div className="absolute inset-0 flex items-center justify-center opacity-20 group-hover:opacity-30 transition-opacity">
              <Play className="w-8 h-8 text-white" />
            </div>
            <div className="absolute bottom-1 left-1 flex items-center gap-1">
              <Heart className="w-3 h-3 text-white/70" />
              <span className="text-white/70 text-[10px]">{formatTokens(p.likes)}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ─── Bottom Nav ────────────────────────────────────────────────────────────────
function PublicBottomNav({ activePage, setActivePage }) {
  const items = [
    { id:'feed',    icon:Home,    label:'Home' },
    { id:'discover',icon:Search,  label:'Discover' },
    { id:'rooms',   icon:Radio,   label:'Rooms' },
    { id:'wallet',  icon:Coins,   label:'Wallet' },
    { id:'profile', icon:User,    label:'Profile' },
  ]
  return (
    <div className="absolute bottom-0 left-0 right-0 z-20 bg-black/90 backdrop-blur-xl border-t border-white/10 flex items-center justify-around px-2 py-2">
      {items.map(({ id, icon:Icon, label }) => (
        <button key={id} onClick={() => setActivePage(id)}
          className={cn('flex flex-col items-center gap-1 px-3 py-1.5 rounded-xl transition-all',
            activePage === id ? 'text-white' : 'text-white/30 hover:text-white/60')}>
          <Icon className="w-5 h-5" />
          <span className="text-[9px] font-semibold">{label}</span>
        </button>
      ))}
    </div>
  )
}

// ─── Demo Data ─────────────────────────────────────────────────────────────────
const DEMO_CREATORS = [
  { id: 'creator1', name: 'Amara Diallo', username: 'amara_dj', followers: 142000, verified: true },
  { id: 'creator2', name: 'Kwame Asante', username: 'kwame_gh', followers: 89500, verified: true },
  { id: 'creator3', name: 'Fatima Ndiaye', username: 'fatima_sn', followers: 67200, verified: false },
  { id: 'creator4', name: 'Chioma Obi', username: 'chioma_ng', followers: 231000, verified: true },
]

const DEMO_POSTS = [
  { id:1, type:'VIDEO', creator:'Amara Diallo', creatorHandle:'amaravibes', caption:"Showcasing Douala's nightlife! African cities deserve more love 🌆", tags:['Douala','Africa','VIBE'], likes:24800, comments:1240, saves:880, shares:540, music:'Afrobeats Mix Vol.3', language:'FR/Pidgin', gradient:'linear-gradient(135deg,#1a1a3e,#4a1a6e,#1a3a4e)', emoji:'🌆', verified:true },
  { id:2, type:'VIDEO', creator:'Kofi Mensah',  creatorHandle:'kofibeats',  caption:'New Afrobeats dropping Friday! 🎵 For the streets of Accra!', tags:['Afrobeats','Ghana','Music'], likes:18200, comments:960, saves:1200, shares:320, music:'Original - kofibeats', language:'Twi/English', gradient:'linear-gradient(135deg,#0d2137,#1a5276,#117a65)', emoji:'🎵', verified:false },
  { id:3, type:'IMAGE', creator:'Fatou Ba',     creatorHandle:'fatoulife',  caption:'Cooking Thiéboudienne for 200 people! 🍲🇸🇳', tags:['SenegalFood','Culture'], likes:9600, comments:480, saves:2100, shares:760, music:'Trending #VibeChallenge', language:'FR/Wolof', gradient:'linear-gradient(135deg,#7d1a1a,#a04000,#7d6608)', emoji:'🍲', verified:true },
]
const DEMO_ROOMS = [
  { id:1, live:true,  title:'African Tech Talk — Building at Scale', context:'Context: Engineers building for Africa', host:'TechVibeAfrica', category:'Tech', listeners:842,  tokensPerMinute:5, goalDate:null,    startsIn:null },
  { id:2, live:true,  title:'Late Night Can\'t Sleep',               context:'Context: Night owls vibing together', host:'AmaraVibes',    category:'Mood', listeners:1240, tokensPerMinute:3, goalDate:null,    startsIn:null },
  { id:3, live:false, title:'Study Session — Deep Focus',            context:'Context: Exams week group study',     host:'VibeStudy',     category:'Study',listeners:0,    tokensPerMinute:4, goalDate:'May 10', startsIn:'In 2h' },
  { id:4, live:false, title:'Processing a Moment Together',         context:'Context: Support and reflection',     host:'VibeNetwork',   category:'Support',listeners:0,  tokensPerMinute:2, goalDate:'May 15', startsIn:'Tomorrow' },
]
const DEMO_COMMENTS = [
  { id:1, name:'Kofi Mensah',  handle:'kofibeats',  text:'This is amazing! 🔥 Africa is rising!', likes:124, time:new Date(Date.now()-300000).toISOString() },
  { id:2, name:'Fatou Ba',     handle:'fatoulife',   text:'Love seeing this! Keep going 💪',       likes:89,  time:new Date(Date.now()-600000).toISOString() },
  { id:3, name:'James Okafor', handle:'jamesokafor', text:'Following immediately! Fire 🎬',        likes:45,  time:new Date(Date.now()-900000).toISOString() },
]
