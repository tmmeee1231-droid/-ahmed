# VANTA Remote — signaling server

This folder contains the WebSocket signaling service used to help two VANTA Remote clients exchange WebRTC connection messages. It does **not** stream a screen, implement Android remote control, or grant Android permissions by itself.

## Deploy on Render

Create a new **Web Service** from the GitHub repository and set:

- Runtime: Node
- Root Directory: `server`
- Build Command: `npm install`
- Start Command: `npm start`
- Health Check Path: `/health`

After deployment, the WebSocket endpoint is:

`wss://YOUR-SERVICE.onrender.com/ws`

The HTTP health endpoint is:

`https://YOUR-SERVICE.onrender.com/health`

## Important safety and production notes

- Screen sharing on Android must use the system MediaProjection consent prompt. The remote user must knowingly approve the session and be able to stop it.
- The six-character room code is only a temporary room identifier, not strong authentication.
- Before public production use, add authenticated pairing, room-code expiration, rate limiting, abuse protection, and properly managed TURN credentials.
- Render's free service may sleep after inactivity and does not guarantee 24/7 availability.
- This service is signaling only; a compatible Android client and WebRTC/STUN/TURN configuration are still required.
