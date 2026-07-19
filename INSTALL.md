# ProxyTracer — Multi-Platform Installation Guide

To ensure ProxyTracer works accurately, follow the architectural rule below. If you configure it incorrectly, the plugin may read incoming IPs as `127.0.0.1` and either block everyone or fail to detect VPN users.

---

## 1. Architectural Rules

| Architecture | Jar to Install | Where |
|---|---|---|
| **Direct Connection** (Player → Paper/Spigot) | `proxytracer-bukkit.jar` | Game server `plugins/` |
| **Velocity Network** (Player → Velocity → Paper) | `proxytracer-velocity.jar` | **Velocity proxy `plugins/` ONLY**. (Do not install on backend Paper servers) |
| **Bungee/Waterfall Network** (Player → Bungee → Paper) | `proxytracer-bungee.jar` | **Bungee proxy `plugins/` ONLY**. (Do not install on backend Paper servers) |

---

## 2. Shared Hosting Real-IP Issue (`127.0.0.1`)

Shared hosting platforms (e.g., TickHosting, PebbleHost, Apex) often route traffic through their own panel firewalls or TCP proxies. This causes the game server process to see the sender IP as `127.0.0.1`.

If your console logs show:
```text
[ProxyTracer] Allowed PlayerName from 127.0.0.1: proxy=false
```
your server is not receiving real player IPs. ProxyTracer cannot scan these properly.

### How to Fix on Paper (Single Server)
If your provider sends **Proxy Protocol** headers, you must enable it in Paper:
1. Stop your server.
2. Open `config/paper-global.yml` in your server directory.
3. Locate the `proxies:` block and change `proxy-protocol` to `true`:
   ```yaml
   proxies:
     proxy-protocol: true
   ```
4. Start your server.
5. Join with a client and check the console. It should now show your public IP instead of `127.0.0.1`.

> [!WARNING]
> Do **not** enable `proxy-protocol` if your host does not support or send PROXY headers. Doing so will block all logins to the server. If in doubt, ask your host's support: *"Do you send Proxy Protocol v1/v2 to Paper?"*

---

## 3. Configuring Velocity Proxy Networks

When using Velocity, ProxyTracer must run **only on Velocity** because Velocity stands at the edge of the network and reads the player's true incoming IP.

### Step-by-Step Setup:
1. Drop `proxytracer-velocity.jar` into the Velocity `plugins/` directory.
2. Ensure you are forwarding player profiles securely from Velocity to Paper. In `velocity.toml`:
   ```toml
   player-info-forwarding-mode = "modern"
   forwarding-secret-file = "forwarding.secret"
   ```
3. Copy the random secret string in `forwarding.secret`.
4. In your backend Paper servers, edit `config/paper-global.yml`:
   ```yaml
   proxies:
     velocity:
       enabled: true
       online-mode: true
       secret: "your_copied_secret_string_here"
   ```
5. Ensure `online-mode` is set to `false` in Paper's `server.properties` (auth is handled by Velocity).
6. Set your API key via the Velocity console or in-game:
   ```text
   /proxytracer set your_api_key_here
   ```
7. Verify the key is active by running:
   ```text
   /proxytracer status
   ```

---

## 4. Configuring BungeeCord / Waterfall Networks

Similar to Velocity, ProxyTracer runs on the BungeeCord gateway.

### Step-by-Step Setup:
1. Put `proxytracer-bungee.jar` into BungeeCord's `plugins/` folder.
2. Enable IP forwarding in BungeeCord's `config.yml`:
   ```yaml
   ip_forward: true
   ```
3. Enable BungeeCord mode in your backend Spigot/Paper server's `spigot.yml`:
   ```yaml
   settings:
     bungeecord: true
   ```
4. Set `online-mode=false` in the backend Paper `server.properties`.
5. Set your API key via the Bungee console or in-game:
   ```text
   /proxytracer set your_api_key_here
   ```
6. Verify status with `/proxytracer status`.

---

## 5. recommended Production Settings
To keep your network fast and prevent downtime blocks:

```yaml
mode: "block"          # Block VPNs immediately

api:
  on-error: "allow"    # Fail-open: if the ProxyTracer API is offline, allow joins rather than locking the server.

cache:
  enabled: true        # Saves API credits and prevents network lag on rejoin
  ttl-minutes: 1440    # 24-hour cache duration
```
