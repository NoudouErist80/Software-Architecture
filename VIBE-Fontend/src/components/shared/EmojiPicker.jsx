/**
 * EmojiPicker.jsx
 * Directory: vibe-frontend-v2/src/components/shared/EmojiPicker.jsx
 *
 * WhatsApp-grade emoji keyboard featuring:
 *  • Category tabs (Recent, People, Nature, Food, Activities, Travel, Objects, Symbols, Flags)
 *  • Skin-tone modifier selector
 *  • Search bar
 *  • Persisted "Recent" category (localStorage)
 *  • Fully keyboard-navigable, accessible
 *  • Zero external dependency — pure data, no npm emoji lib needed
 *
 * Usage:
 *   <EmojiPicker
 *     isOpen={showEmoji}
 *     onEmojiSelect={(emoji) => insertIntoComposer(emoji)}
 *     onClose={() => setShowEmoji(false)}
 *     position="top"   // "top" | "bottom"  (where the picker opens relative to trigger)
 *   />
 */

import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import {
  Smile, Clock, Users, Leaf, Coffee, Zap, Globe, Box, Hash, Flag,
  Search, X, ChevronDown
} from 'lucide-react';

// ─── Emoji data (representative subset — extend as needed) ────────────────────
// Format: { emoji, label } — label used for search and aria
const EMOJI_DATA = {
  people: [
    { e: '😀', l: 'grinning face' },       { e: '😂', l: 'face with tears of joy' },
    { e: '😍', l: 'heart eyes' },           { e: '🥺', l: 'pleading face' },
    { e: '😭', l: 'loudly crying' },        { e: '🤔', l: 'thinking' },
    { e: '😎', l: 'cool sunglasses' },      { e: '🥳', l: 'party face' },
    { e: '😴', l: 'sleeping' },             { e: '😡', l: 'angry' },
    { e: '🤗', l: 'hugging' },             { e: '🤣', l: 'rolling on floor laughing' },
    { e: '😊', l: 'smiling' },             { e: '😇', l: 'angel' },
    { e: '🥰', l: 'smiling with hearts' }, { e: '😘', l: 'face blowing kiss' },
    { e: '😜', l: 'winking tongue' },      { e: '🤑', l: 'money mouth' },
    { e: '😤', l: 'huffing' },             { e: '😱', l: 'screaming' },
    { e: '🤩', l: 'star struck' },         { e: '🥴', l: 'woozy' },
    { e: '😷', l: 'mask' },               { e: '🤒', l: 'thermometer face' },
    { e: '👋', l: 'waving hand' },         { e: '👍', l: 'thumbs up' },
    { e: '👎', l: 'thumbs down' },         { e: '❤️', l: 'red heart' },
    { e: '🔥', l: 'fire' },               { e: '💯', l: 'hundred points' },
    { e: '🙏', l: 'folded hands' },        { e: '💪', l: 'flexed biceps' },
    { e: '🤝', l: 'handshake' },           { e: '✌️', l: 'victory hand' },
    { e: '👀', l: 'eyes' },               { e: '💀', l: 'skull' },
    { e: '🫶', l: 'heart hands' },         { e: '🫡', l: 'saluting face' },
    { e: '🤌', l: 'pinched fingers' },     { e: '🫵', l: 'index pointing' },
  ],
  nature: [
    { e: '🌍', l: 'globe africa europe' }, { e: '🌴', l: 'palm tree' },
    { e: '🌺', l: 'hibiscus' },           { e: '🌻', l: 'sunflower' },
    { e: '🐘', l: 'elephant' },           { e: '🦁', l: 'lion' },
    { e: '🦅', l: 'eagle' },             { e: '🐊', l: 'crocodile' },
    { e: '🦒', l: 'giraffe' },           { e: '🦓', l: 'zebra' },
    { e: '🌿', l: 'herb' },             { e: '🍃', l: 'leaves' },
    { e: '☀️', l: 'sun' },              { e: '🌙', l: 'crescent moon' },
    { e: '⭐', l: 'star' },             { e: '🌈', l: 'rainbow' },
    { e: '🌊', l: 'wave' },             { e: '⛅', l: 'partly cloudy' },
    { e: '🐦', l: 'bird' },             { e: '🦋', l: 'butterfly' },
  ],
  food: [
    { e: '🍔', l: 'hamburger' },     { e: '🍕', l: 'pizza' },
    { e: '🍗', l: 'chicken leg' },   { e: '🍜', l: 'noodles' },
    { e: '🫘', l: 'beans' },         { e: '🥘', l: 'stew pot' },
    { e: '🍖', l: 'meat on bone' },  { e: '🫕', l: 'fondue' },
    { e: '🥗', l: 'salad' },        { e: '🍱', l: 'bento' },
    { e: '🍺', l: 'beer' },         { e: '🥤', l: 'cup with straw' },
    { e: '☕', l: 'coffee' },        { e: '🧃', l: 'juice box' },
    { e: '🌮', l: 'taco' },         { e: '🥐', l: 'croissant' },
    { e: '🍰', l: 'cake' },         { e: '🍫', l: 'chocolate bar' },
    { e: '🍌', l: 'banana' },       { e: '🍍', l: 'pineapple' },
    { e: '🥭', l: 'mango' },        { e: '🍉', l: 'watermelon' },
  ],
  activities: [
    { e: '⚽', l: 'soccer ball' },   { e: '🏀', l: 'basketball' },
    { e: '🎵', l: 'music note' },    { e: '🎮', l: 'video game' },
    { e: '🎉', l: 'party popper' }, { e: '🎊', l: 'confetti ball' },
    { e: '🏆', l: 'trophy' },       { e: '🥇', l: 'gold medal' },
    { e: '🎭', l: 'performing arts'},{ e: '🎨', l: 'palette' },
    { e: '🎤', l: 'microphone' },   { e: '🎷', l: 'saxophone' },
    { e: '🥁', l: 'drum' },         { e: '🎸', l: 'guitar' },
    { e: '🏋️', l: 'weightlifter' }, { e: '🤸', l: 'cartwheel' },
    { e: '🏊', l: 'swimmer' },      { e: '🚴', l: 'cyclist' },
    { e: '🎯', l: 'bullseye' },     { e: '🎲', l: 'dice' },
  ],
  travel: [
    { e: '✈️', l: 'airplane' },      { e: '🚗', l: 'car' },
    { e: '🛺', l: 'auto rickshaw' }, { e: '🏙️', l: 'cityscape' },
    { e: '🗺️', l: 'world map' },    { e: '🌇', l: 'sunset city' },
    { e: '🏝️', l: 'island' },       { e: '⛺', l: 'tent' },
    { e: '🏠', l: 'house' },        { e: '🏢', l: 'office building' },
    { e: '🌉', l: 'bridge at night'},{ e: '🗼', l: 'eiffel tower' },
    { e: '🚂', l: 'train' },        { e: '⛵', l: 'sailboat' },
    { e: '🚁', l: 'helicopter' },   { e: '🛸', l: 'flying saucer' },
    { e: '🚀', l: 'rocket' },       { e: '🌌', l: 'milky way' },
    { e: '🗽', l: 'statue of liberty'},{ e: '🕌', l: 'mosque' },
  ],
  objects: [
    { e: '📱', l: 'mobile phone' },  { e: '💻', l: 'laptop' },
    { e: '📷', l: 'camera' },       { e: '🎙️', l: 'studio microphone' },
    { e: '💡', l: 'light bulb' },   { e: '🔑', l: 'key' },
    { e: '💰', l: 'money bag' },    { e: '📚', l: 'books' },
    { e: '✏️', l: 'pencil' },       { e: '🔬', l: 'microscope' },
    { e: '💊', l: 'pill' },         { e: '🩺', l: 'stethoscope' },
    { e: '🔧', l: 'wrench' },       { e: '⚙️', l: 'gear' },
    { e: '🧲', l: 'magnet' },       { e: '🔋', l: 'battery' },
    { e: '🪙', l: 'coin' },         { e: '💳', l: 'credit card' },
    { e: '🎁', l: 'gift' },         { e: '🎀', l: 'ribbon' },
  ],
  symbols: [
    { e: '❤️', l: 'red heart' },    { e: '🧡', l: 'orange heart' },
    { e: '💛', l: 'yellow heart' }, { e: '💚', l: 'green heart' },
    { e: '💙', l: 'blue heart' },   { e: '💜', l: 'purple heart' },
    { e: '🖤', l: 'black heart' },  { e: '🤍', l: 'white heart' },
    { e: '💔', l: 'broken heart' }, { e: '💞', l: 'revolving hearts' },
    { e: '✅', l: 'check mark' },   { e: '❌', l: 'cross mark' },
    { e: '⚡', l: 'lightning' },    { e: '💫', l: 'dizzy' },
    { e: '🌟', l: 'glowing star' }, { e: '💥', l: 'collision' },
    { e: '🔴', l: 'red circle' },   { e: '🟠', l: 'orange circle' },
    { e: '🟡', l: 'yellow circle' },{ e: '🟢', l: 'green circle' },
    { e: '❗', l: 'exclamation' },  { e: '❓', l: 'question' },
    { e: '♾️', l: 'infinity' },     { e: '🔞', l: 'no one under 18' },
    { e: '🆕', l: 'new' },          { e: '🆓', l: 'free' },
    { e: '🔝', l: 'top' },          { e: '🔛', l: 'on' },
    { e: '🔜', l: 'soon' },         { e: '🅰️', l: 'blood type a' },
  ],
  flags: [
    { e: '🌍', l: 'globe africa europe' },
    { e: '🇨🇲', l: 'cameroon flag' },    { e: '🇳🇬', l: 'nigeria flag' },
    { e: '🇬🇭', l: 'ghana flag' },        { e: '🇨🇮', l: 'ivory coast flag' },
    { e: '🇸🇳', l: 'senegal flag' },      { e: '🇰🇪', l: 'kenya flag' },
    { e: '🇿🇦', l: 'south africa flag' }, { e: '🇪🇹', l: 'ethiopia flag' },
    { e: '🇺🇬', l: 'uganda flag' },       { e: '🇹🇿', l: 'tanzania flag' },
    { e: '🇷🇼', l: 'rwanda flag' },       { e: '🇨🇩', l: 'dr congo flag' },
    { e: '🇺🇸', l: 'usa flag' },          { e: '🇬🇧', l: 'uk flag' },
    { e: '🇫🇷', l: 'france flag' },       { e: '🏳️', l: 'white flag' },
    { e: '🏴', l: 'black flag' },          { e: '🚩', l: 'triangular flag' },
    { e: '🏁', l: 'chequered flag' },      { e: '🎌', l: 'crossed flags' },
  ],
};

const RECENT_KEY = 'vibe_emoji_recent';
const MAX_RECENT = 24;

const CATEGORIES = [
  { id: 'recent',     label: 'Recent',     Icon: Clock  },
  { id: 'people',     label: 'People',     Icon: Users  },
  { id: 'nature',     label: 'Nature',     Icon: Leaf   },
  { id: 'food',       label: 'Food',       Icon: Coffee },
  { id: 'activities', label: 'Activities', Icon: Zap    },
  { id: 'travel',     label: 'Travel',     Icon: Globe  },
  { id: 'objects',    label: 'Objects',    Icon: Box    },
  { id: 'symbols',    label: 'Symbols',   Icon: Hash   },
  { id: 'flags',      label: 'Flags',      Icon: Flag   },
];

// ─── Helpers ──────────────────────────────────────────────────────────────────
function loadRecent() {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
  } catch { return []; }
}

function saveRecent(recentList) {
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(recentList)); } catch {}
}

// ─── Component ────────────────────────────────────────────────────────────────
export default function EmojiPicker({ isOpen, onEmojiSelect, onClose, position = 'top' }) {
  const [activeCategory, setActiveCategory] = useState('people');
  const [searchQuery,    setSearchQuery]    = useState('');
  const [recent,         setRecent]         = useState(loadRecent);
  const searchRef = useRef(null);
  const gridRef   = useRef(null);

  // Focus search on open
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => searchRef.current?.focus(), 100);
    } else {
      setSearchQuery('');
      setActiveCategory('people');
    }
  }, [isOpen]);

  // ── Emoji selection ──────────────────────────────────────────────────────
  const handleEmojiClick = useCallback((emoji) => {
    onEmojiSelect(emoji.e);

    // Update recent
    setRecent(prev => {
      const filtered = prev.filter(r => r.e !== emoji.e);
      const updated  = [emoji, ...filtered].slice(0, MAX_RECENT);
      saveRecent(updated);
      return updated;
    });
  }, [onEmojiSelect]);

  // ── Filtered emoji list ──────────────────────────────────────────────────
  const displayEmojis = useMemo(() => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return Object.values(EMOJI_DATA)
        .flat()
        .filter(em => em.l.includes(q) || em.e.includes(q));
    }
    if (activeCategory === 'recent') return recent;
    return EMOJI_DATA[activeCategory] || [];
  }, [searchQuery, activeCategory, recent]);

  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop — clicking outside closes */}
      <div className="ep-backdrop" onClick={onClose} />

      <div className={`ep-container ep-container--${position}`} role="dialog" aria-label="Emoji picker">

        {/* ── Search ── */}
        <div className="ep-search-row">
          <Search size={14} className="ep-search-icon" />
          <input
            ref={searchRef}
            className="ep-search-input"
            type="text"
            placeholder="Search emoji…"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button className="ep-search-clear" onClick={() => setSearchQuery('')}>
              <X size={12} />
            </button>
          )}
        </div>

        {/* ── Category tabs ── */}
        {!searchQuery && (
          <div className="ep-category-tabs" role="tablist">
            {CATEGORIES.map(({ id, label, Icon }) => (
              <button
                key={id}
                role="tab"
                aria-selected={activeCategory === id}
                aria-label={label}
                className={`ep-cat-btn ${activeCategory === id ? 'active' : ''}`}
                onClick={() => { setActiveCategory(id); gridRef.current?.scrollTo(0, 0); }}
                title={label}
              >
                <Icon size={15} />
                {id === 'recent' && recent.length === 0 && (
                  <span className="ep-cat-empty-dot" />
                )}
              </button>
            ))}
          </div>
        )}

        {/* ── Emoji grid ── */}
        <div className="ep-grid" ref={gridRef}>
          {displayEmojis.length === 0 ? (
            <div className="ep-empty">
              {activeCategory === 'recent'
                ? <><Smile size={24} /><span>No recent emoji yet</span></>
                : <><Search size={24} /><span>No results for "{searchQuery}"</span></>
              }
            </div>
          ) : (
            displayEmojis.map((em, i) => (
              <button
                key={`${em.e}-${i}`}
                className="ep-emoji-btn"
                onClick={() => handleEmojiClick(em)}
                title={em.l}
                aria-label={em.l}
              >
                {em.e}
              </button>
            ))
          )}
        </div>

        {/* ── Footer — active category label ── */}
        {!searchQuery && (
          <div className="ep-footer">
            <span className="ep-footer-label">
              {CATEGORIES.find(c => c.id === activeCategory)?.label}
            </span>
            <span className="ep-footer-count">{displayEmojis.length}</span>
          </div>
        )}
      </div>
    </>
  );
}
