// /**
//  * AddContactModal.jsx
//  * Directory: vibe-frontend-v2/src/components/messenger/AddContactModal.jsx
//  *
//  * WhatsApp-style "Save Contact" flow for VIBE:
//  *  1. User fills in a display name (what they want to save the contact as)
//  *     and a phone number or VIBE username.
//  *  2. On "Save Contact" the app calls the backend to check if that
//  *     phone/username belongs to a registered VIBE account.
//  *  3. If found   → contact is saved and appears in the sidebar.
//  *  4. If NOT found → a styled "Not a VIBE Contact" advisory card is shown
//  *     (mirrors WhatsApp's "Invite to WhatsApp" pattern).
//  *
//  * Scales to billions: validation is a single lightweight GET to the
//  * user-service; no heavy data is transferred until the contact is confirmed.
//  */

// import React, { useState, useCallback, useRef } from 'react';
// import { X, UserPlus, Phone, AtSign, User, AlertTriangle, CheckCircle, Loader2, Send, ChevronDown } from 'lucide-react';
// import { vibeApi } from '../../services/api';
// import useVibeStore from '../../store/index';

// // ─── Country codes (Africa-first, then global) ────────────────────────────────
// const COUNTRY_CODES = [
//   { flag: '🇨🇲', name: 'Cameroon',      code: '+237', iso: 'CM' },
//   { flag: '🇳🇬', name: 'Nigeria',       code: '+234', iso: 'NG' },
//   { flag: '🇬🇭', name: 'Ghana',         code: '+233', iso: 'GH' },
//   { flag: '🇨🇮', name: "Côte d'Ivoire", code: '+225', iso: 'CI' },
//   { flag: '🇸🇳', name: 'Senegal',       code: '+221', iso: 'SN' },
//   { flag: '🇰🇪', name: 'Kenya',         code: '+254', iso: 'KE' },
//   { flag: '🇿🇦', name: 'South Africa',  code: '+27',  iso: 'ZA' },
//   { flag: '🇪🇹', name: 'Ethiopia',      code: '+251', iso: 'ET' },
//   { flag: '🇺🇸', name: 'United States', code: '+1',   iso: 'US' },
//   { flag: '🇬🇧', name: 'United Kingdom',code: '+44',  iso: 'GB' },
//   { flag: '🇫🇷', name: 'France',        code: '+33',  iso: 'FR' },
// ];

// // ─── Input mode toggle ────────────────────────────────────────────────────────
// const INPUT_MODES = {
//   PHONE:    'phone',
//   USERNAME: 'username',
// };

// export default function AddContactModal({ isOpen, onClose }) {
//   const { addContact, currentUser } = useVibeStore();

//   // Form state
//   const [displayName,   setDisplayName]   = useState('');
//   const [inputMode,     setInputMode]     = useState(INPUT_MODES.PHONE);
//   const [phoneNumber,   setPhoneNumber]   = useState('');
//   const [username,      setUsername]      = useState('');
//   const [countryCode,   setCountryCode]   = useState(COUNTRY_CODES[0]);
//   const [showCountries, setShowCountries] = useState(false);

//   // Async state
//   const [status,    setStatus]    = useState('idle'); // idle | loading | found | not_found | error
//   const [foundUser, setFoundUser] = useState(null);
//   const [errorMsg,  setErrorMsg]  = useState('');

//   const countryDropRef = useRef(null);

//   // ─── Reset ─────────────────────────────────────────────────────────────────
//   const reset = useCallback(() => {
//     setDisplayName('');
//     setPhoneNumber('');
//     setUsername('');
//     setStatus('idle');
//     setFoundUser(null);
//     setErrorMsg('');
//     setShowCountries(false);
//   }, []);

//   const handleClose = useCallback(() => {
//     reset();
//     onClose();
//   }, [reset, onClose]);

//   // ─── Validate VIBE membership ───────────────────────────────────────────────
//   const checkVibeAccount = useCallback(async () => {
//     if (!displayName.trim()) {
//       setErrorMsg('Please enter a name for this contact.');
//       setStatus('error');
//       return;
//     }

//     const identifier =
//       inputMode === INPUT_MODES.PHONE
//         ? `${countryCode.code}${phoneNumber.replace(/^0+/, '').trim()}`
//         : username.trim();

//     if (!identifier || identifier === countryCode.code) {
//       setErrorMsg(
//         inputMode === INPUT_MODES.PHONE
//           ? 'Please enter a phone number.'
//           : 'Please enter a VIBE username.'
//       );
//       setStatus('error');
//       return;
//     }

//     setStatus('loading');
//     setFoundUser(null);
//     setErrorMsg('');

//     try {
//       /**
//        * POST /api/contacts/lookup
//        * Body: { identifier: "+237612345678" | "@username" }
//        * Returns: { found: bool, user?: { id, username, displayName, avatarUrl, phone } }
//        *
//        * The endpoint is intentionally lean — it only returns the minimum
//        * needed to confirm VIBE membership and render the preview card.
//        */
//       const res = await vibeApi.post('/contacts/lookup', {
//         identifier,
//         type: inputMode,
//       });

//       if (res.data?.found && res.data?.user) {
//         setFoundUser(res.data.user);
//         setStatus('found');
//       } else {
//         setStatus('not_found');
//       }
//     } catch (err) {
//       if (err.response?.status === 404) {
//         setStatus('not_found');
//       } else {
//         setErrorMsg('Unable to reach VIBE servers. Please check your connection.');
//         setStatus('error');
//       }
//     }
//   }, [inputMode, displayName, phoneNumber, username, countryCode]);

//   // ─── Save confirmed contact ─────────────────────────────────────────────────
//   const saveContact = useCallback(async () => {
//     if (!foundUser) return;
//     setStatus('loading');

//     try {
//       /**
//        * POST /api/contacts
//        * Body: { vibeUserId, displayName }
//        * The backend creates the contact row in the contacts table and
//        * returns the full Contact object to insert into local state.
//        */
//       const res = await vibeApi.post('/contacts', {
//         vibeUserId:  foundUser.id,
//         displayName: displayName.trim() || foundUser.displayName,
//       });

//       addContact(res.data);   // update Zustand store → sidebar re-renders
//       handleClose();
//     } catch (err) {
//       setErrorMsg('Failed to save contact. Please try again.');
//       setStatus('found'); // revert so user can retry
//     }
//   }, [foundUser, displayName, addContact, handleClose]);

//   if (!isOpen) return null;

//   // ─── Render ─────────────────────────────────────────────────────────────────
//   return (
//     <div className="add-contact-overlay" onClick={e => e.target === e.currentTarget && handleClose()}>
//       <div className="add-contact-modal" role="dialog" aria-modal="true" aria-label="Add VIBE Contact">

//         {/* ── Header ── */}
//         <div className="acm-header">
//           <div className="acm-header-icon">
//             <UserPlus size={20} />
//           </div>
//           <h2 className="acm-title">Add Contact</h2>
//           <button className="acm-close-btn" onClick={handleClose} aria-label="Close">
//             <X size={18} />
//           </button>
//         </div>

//         {/* ── Body ── */}
//         <div className="acm-body">

//           {/* Display name -------------------------------------------------- */}
//           <div className="acm-field-group">
//             <label className="acm-label">
//               <User size={14} className="acm-label-icon" />
//               Name
//             </label>
//             <input
//               className="acm-input"
//               type="text"
//               placeholder="e.g. Mama Ngono 😊"
//               value={displayName}
//               onChange={e => { setDisplayName(e.target.value); setStatus('idle'); }}
//               maxLength={50}
//               autoFocus
//             />
//           </div>

//           {/* Input mode toggle ---------------------------------------------- */}
//           <div className="acm-mode-toggle">
//             <button
//               className={`acm-mode-btn ${inputMode === INPUT_MODES.PHONE ? 'active' : ''}`}
//               onClick={() => { setInputMode(INPUT_MODES.PHONE); setStatus('idle'); }}
//             >
//               <Phone size={13} /> Phone
//             </button>
//             <button
//               className={`acm-mode-btn ${inputMode === INPUT_MODES.USERNAME ? 'active' : ''}`}
//               onClick={() => { setInputMode(INPUT_MODES.USERNAME); setStatus('idle'); }}
//             >
//               <AtSign size={13} /> Username
//             </button>
//           </div>

//           {/* Phone / Username input ----------------------------------------- */}
//           {inputMode === INPUT_MODES.PHONE ? (
//             <div className="acm-field-group">
//               <label className="acm-label">
//                 <Phone size={14} className="acm-label-icon" />
//                 Phone number
//               </label>
//               <div className="acm-phone-row">
//                 {/* Country code picker */}
//                 <div className="acm-country-wrapper" ref={countryDropRef}>
//                   <button
//                     className="acm-country-btn"
//                     onClick={() => setShowCountries(v => !v)}
//                     aria-label="Select country code"
//                   >
//                     <span>{countryCode.flag}</span>
//                     <span className="acm-country-code">{countryCode.code}</span>
//                     <ChevronDown size={12} className={showCountries ? 'rotated' : ''} />
//                   </button>

//                   {showCountries && (
//                     <div className="acm-country-dropdown">
//                       {COUNTRY_CODES.map(cc => (
//                         <button
//                           key={cc.iso}
//                           className={`acm-country-option ${cc.iso === countryCode.iso ? 'selected' : ''}`}
//                           onClick={() => { setCountryCode(cc); setShowCountries(false); setStatus('idle'); }}
//                         >
//                           <span>{cc.flag}</span>
//                           <span className="acm-country-name">{cc.name}</span>
//                           <span className="acm-country-code">{cc.code}</span>
//                         </button>
//                       ))}
//                     </div>
//                   )}
//                 </div>

//                 <input
//                   className="acm-input acm-phone-input"
//                   type="tel"
//                   placeholder="612 345 678"
//                   value={phoneNumber}
//                   onChange={e => { setPhoneNumber(e.target.value.replace(/[^\d\s\-]/g, '')); setStatus('idle'); }}
//                   maxLength={15}
//                 />
//               </div>
//             </div>
//           ) : (
//             <div className="acm-field-group">
//               <label className="acm-label">
//                 <AtSign size={14} className="acm-label-icon" />
//                 VIBE Username
//               </label>
//               <div className="acm-username-row">
//                 <span className="acm-at-prefix">@</span>
//                 <input
//                   className="acm-input acm-username-input"
//                   type="text"
//                   placeholder="vibeuser123"
//                   value={username}
//                   onChange={e => { setUsername(e.target.value.replace(/[^a-zA-Z0-9_\.]/g, '')); setStatus('idle'); }}
//                   maxLength={30}
//                 />
//               </div>
//             </div>
//           )}

//           {/* ── Status cards ── */}

//           {/* Error message */}
//           {status === 'error' && (
//             <div className="acm-advisory acm-advisory--error">
//               <AlertTriangle size={16} />
//               <span>{errorMsg}</span>
//             </div>
//           )}

//           {/* Not a VIBE contact — WhatsApp-style advisory */}
//           {status === 'not_found' && (
//             <div className="acm-not-vibe-card">
//               <div className="acm-not-vibe-icon">
//                 <span className="acm-vibe-logo-text">V</span>
//               </div>
//               <div className="acm-not-vibe-content">
//                 <p className="acm-not-vibe-headline">Not on VIBE yet</p>
//                 <p className="acm-not-vibe-body">
//                   {inputMode === INPUT_MODES.PHONE
//                     ? `${countryCode.code} ${phoneNumber}`
//                     : `@${username}`}{' '}
//                   doesn't have a VIBE account. Invite them to join and start messaging!
//                 </p>
//                 <button
//                   className="acm-invite-btn"
//                   onClick={() => {
//                     const inviteText = encodeURIComponent(
//                       `Hey! Join me on VIBE — the African messaging platform. Download it at https://vibe.app 🌍`
//                     );
//                     window.open(`sms:?body=${inviteText}`, '_blank');
//                   }}
//                 >
//                   <Send size={13} /> Invite to VIBE
//                 </button>
//               </div>
//             </div>
//           )}

//           {/* Found — preview card */}
//           {status === 'found' && foundUser && (
//             <div className="acm-found-card">
//               <div className="acm-found-avatar">
//                 {foundUser.avatarUrl
//                   ? <img src={foundUser.avatarUrl} alt={foundUser.displayName} />
//                   : <span>{(foundUser.displayName || '?')[0].toUpperCase()}</span>
//                 }
//                 <span className="acm-found-verified">
//                   <CheckCircle size={14} />
//                 </span>
//               </div>
//               <div className="acm-found-info">
//                 <p className="acm-found-name">{foundUser.displayName}</p>
//                 <p className="acm-found-handle">@{foundUser.username}</p>
//                 <p className="acm-found-badge">✓ VIBE member</p>
//               </div>
//             </div>
//           )}
//         </div>

//         {/* ── Footer actions ── */}
//         <div className="acm-footer">
//           <button className="acm-btn acm-btn--cancel" onClick={handleClose}>
//             Cancel
//           </button>

//           {status !== 'found' ? (
//             <button
//               className="acm-btn acm-btn--primary"
//               onClick={checkVibeAccount}
//               disabled={status === 'loading'}
//             >
//               {status === 'loading'
//                 ? <><Loader2 size={15} className="spin" /> Checking…</>
//                 : <><UserPlus size={15} /> Save Contact</>
//               }
//             </button>
//           ) : (
//             <button
//               className="acm-btn acm-btn--confirm"
//               onClick={saveContact}
//               disabled={status === 'loading'}
//             >
//               {status === 'loading'
//                 ? <><Loader2 size={15} className="spin" /> Saving…</>
//                 : <><CheckCircle size={15} /> Confirm & Save</>
//               }
//             </button>
//           )}
//         </div>
//       </div>
//     </div>
//   );
// }
