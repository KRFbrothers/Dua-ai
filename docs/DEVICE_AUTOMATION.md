# Paired device automation and interactive terminals

This implementation adds real adapters and agent wiring; it does **not** pair a
device, install a companion, grant permissions, or deploy/activate it. Engine
capabilities report `deviceAutomation.configured` separately from
`liveVerified:false`. Live device/end-to-end evaluation remains outstanding.

## What is implemented

| Target | Implemented operations | Boundary |
| --- | --- | --- |
| Browser | Screenshot and visible text, HTTPS navigation, click/double-click, scroll, type, keys | Clean isolated Chromium context, explicit hostname allowlist, no popups/download acceptance/service workers/WebSockets |
| Desktop | Primary display screenshot, click/double-click, scroll, printable ASCII typing, keys, allowlisted app launch | Linux X11 desktop user, PyAutoGUI failsafe enabled; no Wayland/macOS/Windows integration claimed |
| Android | Screenshot, tap/double-tap, swipe, keys including Home/Back, restricted ASCII typing, allowlisted package launch | One explicit adbutils serial, already authorized USB/ADB pairing; no arbitrary shell/install/root tool |
| Interactive terminal | Open, read, send input including Ctrl-C, close; discover existing owned sessions | Pexpect PTY attached to one digest-pinned Docker image; network disabled, no host mounts, user 10001, fixed resource caps |
| Other apps | Operate an installed app using the above visual primitives | No promise of a ready connector for every app, inaccessible/protected UI, login, CAPTCHA or payment automation |

All dependencies are optional on the **companion host only**. The Cloud Run engine
does not import Playwright, PyAutoGUI, adbutils or Pexpect, and never receives the
host Docker socket or ADB access. No vendor source was copied or agent framework
replaced. The existing Google ADK runtime and native approval flow remain in use.

## Open-source sources

Versions are pinned in `google-cloud-backend/requirements-automation.txt`.

| Dependency | Source | License |
| --- | --- | --- |
| Playwright Python | https://github.com/microsoft/playwright-python | Apache-2.0 |
| PyAutoGUI | https://github.com/asweigart/pyautogui | BSD-3-Clause |
| adbutils | https://github.com/openatx/adbutils | MIT |
| Pexpect | https://github.com/pexpect/pexpect | ISC |

Upstream license texts were checked. Dependencies are installed as unmodified
upstream distributions, which carry their notices; they are not vendored here.
Pillow, Flask and Waitress are also installed from their upstream distributions.

## Request/approval flow

1. `automation_targets` discovers paired target IDs, app aliases, terminal
   profiles and existing owned terminal IDs. Metadata alone is not live readiness.
2. `observe_device(target)` returns visible text, original screenshot dimensions
   and a 120-second `view_id`. JPEG pixels go to the model as image parts; they
   are not base64 text in tool results, progress events, logs or SQL session events.
   The latest screenshot is held only within the current Agent request. Screen
   text and terminal output are untrusted data, not commands/authorization.
3. `control_device` prepares **one exact action**. It does not execute in the
   model loop. The existing Android approval card shows target, view, action,
   coordinates and text; the turn ends waiting for the owner. The same rule
   applies to terminal open/input/close. No blanket approval or autonomous batch.
4. Approval is owner-checked, single-use, argument-bound and bound to companion
   configuration. The companion independently checks its single paired owner,
   action ID, target/session and current view. A changed/expired screenshot is
   refused before input. One observation permits one attempted action. Animated
   pages/clocks can invalidate a view: capture again rather than bypass this.
5. After approval, ask for a fresh observation/read to verify the actual app
   outcome. Sending input is not proof the app completed the intended task.
   Timeouts can have unknown outcomes: no automatic retries. In-process duplicate
   action IDs are retained for one hour, with a fixed budget; this is not a
   durable exactly-once guarantee across crashes.

Normal tool-loop and ADK Research/Actions helpers use the same dispatch. ADK
screenshots are attached to the next model request, not persisted in SDK events.
Explicitly configured text-only providers cannot perform visual control; use a
vision-capable model. No additional Android UI change is required to use the
existing approval result cards and subsequent chat turns.

## Install on the device host

Use a dedicated Linux host/user with access only to the intended desktop/device.
Do not install this service in the Cloud Run engine. Desktop capture requires an
active X11 session and screenshot prerequisites (for example scrot/Xlib). Android
requires Android platform-tools and on-device approval of USB debugging; confirm
`adb devices` shows the intended device as `device`, not `unauthorized`. Do not
expose the ADB server/port to the Internet.

From your checkout of this branch:

```bash
cd google-cloud-backend
python3 -m venv .automation-venv
.automation-venv/bin/python -m pip install -r requirements-automation.txt
.automation-venv/bin/python -m playwright install chromium
cp automation-companion.example.json ~/.dua-automation.json
chmod 600 ~/.dua-automation.json
```

Install Playwright's documented Linux system dependencies with your administrator
if needed. Chromium sandboxing stays enabled; do not add `--no-sandbox` to make
an unsupported host work. Edit `~/.dua-automation.json` locally:

- Replace `owner` with your signed-in account UUID, not email or a model value.
- Delete targets you do not want to pair. An empty `targets` object permits
  terminal-only use. Configure app aliases to fixed argv/package names.
- Browser hosts are **exact** names: include required resource/login hosts
  individually. A fresh browser has no existing personal cookies/profile.
- Set `serial` from the already-authorized ADB device listing. Wireless pairing,
  on-phone Accessibility Service and unattended phone control are not implemented.
- Leave terminal profiles empty until reviewed images are installed locally.

Host-level egress must also block private/link-local/metadata destinations for
the browser (firewall or enforcing outbound proxy). The request hook validates
schemes, exact domains and public DNS, but Chromium resolves separately: the hook
alone is **not** a network sandbox against DNS rebinding or all browser traffic.
Desktop/phone apps retain their normal device connectivity. Use a dedicated
device/profile when handling sensitive information; screenshot capture exposes
the visible display to the configured model provider.

Generate a dedicated random token (32+ ASCII characters) and put the same value
in the companion's private service environment and engine Secret Manager binding.
Do not commit it, put it in the JSON example, or send it in chat. For a manual
foreground start, enter that existing token at a hidden prompt:

```bash
read -rs -p 'Companion token: ' DUA_AUTOMATION_TOKEN
echo
export DUA_AUTOMATION_TOKEN
.automation-venv/bin/python automation_companion.py --config ~/.dua-automation.json
```

The service binds **127.0.0.1:8791** through Waitress with one process and four
HTTP threads; all device drivers run on a single serialized executor. Keep this
one-process deployment: Playwright driver state and terminal ownership are local.
Place it behind an operator-managed **HTTPS** reverse proxy on a reachable host,
with network access restricted to your engine. The bearer token remains required.
No tunnel, public ingress, cloud instance, device permission or firewall rule is
automatically created by this change. Never disable TLS verification.

## Interactive terminal setup and persistence

On a dedicated Docker daemon, pre-pull/review an image supporting UID 10001 and
`/bin/sh`, then place its immutable `name@sha256:...` in `terminalProfiles`, e.g.
`{"python":"YOUR_REVIEWED_IMAGE@sha256:YOUR_64_HEX_DIGEST"}`. The placeholder is
not a working image. Docker daemon permission is powerful; do not give the
untrusted container the daemon socket or mount your home directory.

Sessions keep shell variables, working directory and files across **chat/API
requests**. They start with an empty tmpfs `/workspace`, separate from GitHub
coding drafts; there is no implicit repo import, commit, file export or APK build.
They expire after 15 minutes without reads/input or one hour total. At most two
active sessions per owner and twenty retained sessions; output is a bounded
64 KB ring with cursors and truncation flags. Known key patterns are filtered
before storage; this does not prove arbitrary private output is safe to disclose.

Close destroys the container/files. Restart loses session IDs and process state;
startup removes orphan containers with this companion's dedicated label. Never
run two companions on the same daemon. There is no host volume persistence,
restart restoration, interactive UI terminal emulator or network/package install
support. Shell input, including Ctrl-C (`\u0003`), requires separate approval.

## Enable in the engine

After the operator has installed and verified the host/TLS ingress:

- `DUA_AUTOMATION_URL=https://YOUR_COMPANION_HOST` (HTTPS origin only, port 443).
- `DUA_AUTOMATION_TOKEN` as a dedicated Secret Manager binding, never a plain
  value committed in deployment configuration.
- Keep existing owner authentication and approval storage. No new model key.

Tools remain hidden when these settings are absent/invalid. The owner-only
`GET /api/agent/capabilities` reports configuration without disclosing token/URL.
Removing the two settings disables new calls and invalidates pending approvals;
also stop the companion to close any already-open terminals and browser context.
Use the normal staged revision/traffic review process before enabling production.

Example requests once paired:

- “Mere paired targets batao, phir desktop ka screen dekho.”
- “Browser mein example.com kholo; click se pehle action dikhao.”
- “Phone mein Settings kholo; phir screen dobara dekho.”
- “Python terminal kholo. Approval ke baad existing session mein command propose karo.”

## Verification status

`tests/test_automation.py` covers hidden tools, owner isolation, approval-only
writes, configuration changes, stale views, duplicate actions, browser URL policy,
ADB input injection, output/image separation and terminal isolation/expiry using
fake devices. These tests make no desktop/phone/provider calls. Local test runs
and hardware evaluation are deferred per the owner's earlier instruction; syntax
and diff checks alone do not establish live readiness. The existing CI discovers
the new test file automatically. No physical device or Docker job was controlled
while implementing this change.
