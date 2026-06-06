import React, { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Eye, EyeOff, Shield, Globe, Coins, MessageSquare, Check } from 'lucide-react'
import { useAuthStore, useUIStore } from '../store'
import { authAPI, logAuthTokens } from '../services/api'
import { SUPPORTED_LANGUAGES, cn } from '../utils'

// ─── Login Page ────────────────────────────────────────────────────────────────
export function LoginPage() {
  const navigate  = useNavigate()
  const login     = useAuthStore(s => s.login)
  const addToast  = useUIStore(s => s.addToast)
  const [form, setForm]           = useState({ email: '', password: '' })
  const [showPw, setShowPw]       = useState(false)
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState('')
  const [showForgot, setShowForgot]     = useState(false)
  const [forgotStep, setForgotStep]     = useState(1)
  const [forgotEmail, setForgotEmail]   = useState('')
  const [forgotOtp, setForgotOtp]       = useState('')
  const [forgotNewPw, setForgotNewPw]   = useState('')
  const [forgotLoading, setForgotLoading] = useState(false)
  const [forgotError, setForgotError]   = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true); setError('')
    try {
      const res  = await authAPI.login(form)
      // The gateway wraps responses: res.data is ApiResponse<AuthResponse>
      // Data lives in res.data.data (ApiResponse wrapper) OR res.data directly
      const body = res.data?.data ?? res.data
      const user  = body.user
      const token = body.accessToken

      login(user, token)

      // ── Log tokens to DevTools console for inspection ──────────────────────
      logAuthTokens('Login', token, user)

      addToast(`Welcome back, ${user.fullName?.split(' ')[0] || user.username}! 🎉`, 'success')
      navigate('/app')
    } catch (err) {
      const msg = err.response?.data?.data?.message
               || err.response?.data?.message
               || 'Login failed. Check your credentials.'
      setError(msg)
      console.error('❌ Login error:', err.response?.data)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <div className="w-full max-w-sm mx-auto">
        <h2 className="font-display text-2xl font-bold text-gray-900 mb-1">Welcome back</h2>
        <p className="text-gray-500 text-sm mb-7">Sign in to your VIBE account</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <AuthInput label="Email or Username" type="text" value={form.email}
            onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
            placeholder="you@example.com or @username" required />
          <AuthInput label="Password" type={showPw ? 'text' : 'password'}
            value={form.password}
            onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
            placeholder="••••••••" required
            suffix={
              <button type="button" onClick={() => setShowPw(p => !p)}
                className="text-gray-400 hover:text-vibe-600 transition-colors">
                {showPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            } />
          {error && <p className="text-red-500 text-sm bg-red-50 px-3 py-2 rounded-lg">{error}</p>}
          <button type="submit" disabled={loading} className="btn-vibe w-full py-3 disabled:opacity-50">
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
        <p className="text-center text-sm text-gray-500 mt-4">
          <button type="button" onClick={() => setShowForgot(true)}
            className="text-vibe-600 hover:text-vibe-700 font-semibold text-sm">
            Forgot Password?
          </button>
        </p>
        <p className="text-center text-sm text-gray-500 mt-2">
          New to VIBE?{' '}
          <Link to="/auth/register" className="text-vibe-600 hover:text-vibe-700 font-semibold">Create account</Link>
        </p>
      </div>

      {/* Forgot Password Modal */}
      {showForgot && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-8 w-full max-w-sm shadow-2xl">
            {forgotStep === 1 && (
              <>
                <h2 className="font-bold text-xl text-gray-900 mb-2">Reset Password</h2>
                <p className="text-gray-500 text-sm mb-5">Enter your email — the OTP will be logged to the server console in dev mode.</p>
                <input type="email" value={forgotEmail} onChange={e => setForgotEmail(e.target.value)}
                  placeholder="your@email.com"
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm mb-4 focus:outline-none focus:border-vibe-400" />
                {forgotError && <p className="text-red-500 text-sm mb-3">{forgotError}</p>}
                <button onClick={async () => {
                  if (!forgotEmail) return
                  setForgotError(''); setForgotLoading(true)
                  try {
                    await authAPI.forgotPassword(forgotEmail)
                    setForgotStep(2)
                  } catch (e) { setForgotError(e.response?.data?.message || 'Failed') }
                  finally { setForgotLoading(false) }
                }} disabled={forgotLoading}
                  className="w-full py-3 bg-vibe-600 text-white rounded-xl font-semibold text-sm hover:bg-vibe-500 disabled:opacity-50">
                  {forgotLoading ? 'Sending…' : 'Send OTP'}
                </button>
              </>
            )}
            {forgotStep === 2 && (
              <>
                <h2 className="font-bold text-xl text-gray-900 mb-2">Enter OTP</h2>
                <p className="text-gray-500 text-sm mb-5">Check the <strong>auth-service console</strong> for the OTP, then enter your new password.</p>
                <input type="text" value={forgotOtp} onChange={e => setForgotOtp(e.target.value)}
                  placeholder="123456" maxLength={6}
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm mb-3 focus:outline-none focus:border-vibe-400" />
                <input type="password" value={forgotNewPw} onChange={e => setForgotNewPw(e.target.value)}
                  placeholder="New password (min 8 chars)"
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm mb-4 focus:outline-none focus:border-vibe-400" />
                {forgotError && <p className="text-red-500 text-sm mb-3">{forgotError}</p>}
                <button onClick={async () => {
                  setForgotError(''); setForgotLoading(true)
                  try {
                    await authAPI.resetPassword({ email: forgotEmail, otp: forgotOtp, newPassword: forgotNewPw })
                    setShowForgot(false); setForgotStep(1)
                    setForgotEmail(''); setForgotOtp(''); setForgotNewPw('')
                    addToast('Password reset! Please sign in.', 'success')
                  } catch (e) { setForgotError(e.response?.data?.message || 'Invalid OTP') }
                  finally { setForgotLoading(false) }
                }} disabled={forgotLoading}
                  className="w-full py-3 bg-vibe-600 text-white rounded-xl font-semibold text-sm hover:bg-vibe-500 disabled:opacity-50">
                  {forgotLoading ? 'Resetting…' : 'Reset Password'}
                </button>
              </>
            )}
            <button onClick={() => { setShowForgot(false); setForgotStep(1) }}
              className="mt-4 w-full text-center text-sm text-gray-400 hover:text-gray-600">Cancel</button>
          </div>
        </div>
      )}
    </AuthLayout>
  )
}

// ─── Register Page ─────────────────────────────────────────────────────────────
export function RegisterPage() {
  const navigate  = useNavigate()
  const login     = useAuthStore(s => s.login)
  const addToast  = useUIStore(s => s.addToast)
  const [step, setStep]   = useState(1)
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')
  const [form, setForm] = useState({
    fullName: '', username: '', email: '',
    phoneNumber: '', password: '', confirmPassword: '',
    preferredLanguage: 'en', countryCode: 'CM'
  })
  const setF = (k, v) => setForm(f => ({ ...f, [k]: v }))

  const [phoneOtp, setPhoneOtp]             = useState('')
  const [otpResendTimer, setOtpResendTimer] = useState(0)

  const handleSubmit = async (e) => {
    e.preventDefault()

    // Step 1 → move to step 2
    if (step === 1) { setStep(2); return }

    // Step 2 → submit registration
    if (step === 2) {
      if (form.password !== form.confirmPassword) { setError('Passwords do not match'); return }
      setLoading(true); setError('')
      try {
        const payload = {
          fullName:          form.fullName,
          username:          form.username.toLowerCase().replace(/\s/g, ''),
          email:             form.email.toLowerCase(),
          password:          form.password,
          phoneNumber:       form.phoneNumber || null,
          countryCode:       form.countryCode,
          preferredLanguage: form.preferredLanguage,
        }
        console.log('📤 Registration payload:', payload)

        const res  = await authAPI.register(payload)
        // Handle both direct and wrapped response shapes
        const body = res.data?.data ?? res.data
        const user  = body.user
        const token = body.accessToken

        console.log('✅ Registration response (raw):', res.data)

        login(user, token)

        // ── Log tokens to DevTools console ────────────────────────────────
        logAuthTokens('Registration', token, user)

        if (form.phoneNumber) {
          await authAPI.sendPhoneOtp(form.phoneNumber).catch(() => {})
          setOtpResendTimer(60)
          setStep(3)
        } else {
          addToast(`Welcome to VIBE, ${user.fullName?.split(' ')[0] || user.username}! 🚀`, 'success')
          navigate('/app')
        }
      } catch (err) {
        console.error('❌ Registration error:', err.response?.data)
        const errBody = err.response?.data
        // Field-level validation errors come in errBody.data as a map
        const fieldErrors = errBody?.data && typeof errBody.data === 'object'
          ? Object.entries(errBody.data).map(([f, m]) => `${f}: ${m}`).join('. ')
          : null
        setError(fieldErrors || errBody?.message || 'Registration failed. Please try again.')
      } finally { setLoading(false) }
      return
    }

    // Step 3 – phone OTP verification
    if (step === 3) {
      setLoading(true); setError('')
      try {
        await authAPI.verifyPhone(form.phoneNumber, phoneOtp)
        addToast('Phone verified! Welcome to VIBE 🚀', 'success')
        navigate('/app')
      } catch (err) {
        setError(err.response?.data?.message || 'Invalid OTP')
      } finally { setLoading(false) }
    }
  }

  useEffect(() => {
    if (otpResendTimer <= 0) return
    const t = setInterval(() => setOtpResendTimer(n => n - 1), 1000)
    return () => clearInterval(t)
  }, [otpResendTimer])

  return (
    <AuthLayout>
      <div className="w-full max-w-sm mx-auto">
        <h2 className="font-display text-2xl font-bold text-gray-900 mb-1">Join VIBE</h2>
        <p className="text-gray-500 text-sm mb-5">Africa's communication & content platform</p>

        {/* Step bar */}
        <div className="flex gap-2 mb-6">
          {[1, 2].map(s => (
            <div key={s} className={cn('h-1.5 flex-1 rounded-full transition-all duration-300',
              step >= s ? 'bg-vibe-500' : 'bg-gray-200')} />
          ))}
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {step === 1 && <>
            <AuthInput label="Full Name" value={form.fullName}
              onChange={e => setF('fullName', e.target.value)}
              placeholder="Tchango Noudou Joseph" required />
            <AuthInput label="Username" value={form.username}
              onChange={e => setF('username', e.target.value.toLowerCase().replace(/\s/g,''))}
              placeholder="tchanjo" required
              prefix={<span className="text-gray-400 text-sm">@</span>} />
            <AuthInput label="Email" type="email" value={form.email}
              onChange={e => setF('email', e.target.value)}
              placeholder="you@example.com" required />
            <button type="submit" className="btn-vibe w-full py-3">Continue →</button>
          </>}

          {step === 2 && <>
            <AuthInput label="Phone Number (optional)" type="tel" value={form.phoneNumber}
              onChange={e => setF('phoneNumber', e.target.value)}
              placeholder="+237 6XX XXX XXX" />

            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">Preferred Language</label>
              <select value={form.preferredLanguage}
                onChange={e => setF('preferredLanguage', e.target.value)}
                className="vibe-input">
                {SUPPORTED_LANGUAGES.map(l => (
                  <option key={l.code} value={l.code}>{l.flag} {l.label}</option>
                ))}
              </select>
            </div>

            <AuthInput label="Password" type={showPw ? 'text' : 'password'}
              value={form.password} onChange={e => setF('password', e.target.value)}
              placeholder="Min. 8 chars, uppercase + number" required
              suffix={
                <button type="button" onClick={() => setShowPw(p => !p)}
                  className="text-gray-400 hover:text-vibe-600 transition-colors">
                  {showPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              } />
            <AuthInput label="Confirm Password" type="password"
              value={form.confirmPassword} onChange={e => setF('confirmPassword', e.target.value)}
              placeholder="••••••••" required />

            {error && <p className="text-red-500 text-sm bg-red-50 px-3 py-2 rounded-lg">{error}</p>}

            <div className="flex gap-3">
              <button type="button" onClick={() => setStep(1)}
                className="btn-vibe-outline flex-1 py-3 text-sm">← Back</button>
              <button type="submit" disabled={loading} className="btn-vibe flex-1 py-3 disabled:opacity-50">
                {loading ? 'Creating…' : 'Create Account'}
              </button>
            </div>
          </>}
        </form>

        {step === 3 && (
          <div className="space-y-4">
            <div className="text-center">
              <p className="text-2xl mb-2">📱</p>
              <p className="text-sm text-gray-600">Enter the 6-digit OTP sent to <strong>{form.phoneNumber}</strong></p>
              <p className="text-xs text-gray-400 mt-1">(Check the auth-service terminal in dev mode)</p>
            </div>
            <input type="text" value={phoneOtp} onChange={e => setPhoneOtp(e.target.value)}
              placeholder="123456" maxLength={6}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-center text-2xl tracking-[0.5em] font-mono focus:outline-none focus:border-vibe-400" />
            <button onClick={handleSubmit} disabled={loading || phoneOtp.length !== 6}
              className="w-full py-3.5 bg-vibe-600 text-white rounded-2xl font-semibold text-sm hover:bg-vibe-500 disabled:opacity-50 transition-all">
              {loading ? 'Verifying…' : 'Verify Phone ✓'}
            </button>
            <button type="button" onClick={() => { navigate('/app'); addToast('Welcome to VIBE! 🚀', 'success') }}
              className="w-full text-center text-sm text-gray-400 hover:text-gray-600">Skip for now</button>
            <div className="text-center">
              {otpResendTimer > 0
                ? <p className="text-xs text-gray-400">Resend in {otpResendTimer}s</p>
                : <button type="button" onClick={async () => {
                    await authAPI.sendPhoneOtp(form.phoneNumber).catch(() => {})
                    setOtpResendTimer(60)
                    addToast('OTP resent!', 'info')
                  }} className="text-xs text-vibe-600 hover:text-vibe-700">Resend OTP</button>}
            </div>
          </div>
        )}

        <p className="text-center text-sm text-gray-500 mt-6">
          Already on VIBE?{' '}
          <Link to="/auth/login" className="text-vibe-600 hover:text-vibe-700 font-semibold">Sign in</Link>
        </p>
      </div>
    </AuthLayout>
  )
}

// ─── Shared Auth Layout — split screen ────────────────────────────────────────
function AuthLayout({ children }) {
  return (
    <div className="min-h-screen flex">
      {/* Left hero panel — hidden on mobile */}
      <div className="hidden lg:flex lg:w-1/2 vibe-hero-bg flex-col items-center justify-center p-12 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-64 bg-white/5 rounded-full -translate-y-1/2 translate-x-1/2" />
        <div className="absolute bottom-0 left-0 w-96 h-96 bg-white/5 rounded-full translate-y-1/2 -translate-x-1/2" />

        <div className="relative z-10 flex flex-col items-center mb-10">
          <img src="/vibe_appicon_512.png" alt="VIBE" className="w-24 h-24 mb-6 drop-shadow-2xl" />
          <h1 className="font-display text-5xl font-extrabold text-white tracking-tight mb-2">VIBE</h1>
          <p className="text-sky-200 text-sm font-medium tracking-widest uppercase">Vision Into Being Everywhere</p>
        </div>

        <div className="relative z-10 max-w-sm text-center mb-10">
          <p className="text-white/90 text-lg leading-relaxed font-medium">
            Africa's first platform where you <span className="text-sky-200 font-bold">communicate</span>,
            {' '}<span className="text-sky-200 font-bold">create content</span>, and
            {' '}<span className="text-sky-200 font-bold">earn real money</span> — in your language.
          </p>
        </div>

        <div className="relative z-10 space-y-3 w-full max-w-xs">
          {[
            { icon: <MessageSquare className="w-4 h-4" />, text: 'Message in any African language' },
            { icon: <Coins className="w-4 h-4" />, text: 'Earn tokens for every interaction' },
            { icon: <Globe className="w-4 h-4" />, text: 'AI translation across 11 languages' },
            { icon: <Shield className="w-4 h-4" />, text: 'End-to-end encrypted messaging' },
          ].map(f => (
            <div key={f.text} className="flex items-center gap-3 bg-white/10 rounded-xl px-4 py-3">
              <div className="text-sky-200">{f.icon}</div>
              <span className="text-white text-sm">{f.text}</span>
              <Check className="w-4 h-4 text-sky-300 ml-auto" />
            </div>
          ))}
        </div>

        <p className="relative z-10 text-white/40 text-xs mt-10">Built in Africa, for the world 🌍</p>
      </div>

      {/* Right form panel */}
      <div className="flex-1 flex flex-col">
        <div className="lg:hidden flex items-center justify-center py-8 vibe-hero-bg">
          <img src="/vibe_appicon_512.png" alt="VIBE" className="w-14 h-14 mr-3" />
          <span className="font-display text-3xl font-extrabold text-white">VIBE</span>
        </div>

        <div className="flex-1 flex items-center justify-center p-6 lg:p-12 bg-white">
          <div className="w-full max-w-sm">
            {children}
            <p className="text-center text-xs text-gray-400 mt-8 flex items-center justify-center gap-1.5">
              <Shield className="w-3 h-3" /> Secured · Encrypted · Private
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Reusable Input ────────────────────────────────────────────────────────────
function AuthInput({ label, prefix, suffix, className, ...props }) {
  return (
    <div>
      {label && <label className="block text-xs font-semibold text-gray-600 mb-1.5 uppercase tracking-wide">{label}</label>}
      <div className="relative flex items-center">
        {prefix && <div className="absolute left-4 pointer-events-none">{prefix}</div>}
        <input
          {...props}
          className={cn(
            'vibe-input',
            prefix ? 'pl-8' : '',
            suffix ? 'pr-10' : '',
            className
          )}
        />
        {suffix && <div className="absolute right-4">{suffix}</div>}
      </div>
    </div>
  )
}