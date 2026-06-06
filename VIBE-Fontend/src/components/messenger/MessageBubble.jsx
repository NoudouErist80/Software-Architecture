/**
 * MessageBubble.jsx
 * ─────────────────────────────────────────────────────────────────────────────
 * Renders a single chat message bubble following industry standards:
 *
 * ▸ DIRECT (1-to-1) chat — WhatsApp / Telegram layout:
 *     • My messages   → RIGHT, VIBE blue/indigo bubble (#4f46e5), white text
 *     • Their messages → LEFT,  white bubble, dark text, their avatar shown
 *
 * ▸ GROUP chat — Snapchat layout:
 *     • ALL messages go LEFT (same side)
 *     • My messages:   light VIBE tinted bubble (#eef2ff) with left accent border
 *     • Their messages: white bubble, sender name shown above, their avatar
 *     • Own avatar is NOT shown in groups (Snapchat/Discord convention)
 *
 * Colors chosen to match VIBE's blue/indigo theme (Tailwind vibe-* classes).
 * ─────────────────────────────────────────────────────────────────────────────
 */

import React, { useState } from 'react'
import {
  Check, CheckCheck, Reply, Globe, Edit2, Trash2,
  Copy, Paperclip, Play, Pause, Mic,
} from 'lucide-react'
import Avatar from '../shared/Avatar'
import { cn, formatMessageTime, canEditOrDelete, SUPPORTED_LANGUAGES } from '../../utils'
import { useAuthStore } from '../../store'

// ─── Voice Message Player ──────────────────────────────────────────────────────
function VoiceMessage({ url, duration, isMine, isGroup }) {
  const [playing, setPlaying]   = useState(false)
  const [progress, setProgress] = useState(0)
  const [currentTime, setCurrentTime] = useState(0)
  const audioRef = React.useRef(null)

  React.useEffect(() => {
    const audio = new Audio(url)
    audioRef.current = audio
    const onTime = () => {
      setCurrentTime(audio.currentTime)
      setProgress(audio.duration ? (audio.currentTime / audio.duration) * 100 : 0)
    }
    const onEnd = () => { setPlaying(false); setProgress(0); setCurrentTime(0) }
    audio.addEventListener('timeupdate', onTime)
    audio.addEventListener('ended', onEnd)
    return () => { audio.pause(); audio.removeEventListener('timeupdate', onTime); audio.removeEventListener('ended', onEnd) }
  }, [url])

  const toggle = () => {
    if (!audioRef.current) return
    if (playing) { audioRef.current.pause(); setPlaying(false) }
    else          { audioRef.current.play();  setPlaying(true) }
  }

  const fmt = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`

  // In groups all bubbles are "light" so isMine in group = tinted, not dark
  const isDark = isMine && !isGroup

  return (
    <div className="flex items-center gap-3 py-1 min-w-[180px]">
      <button
        onClick={toggle}
        className={cn(
          'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 transition-colors',
          isDark
            ? 'bg-white/20 hover:bg-white/30 text-white'
            : 'bg-vibe-100 hover:bg-vibe-200 text-vibe-600',
        )}
      >
        {playing ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
      </button>
      <div className="flex-1">
        <div className={cn('h-1.5 rounded-full overflow-hidden', isDark ? 'bg-white/30' : 'bg-gray-200')}>
          <div
            className={cn('h-full rounded-full transition-all', isDark ? 'bg-white' : 'bg-vibe-500')}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
      <span className={cn('text-[10px] flex-shrink-0 tabular-nums', isDark ? 'text-white/70' : 'text-gray-400')}>
        {playing ? fmt(currentTime) : fmt(duration || 0)}
      </span>
      <Mic className={cn('w-3 h-3 flex-shrink-0', isDark ? 'text-white/60' : 'text-gray-400')} />
    </div>
  )
}

// ─── Action toolbar button ─────────────────────────────────────────────────────
function MsgBtn({ title, emoji, icon, onClick, danger }) {
  return (
    <button
      title={title}
      onClick={onClick}
      className={cn(
        'w-6 h-6 flex items-center justify-center rounded-lg text-xs transition-colors',
        danger
          ? 'text-red-400 hover:text-red-600 hover:bg-red-50'
          : 'text-gray-500 hover:text-vibe-600 hover:bg-sky-50',
      )}
    >
      {emoji || icon}
    </button>
  )
}

// ─── MessageBubble ─────────────────────────────────────────────────────────────
export default function MessageBubble({
  msg,
  isMine,
  isGroup,
  showSender,       // show sender name label above bubble (groups only)
  isLastInGroup,    // last consecutive message from same sender → show avatar
  translated,
  translateTarget,
  onTranslate,
  onOpenTranslate,
  editingMsg,
  editText,
  setEditText,
  onEditStart,
  onEditSave,
  onEditCancel,
  onDelete,
  onReply,
  reactions,
  onReaction,
  reactionTarget,
  onOpenReaction,
  QUICK_REACTIONS,
}) {
  const { user } = useAuthStore()
  const [hover, setHover]               = useState(false)
  const [showDeleteMenu, setShowDeleteMenu] = useState(false)
  const canModify  = canEditOrDelete(msg.createdAt)
  const isEditing  = editingMsg === msg.id

  // ── Deleted message placeholder ──────────────────────────────────────────────
  if (msg.deleted) {
    return (
      <div className={cn('flex mb-1', isGroup ? 'justify-start' : isMine ? 'justify-end' : 'justify-start')}>
        <span className="text-xs text-gray-400 italic px-3 py-1 bg-gray-50 rounded-xl border border-gray-200">
          🚫 This message was deleted
        </span>
      </div>
    )
  }

  // ────────────────────────────────────────────────────────────────────────────
  // LAYOUT LOGIC
  //
  // GROUP  → Snapchat style: ALL messages left-aligned
  //   • isMine   : indigo-tinted bubble (#eef2ff) with left accent border,
  //                "You" label, NO own avatar
  //   • !isMine  : white bubble, sender name label, their avatar left
  //
  // DIRECT → WhatsApp/Telegram style
  //   • isMine   : RIGHT, VIBE indigo solid bubble, white text
  //   • !isMine  : LEFT,  white bubble, dark text, their avatar
  // ────────────────────────────────────────────────────────────────────────────

  const rowAlign = isGroup
    ? 'justify-start'
    : isMine
      ? 'justify-end'
      : 'justify-start'

  // Bubble colors
  const bubbleCls = isGroup
    ? isMine
      // Group / own: soft indigo tint with accent border (Snapchat-style "you")
      ? 'bg-indigo-50 border border-indigo-200 border-l-4 border-l-indigo-400 text-gray-800 rounded-2xl rounded-tl-sm'
      // Group / other: clean white
      : 'bg-white border border-gray-200 text-gray-800 rounded-2xl rounded-tl-sm shadow-sm'
    : isMine
      // Direct / mine: solid VIBE indigo → white text (WhatsApp blue equivalent)
      ? 'bg-indigo-600 text-white rounded-2xl rounded-tr-sm shadow-md'
      // Direct / other: white → dark text
      : 'bg-white border border-gray-200 text-gray-800 rounded-2xl rounded-tl-sm shadow-sm'

  // Sender name color in groups
  const senderLabelColor = isMine ? 'text-indigo-500' : 'text-vibe-600'

  // Is the bubble "dark" (white text)? Only direct/mine bubbles are dark.
  const isDark = isMine && !isGroup

  return (
    <div
      className={cn('flex gap-2 group mb-0.5', rowAlign)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => { setHover(false); setShowDeleteMenu(false) }}
    >
      {/* ── Left avatar slot ──────────────────────────────────────────────── */}
      {/* Groups: show avatar for OTHERS only (not own messages) */}
      {isGroup && !isMine && (
        <div className="w-8 flex-shrink-0 mt-auto mb-1">
          {isLastInGroup
            ? <Avatar name={msg.senderName || 'User'} size="sm" />
            : <div className="w-8" /> /* spacer to keep alignment */
          }
        </div>
      )}
      {/* Group own: no avatar (Snapchat/Discord: own avatar not shown in groups) */}
      {isGroup && isMine && <div className="w-0" />}

      {/* Direct / other: show their avatar left */}
      {!isGroup && !isMine && (
        <div className="w-8 flex-shrink-0 mt-auto mb-1">
          {isLastInGroup
            ? <Avatar name={msg.senderName || 'User'} size="sm" />
            : <div className="w-8" />
          }
        </div>
      )}

      {/* ── Bubble column ─────────────────────────────────────────────────── */}
      <div className={cn('flex flex-col max-w-xs lg:max-w-md xl:max-w-lg', isGroup || !isMine ? 'items-start' : 'items-end')}>

        {/* Sender name label — groups only */}
        {showSender && (
          <span className={cn('text-[11px] font-semibold ml-1 mb-0.5', senderLabelColor)}>
            {isMine ? 'You' : (msg.senderName || 'User')}
          </span>
        )}

        {/* Reply-to quote */}
        {msg.replyTo && (
          <div className={cn(
            'text-xs px-3 py-1.5 rounded-xl mb-1 border-l-2 border-vibe-400 max-w-full',
            isDark ? 'bg-white/20 text-white/80' : 'bg-gray-50 text-gray-500',
          )}>
            <span className="font-semibold text-vibe-400">{msg.replyTo.senderName}</span>
            <p className="truncate">{msg.replyTo.content}</p>
          </div>
        )}

        {/* ── Main bubble ───────────────────────────────────────────────── */}
        <div className="relative">
          {isEditing ? (
            <div className="flex flex-col gap-2 min-w-[220px]">
              <textarea
                value={editText}
                onChange={e => setEditText(e.target.value)}
                className="vibe-input text-sm resize-none"
                rows={2}
                autoFocus
              />
              <div className="flex gap-2 justify-end">
                <button onClick={onEditCancel} className="text-xs text-gray-500 px-3 py-1 border border-gray-200 rounded-lg hover:bg-gray-50">
                  Cancel
                </button>
                <button onClick={onEditSave} className="text-xs btn-vibe py-1 px-3">
                  Save
                </button>
              </div>
            </div>
          ) : (
            <div className={cn('px-3.5 py-2.5 text-sm leading-relaxed', bubbleCls)}>
              {/* Media content */}
              {msg.type === 'IMAGE' && msg.mediaUrl ? (
                <img
                  src={msg.mediaUrl}
                  alt="media"
                  className="max-w-[220px] rounded-xl object-cover cursor-pointer"
                  onClick={() => window.open(msg.mediaUrl, '_blank')}
                />
              ) : msg.type === 'VIDEO' && msg.mediaUrl ? (
                <video src={msg.mediaUrl} controls className="max-w-[220px] rounded-xl" />
              ) : msg.type === 'VOICE' && msg.mediaUrl ? (
                <VoiceMessage url={msg.mediaUrl} duration={msg.durationSeconds} isMine={isMine} isGroup={isGroup} />
              ) : msg.type === 'FILE' ? (
                <div className="flex items-center gap-2 py-1">
                  <Paperclip className={cn('w-4 h-4', isDark ? 'text-white/70' : 'text-vibe-400')} />
                  <span
                    className={cn('text-xs underline cursor-pointer', isDark ? 'text-white/80' : 'text-vibe-600')}
                    onClick={() => window.open(msg.mediaUrl, '_blank')}
                  >
                    {msg.content || 'File'}
                  </span>
                </div>
              ) : (
                <p className="text-[13px] leading-relaxed whitespace-pre-wrap break-words">
                  {translated || msg.content}
                </p>
              )}

              {/* Translation badge */}
              {translated && (
                <p className={cn('text-[10px] mt-1', isDark ? 'text-indigo-200' : 'text-vibe-500')}>
                  🌐 Translated
                </p>
              )}

              {/* Edited badge */}
              {msg.edited && (
                <p className={cn('text-[10px] mt-0.5 italic', isDark ? 'text-white/50' : 'text-gray-400')}>
                  edited
                </p>
              )}
            </div>
          )}

          {/* ── Hover action toolbar ────────────────────────────────────── */}
          {hover && !isEditing && (
            <div className={cn(
              'absolute -top-9 flex items-center animate-fade-in z-20',
              // Own messages → toolbar to the left of bubble; others → to the right
              isMine && !isGroup ? 'right-0' : 'left-0',
            )}>
              <div className="flex items-center bg-white border border-gray-200 rounded-xl px-1 py-1 shadow-lg gap-0.5">
                {/* React */}
                <div className="relative">
                  <MsgBtn title="React" emoji="😊" onClick={onOpenReaction} />
                  {reactionTarget === msg.id && (
                    <div className="absolute bottom-full mb-1 left-0 bg-white border border-gray-200 rounded-xl px-2 py-1.5 flex gap-1 shadow-xl z-50 animate-fade-in">
                      {QUICK_REACTIONS.map(e => (
                        <button key={e} onClick={() => onReaction(e)} className="text-lg hover:scale-125 transition-transform">
                          {e}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Reply */}
                <MsgBtn title="Reply" icon={<Reply className="w-3 h-3" />} onClick={onReply} />

                {/* Translate */}
                <div className="relative">
                  <MsgBtn title="Translate" icon={<Globe className="w-3 h-3" />} onClick={onOpenTranslate} />
                  {translateTarget === msg.id && (
                    <div className={cn(
                      'absolute bottom-full mb-1 bg-white border border-gray-200 rounded-xl py-1 w-48 z-50 shadow-xl animate-fade-in max-h-56 overflow-y-auto scrollbar-none',
                      isMine && !isGroup ? 'right-0' : 'left-0',
                    )}>
                      <p className="px-3 py-1 text-[10px] text-gray-400 font-semibold uppercase sticky top-0 bg-white">
                        Translate to…
                      </p>
                      {SUPPORTED_LANGUAGES.map(l => (
                        <button
                          key={l.code}
                          onClick={() => onTranslate(l.code)}
                          className="w-full text-left px-3 py-1.5 text-xs text-gray-600 hover:text-vibe-700 hover:bg-sky-50 flex items-center gap-2"
                        >
                          {l.flag} {l.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Edit (own messages only, within 15 min) */}
                {isMine && canModify && (
                  <MsgBtn title="Edit" icon={<Edit2 className="w-3 h-3" />} onClick={onEditStart} />
                )}

                {/* Copy */}
                <MsgBtn
                  title="Copy"
                  icon={<Copy className="w-3 h-3" />}
                  onClick={() => navigator.clipboard?.writeText(msg.content)}
                />

                {/* Delete (own only) */}
                {isMine && (
                  <div className="relative">
                    <MsgBtn
                      title="Delete"
                      icon={<Trash2 className="w-3 h-3" />}
                      onClick={() => setShowDeleteMenu(d => !d)}
                      danger
                    />
                    {showDeleteMenu && (
                      <div className={cn(
                        'absolute bottom-full mb-1 bg-white border border-gray-200 rounded-xl py-1 w-52 z-50 shadow-xl animate-fade-in',
                        isMine && !isGroup ? 'right-0' : 'left-0',
                      )}>
                        {canModify && (
                          <button
                            onClick={() => { onDelete(true); setShowDeleteMenu(false) }}
                            className="w-full text-left px-4 py-2 text-xs text-red-600 hover:bg-red-50 flex items-center gap-2"
                          >
                            <Trash2 className="w-3 h-3" /> Delete for everyone
                          </button>
                        )}
                        <button
                          onClick={() => { onDelete(false); setShowDeleteMenu(false) }}
                          className="w-full text-left px-4 py-2 text-xs text-gray-600 hover:bg-gray-50 flex items-center gap-2"
                        >
                          <Trash2 className="w-3 h-3" /> Delete for me
                        </button>
                        {!canModify && (
                          <p className="px-4 py-2 text-[10px] text-gray-400">
                            Cannot delete for everyone after 15 min
                          </p>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* ── Emoji reactions ───────────────────────────────────────────── */}
        {Object.keys(reactions).length > 0 && (
          <div className={cn('flex gap-1 mt-0.5 flex-wrap', isMine && !isGroup ? 'justify-end' : 'justify-start')}>
            {Object.entries(reactions).map(([emoji, count]) => (
              <span
                key={emoji}
                className="text-xs bg-white border border-gray-200 rounded-full px-1.5 py-0.5 shadow-sm cursor-pointer hover:bg-sky-50"
              >
                {emoji}{count > 1 ? ` ${count}` : ''}
              </span>
            ))}
          </div>
        )}

        {/* ── Timestamp + delivery tick ─────────────────────────────────── */}
        <div className={cn('flex items-center gap-1 mt-0.5', isMine && !isGroup ? 'justify-end' : 'justify-start')}>
          <span className="text-[10px] text-gray-400 tabular-nums">
            {formatMessageTime(msg.createdAt)}
          </span>
          {isMine && (
            msg.status === 'READ'
              ? <CheckCheck className="w-3 h-3 text-green-500" />
              : msg.status === 'DELIVERED'
                ? <CheckCheck className="w-3 h-3 text-gray-400" />
                : <Check className="w-3 h-3 text-gray-400" />
          )}
        </div>
      </div>

      {/* ── Right avatar slot (direct / own messages only) ────────────────── */}
      {!isGroup && isMine && isLastInGroup && (
        <div className="w-8 flex-shrink-0 mt-auto mb-1">
          <Avatar name={user?.fullName || 'Me'} src={user?.profilePictureUrl} size="sm" />
        </div>
      )}
      {!isGroup && isMine && !isLastInGroup && <div className="w-8 flex-shrink-0" />}
    </div>
  )
}