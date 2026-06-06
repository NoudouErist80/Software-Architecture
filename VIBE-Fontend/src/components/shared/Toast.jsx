import React from 'react'
import { CheckCircle, AlertCircle, Info, X } from 'lucide-react'
import { useUIStore } from '../../store'
import { cn } from '../../utils'

const icons = {
  success: <CheckCircle className="w-4 h-4 text-green-500" />,
  error:   <AlertCircle className="w-4 h-4 text-red-500" />,
  info:    <Info className="w-4 h-4 text-vibe-500" />,
}
const borders = { success: 'border-l-green-500', error: 'border-l-red-500', info: 'border-l-vibe-500' }

export default function ToastContainer() {
  const toasts = useUIStore(s => s.toasts)
  return (
    <div className="fixed top-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
      {toasts.map(t => (
        <div key={t.id}
          className={cn(
            'bg-white border border-gray-100 shadow-xl rounded-xl flex items-center gap-3 px-4 py-3',
            'min-w-[280px] max-w-sm pointer-events-auto animate-slide-in-right',
            'border-l-4', borders[t.type] || borders.info
          )}>
          {icons[t.type] || icons.info}
          <span className="text-sm text-gray-700 font-medium flex-1">{t.msg}</span>
        </div>
      ))}
    </div>
  )
}
