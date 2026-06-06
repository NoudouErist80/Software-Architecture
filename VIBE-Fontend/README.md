# VIBE Frontend

React + Vite frontend for the VIBE African social media platform.

## Prerequisites

- Node.js 18+
- The VIBE backend running (`docker-compose up --build -d` in `VIBE_Backend_UPDATED/`)

## Start

```bash
npm install
npm run dev
```

Open **http://localhost:3000**

## Environment

`.env` file (already configured):
```
VITE_API_URL=http://localhost:8090
VITE_WS_URL=http://localhost:8090/ws
```

The frontend calls the API Gateway on port 8090 only — never directly to individual services.

## Tech Stack

| Library | Purpose |
|---------|---------|
| React 18 | UI framework |
| Vite | Build tool + dev server |
| Zustand | State management |
| Axios | HTTP client |
| @stomp/stompjs + sockjs-client | WebSocket (real-time messages, typing, notifications) |
| Lucide React | Icons |
| TailwindCSS | Styling |

## State Stores (Zustand)

| Store | Contents |
|-------|---------|
| `useAuthStore` | user, token, isAuthenticated |
| `useMessagingStore` | conversations, messages, selectedConversation |
| `useWalletStore` | balance, transactions |
| `useFeedStore` | posts, statuses |
| `useContactsStore` | contacts, blocked |
| `useUIStore` | toasts, modals, active section |

## API Services (`src/services/api.js`)

All HTTP calls go through a single Axios instance with JWT auto-attach.
Exports: `authAPI`, `contactsAPI`, `messagingAPI`, `mediaAPI`, `aiAPI`, `feedAPI`, `statusAPI`, `roomsAPI`, `rewardsAPI`, `notificationAPI`

## WebSocket (`src/services/websocket.js`)

Singleton `wsService` — connects on login, auto-reconnects every 5s.
- `wsService.subscribeToConversation(id, cb)` — new messages
- `wsService.subscribeToTyping(id, cb)` — typing indicators
- `wsService.subscribeToNotifications(cb)` — push notifications
- `wsService.sendTyping(id, userId, isTyping)` — broadcast typing state

## Build for Production

```bash
npm run build
# Output in dist/
```
