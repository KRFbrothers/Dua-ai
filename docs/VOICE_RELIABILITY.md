# Typography and voice pillars (0–1)

## Implemented

- `KrfBrothersFont` aliases the existing `InterFont` resource family. `KrfBrothersCodeFont` is `FontFamily.Monospace` for new components. Existing `CodeTextStyle` and the saved monospace preference retain the bundled code font.
- Both voice surfaces use a display-frame-clock waveform with cached native paints, radial shader, path, and a remembered 48-bin array. Frame state is read in drawing rather than composition. New primitive level providers avoid per-draw Float boxing; frame callbacks are reused. Gradients and paths are not recreated by the renderer each frame.
- The waveform is an RMS/state-driven spectrum-style visualization. Device TTS exposes playback state, not PCM frequency bins: the speaking envelope is visual feedback, not an FFT measurement. The existing official 960×960 3-bar icon is reused through a resource alias.
- Speaking transitions produce one subtle haptic when enabled and the screen is resumed; hands-free status adds a glowing speaker indicator.
- Hands-free mode opens a local, echo-cancelled microphone monitor only during unmuted replies. It calibrates residual noise for 200ms, then requires 120ms of sustained above-floor audio before stopping ReplyVoice and starting SpeechRecognizer. Stale utterance callbacks, mute, pause and close cannot re-open the mic or restart old playback.
- If mic permission or platform echo cancellation is unavailable, automatic barge-in is disabled and the UI asks the user to tap the waveform. Tap interruption remains available. This avoids treating the assistant's own loudspeaker output as new user speech. Audio is neither retained nor uploaded by this local monitor.
- Live voice retains its existing server VAD/AudioTrack flush behavior. Tapping its waveform now flushes playback, cancels an active response and rejects late audio from the interrupted reply. The current live provider remains xAI; adding Gemini artwork does not add a Gemini Live integration.

## Phone verification still required

Unit/UI tests cover state transitions, stale callbacks, noise/click rejection, buffer reuse, haptic opt-out and accessible tap controls. They cannot certify speaker echo cancellation, Bluetooth routing, perceived interruption latency, GPU frame rate or whole-process PSS.

On a physical 60Hz Android device, install the reviewed signed APK and test both themes:

1. Start hands-free voice and play a long reply. Speak normally, then repeat using tap-to-interrupt. The old reply must stop and listening must resume once.
2. Play replies without speaking. Speaker leakage, a brief click or steady background noise must not repeatedly interrupt. Repeat with wired/Bluetooth audio and at several speaker volumes.
3. Mute during a reply, background the app, revoke mic permission, and close the screen. Hands-free capture must stop. Live voice's existing foreground-service behavior remains separate.
4. In Android Studio Profiler or Perfetto, record at least 60 seconds of listening/speaking transitions. Verify the requested 60fps target (16.67ms budget at 60Hz), no sustained jank, and no renderer-created path/shader/sample-array churn. Framework/coroutine allocations must be measured, not assumed absent.
5. Capture memory before/during/after several turns:

   ```sh
   adb shell dumpsys gfxinfo com.dua.ai reset
   # Interact with voice for at least 60 seconds.
   adb shell dumpsys gfxinfo com.dua.ai framestats
   adb shell dumpsys meminfo com.dua.ai
   ```

   Check `TOTAL PSS` stays comfortably below 180MB (about 175,781kB for a decimal 180MB limit), without turn-over-turn growth. No physical-device frame-rate, allocation or PSS result is claimed by this PR.

Only supplied pillars 0 and 1 are covered here; no unspecified flagship features are added.
