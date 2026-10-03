# Native Android mobile layout parity

The native Compose interface follows the mobile design in `web/dist/preview/`.

- Home shows the current conversation preview, searches its text, and presents empty/no-result states.
- Chat retains the centered header and original DUA logo, wrapping message bubbles and an IME-aware composer. Model and icon controls have at least 48dp targets. The composer clears accepted input and changes Cube → Send → Stop, returning to Cube after completion/cancellation.
- The model sheet uses provider avatars, selected-state artwork, unavailable ChatGPT/Claude/Grok/Kimi rows and a Done action.
- Settings has a fixed header and scrollable cards, with nested Profile, Data controls, Permissions, appearance, Notifications, account and Computer mode design screens. Back restores the entry route, including Voice.
- Add to Chat uses three 120dp cards, retains selected filenames across navigation, and offers Remove. Attachment upload remains unavailable; selected files block text sending until removed. Web search and Memory stay disabled/off.
- Voice has scrollable response content, a fixed footer, minimum 48dp controls and a six-face perspective cube. A local mic preview toggle drives four-second rotation and soft pulse. Reduced motion and background lifecycle states stop animation. No audio is recorded.

Production Android still calls the existing shared Gemini backend. The web `/preview/` sample replies and account/settings samples are design simulations. Matching visuals do not mean every preview feature is connected to a native service. Android account logout, history clearing, notifications, appearance settings and Voice permission screens demonstrate design states only; unfinished integrations are preserved.

Verification commands: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
Physical device/emulator checks remain necessary for keyboard resizing, font scaling and visual comparison at 320/390/480dp widths.
