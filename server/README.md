# VANTA Remote signaling server

This backend relays WebRTC signaling messages between at most two connected clients in a six-character room. It does not itself stream screen pixels, authenticate a device owner, or grant Android control permissions. The receiving Android device must request explicit user approval and use Android's MediaProjection consent dialog before screen capture.

## Deploy on Render

This repository includes a Render Blueprint at the repository root. In Render, create a new Blueprint and select this repository. The service is configured with:

- Runtime: Node
- Root directory: `server`
- Build command: `npm install`
- Start command: `npm start`
- Health check: `/health`

After deployment, test `https://YOUR-SERVICE.onrender.com/health`. WebSocket clients connect to `wss://YOUR-SERVICE.onrender.com/ws`.

## WebSocket protocol

1. Connect to `/ws`.
2. Send `{ "type": "join", "code": "ABC123" }`.
3. Wait for `peer_ready` when the second device joins.
4. Relay `{ "type": "offer|answer|ice", "payload": { ... } }` between peers.
5. Send `{ "type": "leave" }` or `{ "type": "session_end" }` to end the room.

Notifications include `connected`, `joined`, `peer_ready`, `peer_left`, `left`, and `error`.

## Security and production notes

- Pair codes are temporary room identifiers, not strong authentication. Generate them randomly on-device, expire them, and never reuse them.
- Add authenticated pairing, rate limiting, abuse protection, and TURN credentials before public production use.
- A TURN service may be needed when direct WebRTC connectivity fails.
- Render's free web services may sleep after inactivity and are not guaranteed 24/7.
