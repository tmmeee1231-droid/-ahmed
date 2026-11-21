'use strict';

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = Number(process.env.PORT || 10000);
const MAX_CLIENTS_PER_ROOM = 2;
const rooms = new Map();

function json(res, status, body) {
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store'
  });
  res.end(JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  if (req.url === '/health' && req.method === 'GET') {
    return json(res, 200, {
      ok: true,
      service: 'vanta-signaling',
      time: new Date().toISOString()
    });
  }

  if (req.url === '/' && req.method === 'GET') {
    return json(res, 200, {
      service: 'VANTA Remote signaling',
      websocket: '/ws',
      health: '/health'
    });
  }

  return json(res, 404, { error: 'not_found' });
});

const wss = new WebSocketServer({
  server,
  path: '/ws',
  maxPayload: 64 * 1024
});

function send(ws, data) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
}

function leave(ws) {
  if (!ws.roomCode) return;

  const room = rooms.get(ws.roomCode);
  if (room) {
    room.delete(ws);
    for (const peer of room) send(peer, { type: 'peer_left' });
    if (room.size === 0) rooms.delete(ws.roomCode);
  }

  ws.roomCode = null;
}

function validCode(code) {
  return typeof code === 'string' && /^[A-Z0-9]{6}$/.test(code);
}

wss.on('connection', (ws) => {
  ws.isAlive = true;
  ws.on('pong', () => {
    ws.isAlive = true;
  });

  send(ws, { type: 'connected', protocol: 1 });

  ws.on('message', (raw) => {
    let msg;

    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return send(ws, { type: 'error', code: 'bad_json' });
    }

    if (!msg || typeof msg.type !== 'string') {
      return send(ws, { type: 'error', code: 'bad_message' });
    }

    if (msg.type === 'join') {
      if (ws.roomCode) leave(ws);

      const code = String(msg.code || '').toUpperCase();
      if (!validCode(code)) {
        return send(ws, { type: 'error', code: 'invalid_pair_code' });
      }

      let room = rooms.get(code);
      if (!room) {
        room = new Set();
        rooms.set(code, room);
      }

      if (room.size >= MAX_CLIENTS_PER_ROOM) {
        return send(ws, { type: 'error', code: 'room_full' });
      }

      room.add(ws);
      ws.roomCode = code;
      send(ws, { type: 'joined', peers: room.size });

      if (room.size === 2) {
        for (const peer of room) send(peer, { type: 'peer_ready' });
      }
      return;
    }

    if (msg.type === 'leave') {
      leave(ws);
      return send(ws, { type: 'left' });
    }

    const allowed = new Set(['offer', 'answer', 'ice', 'session_end']);
    if (!allowed.has(msg.type)) {
      return send(ws, { type: 'error', code: 'unknown_type' });
    }

    if (!ws.roomCode) {
      return send(ws, { type: 'error', code: 'not_in_room' });
    }

    const room = rooms.get(ws.roomCode);
    if (!room) return leave(ws);

    const payload = msg.payload;
    if (JSON.stringify(payload ?? null).length > 48 * 1024) {
      return send(ws, { type: 'error', code: 'payload_too_large' });
    }

    for (const peer of room) {
      if (peer !== ws) send(peer, { type: msg.type, payload: payload ?? null });
    }

    if (msg.type === 'session_end') leave(ws);
  });

  ws.on('close', () => leave(ws));
  ws.on('error', () => leave(ws));
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) {
      ws.terminate();
      continue;
    }

    ws.isAlive = false;
    ws.ping();
  }
}, 30000);

server.listen(PORT, '0.0.0.0', () => {
  console.log(`VANTA signaling listening on ${PORT}`);
});

server.on('close', () => clearInterval(heartbeat));
