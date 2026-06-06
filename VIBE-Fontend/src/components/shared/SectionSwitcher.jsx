import React from 'react'
import { MessageSquare, Tv2, Zap } from 'lucide-react'
import { useUIStore, useAuthStore } from '../../store'
import { cn } from '../../utils'

export default function SectionSwitcher() {
  const { activeSection, setSection, addToast } = useUIStore()
  const { hasPublicAccount } = useAuthStore()

  return (
    <div className="flex items-center justify-center py-2 px-4 bg-white border-b border-surface-border flex-shrink-0 relative z-30 shadow-sm">
      {/* Logo */}
      <div className="absolute left-4 flex items-center gap-2">
        <img src="/vibe_appicon_512.png" alt="VIBE" className="w-8 h-8" />
        <span className="font-display font-extrabold text-vibe-700 text-lg tracking-tight hidden sm:block">VIBE</span>
      </div>

      {/* Toggle pill */}
      <div className="flex items-center bg-sky-50 rounded-xl p-1 border border-surface-border">
        <SwitchBtn active={activeSection === 'messenger'} onClick={() => setSection('messenger')}
          icon={<MessageSquare className="w-3.5 h-3.5" />} label="Messenger" />
        <SwitchBtn active={activeSection === 'public'} locked={!hasPublicAccount}
          onClick={() => {
            if (!hasPublicAccount) { addToast('Create a VIBE Public account to access content platform 🎬', 'info'); return }
            setSection('public')
          }}
          icon={<Tv2 className="w-3.5 h-3.5" />} label="VIBE Public" />
      </div>

      <div className="absolute right-4">
        <span className="text-[10px] text-gray-400 font-mono">v1.0</span>
      </div>
    </div>
  )
}

function SwitchBtn({ active, onClick, icon, label, locked }) {
  return (
    <button onClick={onClick}
      className={cn(
        'flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-display font-semibold transition-all duration-200',
        active ? 'bg-vibe-600 text-white shadow-md shadow-vibe-600/20' : 'text-gray-500 hover:text-gray-700',
        locked && !active && 'opacity-60'
      )}>
      {icon}
      <span>{label}</span>
      {locked && !active && <span className="text-[10px]">🔒</span>}
    </button>
  )
}
