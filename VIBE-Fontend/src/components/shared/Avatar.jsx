import React from 'react'
import { cn, getInitials, stringToColor } from '../../utils'

/**
 * Avatar component — renders a profile picture or a coloured initial fallback.
 *
 * Null-safe: `name` and `src` may both be null/undefined without crashing.
 * The colour is deterministic (same name → same colour) so avatars look
 * consistent across page loads, exactly like WhatsApp / Messenger.
 */
export default function Avatar({
  src,
  name = '',
  size = 'md',
  online,
  className,
  onClick,
}) {
  // Normalise so internal helpers never receive null/undefined
  const safeName = name || ''

  const sizes = {
    xs:  'w-6  h-6  text-[10px]',
    sm:  'w-8  h-8  text-xs',
    md:  'w-10 h-10 text-sm',
    lg:  'w-12 h-12 text-base',
    xl:  'w-16 h-16 text-lg',
    '2xl': 'w-20 h-20 text-xl',
  }

  const dotSizes = {
    xs:  'w-1.5 h-1.5',
    sm:  'w-2   h-2',
    md:  'w-2.5 h-2.5',
    lg:  'w-3   h-3',
    xl:  'w-3.5 h-3.5',
    '2xl': 'w-4 h-4',
  }

  return (
    <div
      className={cn('relative flex-shrink-0', className)}
      onClick={onClick}
      style={{ cursor: onClick ? 'pointer' : 'default' }}
    >
      <div
        className={cn(
          'rounded-full overflow-hidden flex items-center justify-center',
          'font-display font-semibold text-white select-none',
          sizes[size] ?? sizes.md,
        )}
        style={{ background: src ? undefined : stringToColor(safeName) }}
      >
        {src ? (
          <img
            src={src}
            alt={safeName}
            className="w-full h-full object-cover"
            onError={e => { e.currentTarget.style.display = 'none' }}
          />
        ) : (
          <span aria-label={safeName}>{getInitials(safeName)}</span>
        )}
      </div>

      {online !== undefined && (
        <span
          className={cn(
            'absolute bottom-0 right-0 rounded-full border-2 border-white',
            dotSizes[size] ?? dotSizes.md,
            online ? 'bg-green-500' : 'bg-gray-300',
          )}
        />
      )}
    </div>
  )
}