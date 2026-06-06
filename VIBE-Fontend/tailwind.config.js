/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        vibe: {
          50:  '#f0f8ff',
          100: '#e0f0ff',
          200: '#bae0ff',
          300: '#7ec8f5',
          400: '#38aee8',
          500: '#0d94d4',
          600: '#0077b6',
          700: '#005f8f',
          800: '#004f78',
          900: '#003d5c',
          950: '#002540',
        },
        sky: {
          light: '#e8f4fd',
          soft:  '#b3d9f5',
          mid:   '#5ab4e8',
          main:  '#0d94d4',
          deep:  '#0077b6',
        },
        surface: {
          DEFAULT: '#ffffff',
          card:    '#f8fafc',
          hover:   '#f0f7ff',
          border:  '#d1e9f7',
          muted:   '#94b8cc',
          dark:    '#0077b6',
        },
        accent: {
          gold:   '#f59e0b',
          green:  '#10b981',
          red:    '#ef4444',
          purple: '#8b5cf6',
          cyan:   '#06b6d4',
          orange: '#f97316',
        }
      },
      fontFamily: {
        display: ['"Syne"', 'sans-serif'],
        body:    ['"DM Sans"', 'sans-serif'],
        mono:    ['"JetBrains Mono"', 'monospace'],
      },
      animation: {
        'slide-in-right': 'slideInRight 0.3s ease-out',
        'slide-in-up':    'slideInUp 0.3s ease-out',
        'fade-in':        'fadeIn 0.2s ease-out',
        'pulse-glow':     'pulseGlow 2s ease-in-out infinite',
        'shimmer':        'shimmer 1.5s infinite',
        'bounce-soft':    'bounceSoft 0.5s ease-out',
      },
      keyframes: {
        slideInRight: { from: { transform: 'translateX(100%)', opacity: 0 }, to: { transform: 'translateX(0)', opacity: 1 } },
        slideInUp:    { from: { transform: 'translateY(20px)', opacity: 0 }, to: { transform: 'translateY(0)', opacity: 1 } },
        fadeIn:       { from: { opacity: 0 }, to: { opacity: 1 } },
        pulseGlow:    { '0%,100%': { boxShadow: '0 0 5px #0d94d444' }, '50%': { boxShadow: '0 0 20px #0d94d499' } },
        shimmer:      { '0%': { backgroundPosition: '-200% 0' }, '100%': { backgroundPosition: '200% 0' } },
        bounceSoft:   { '0%,100%': { transform: 'translateY(0)' }, '50%': { transform: 'translateY(-6px)' } },
      },
      backdropBlur: { xs: '2px' },
      borderRadius: { '2xl': '1rem', '3xl': '1.5rem', '4xl': '2rem' }
    }
  },
  plugins: []
}
