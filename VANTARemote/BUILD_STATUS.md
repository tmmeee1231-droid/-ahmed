# VANTA Remote build status

The Android project builds successfully as a debug APK through GitHub Actions.

The current source includes:
- WebSocket pairing/signaling against the Railway service.
- WebRTC offer/answer and ICE exchange.
- Android MediaProjection screen capture after the system consent prompt.
- Remote video rendering.
- Remote tap messages over a WebRTC data channel, handled through an explicitly enabled Android AccessibilityService.

Important limitations:
- ICE currently configures a public STUN server only; TURN is not configured, so some NAT/firewall combinations will fail.
- Remote input currently supports taps only.
- Pair codes are not strong authentication and have no expiry/rate limiting.
- A successful CI build is not an end-to-end device test. Test on two physical Android devices before production use.

Latest successful debug artifact: VANTA-Remote-debug-apk.
