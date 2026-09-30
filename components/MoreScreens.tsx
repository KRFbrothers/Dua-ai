"use client";

import { useState } from "react";
import {
  Card,
  CubeMark,
  Pill,
  PrimaryButton,
  Row,
  Toggle,
  TopBar,
} from "@/components/Chrome";
import { LOCALES, t, type CopyKey } from "@/lib/i18n";
import type { Artwork } from "@/lib/gallery";
import { artworkSvg, svgDataUrl } from "@/lib/gallery";
import type { FontChoice, Preferences, ThemeChoice, VoiceChoice } from "@/lib/prefs";

export function Onboarding({
  onDone,
  onSkip,
}: {
  onDone: (email: string) => void;
  onSkip: () => void;
}) {
  const [step, setStep] = useState(0);
  const [email, setEmail] = useState("");

  if (step === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
        <CubeMark size={120} />
        <p className="mt-6 text-2xl font-semibold">Meet Dua AI</p>
        <p className="mt-2 text-sm text-[var(--muted)]">
          A space for your ideas, questions, and everyday conversations.
        </p>
        <div className="mt-8 w-full">
          <PrimaryButton onClick={() => setStep(1)}>Continue</PrimaryButton>
          <button type="button" onClick={onSkip} className="mt-3 w-full py-2 text-sm text-[var(--muted)]">
            Skip
          </button>
        </div>
        <p className="mt-2 text-xs text-[var(--faint)]">1 of 3</p>
      </div>
    );
  }

  if (step === 1) {
    return (
      <div className="flex flex-1 flex-col px-6 pt-4">
        <div className="flex justify-end">
          <button type="button" onClick={onSkip} className="text-sm text-[var(--muted)]">
            Skip
          </button>
        </div>
        <div className="mt-8 rounded-[1.6rem] bg-[var(--card)] p-4 text-sm">
          <p className="text-[var(--muted)]">Help me break down a big idea into steps.</p>
          <p className="mt-3">I’m ready. Tell me what you’re thinking, and we will structure it together.</p>
        </div>
        <div className="mt-auto pb-8">
          <h2 className="text-3xl leading-tight font-semibold">Talk it through</h2>
          <p className="mt-2 text-sm text-[var(--muted)]">Ask a question, explore an idea, or pick up where you left off.</p>
          <div className="mt-6">
            <PrimaryButton onClick={() => setStep(2)}>Continue</PrimaryButton>
          </div>
          <p className="mt-2 text-center text-xs text-[var(--faint)]">2 of 3</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col px-6 pt-4">
      <div className="flex justify-end">
        <button type="button" onClick={onSkip} className="text-sm text-[var(--muted)]">
          Skip
        </button>
      </div>
      <div className="mt-10 flex flex-col items-center">
        <CubeMark size={96} />
        <h2 className="mt-6 text-3xl font-semibold">Make it yours</h2>
        <p className="mt-2 text-center text-sm text-[var(--muted)]">
          Start with a thought, a task, or something you want to learn.
        </p>
      </div>
      <form
        className="mt-8"
        onSubmit={(event) => {
          event.preventDefault();
          onDone(email.trim() || "user@dua.ai");
        }}
      >
        <label className="mb-1 block text-xs text-[var(--muted)]" htmlFor="email">
          Email
        </label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="you@example.com"
          className="w-full rounded-2xl bg-[var(--card)] px-4 py-3 outline-none"
        />
        <div className="mt-4">
          <PrimaryButton type="submit">Start chatting</PrimaryButton>
        </div>
      </form>
      <button type="button" onClick={onSkip} className="mt-3 py-2 text-sm text-[var(--muted)]">
        Sign in later
      </button>
    </div>
  );
}

export function Splash() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center">
      <CubeMark size={88} />
      <p className="mt-4 text-lg">Dua AI</p>
      <div className="mt-8 h-8 w-8 animate-spin rounded-full border-2 border-white/15 border-t-[var(--accent)]" />
    </div>
  );
}

export function SearchPane({
  query,
  onQuery,
  results,
  onOpen,
}: {
  query: string;
  onQuery: (value: string) => void;
  results: Array<{ id: string; title: string; snippet: string }>;
  onOpen: (id: string) => void;
}) {
  return (
    <div className="flex min-h-0 flex-1 flex-col px-4 pt-3">
      <label className="sr-only" htmlFor="search">
        Search chats
      </label>
      <input
        id="search"
        value={query}
        onChange={(event) => onQuery(event.target.value)}
        placeholder="Search chats"
        className="rounded-2xl bg-[var(--card)] px-4 py-3 outline-none"
      />
      <div className="scrollbar-thin mt-4 min-h-0 flex-1 overflow-y-auto">
        {query && results.length === 0 ? (
          <p className="pt-16 text-center text-sm text-[var(--muted)]">No results</p>
        ) : (
          <ul className="space-y-2">
            {results.map((result) => (
              <li key={result.id}>
                <button type="button" onClick={() => onOpen(result.id)} className="w-full rounded-2xl bg-[var(--card)] px-4 py-3 text-left">
                  <span className="block text-sm font-medium">{result.title}</span>
                  <span className="mt-1 block truncate text-xs text-[var(--muted)]">{result.snippet}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

export function ProfilePane({
  prefs,
  locale,
  onSettings,
  onAbout,
  onTheme,
  onSignOut,
  onDelete,
}: {
  prefs: Preferences;
  locale: string;
  onSettings: () => void;
  onAbout: () => void;
  onTheme: (dark: boolean) => void;
  onSignOut: () => void;
  onDelete: () => void;
}) {
  const initial = prefs.displayName.slice(0, 1).toUpperCase();
  return (
    <div className="scrollbar-thin min-h-0 flex-1 overflow-y-auto px-4 pt-2 pb-4">
      <TopBar title="Profile" trailing={<CubeMark size={22} />} />
      <p className="-mt-2 mb-3 text-center text-xs text-[var(--muted)]">Account</p>
      <Card>
        <div className="flex items-center gap-3 px-4 py-4">
          <div className="grid h-12 w-12 place-items-center rounded-full bg-[var(--accent)] text-lg text-white">{initial}</div>
          <div>
            <p className="font-medium">{prefs.displayName}</p>
            <p className="text-xs text-[var(--muted)]">{prefs.email}</p>
          </div>
        </div>
      </Card>
      <p className="mt-5 mb-2 px-1 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Account details</p>
      <Card>
        <Row title="Display name" detail={prefs.displayName} />
        <Row title="Registered email" detail={prefs.email} />
      </Card>
      <p className="mt-5 mb-2 px-1 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Preferences</p>
      <Card>
        <Row title={t(locale, "settings" as CopyKey)} detail="Customize" onClick={onSettings} />
        <Row
          title="Dark mode"
          trailing={<Toggle checked={prefs.theme !== "light"} onChange={onTheme} label="Dark mode" />}
        />
        <Row title="About Dua AI" detail={`v${prefs.version}`} onClick={onAbout} />
      </Card>
      <div className="mt-4">
        <Card>
          <Row title="Sign out" onClick={onSignOut} />
          <Row title="Delete account" danger onClick={onDelete} />
        </Card>
      </div>
    </div>
  );
}

export function SettingsHome({
  prefs,
  onBack,
  onChange,
  onOpen,
}: {
  prefs: Preferences;
  onBack: () => void;
  onChange: (patch: Partial<Preferences>) => void;
  onOpen: (screen: string) => void;
}) {
  return (
    <div className="scrollbar-thin min-h-0 flex-1 overflow-y-auto pb-6">
      <TopBar title="Settings" onBack={onBack} trailing={<CubeMark size={22} />} />
      <div className="px-4">
        <Card>
          <div className="flex items-center gap-3 px-4 py-4">
            <div className="grid h-11 w-11 place-items-center rounded-full bg-[var(--accent)] text-white">
              {prefs.displayName.slice(0, 1)}
            </div>
            <div>
              <p className="font-medium">{prefs.displayName}</p>
              <p className="text-xs text-[var(--muted)]">{prefs.email}</p>
            </div>
          </div>
        </Card>
        <p className="mt-5 mb-2 px-1 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">App preferences</p>
        <Card>
          <Row title="Profile" detail={prefs.displayName} onClick={() => onOpen("profile")} />
          <Row title="Comparison mode" detail={prefs.comparison ? "On" : "Off"} onClick={() => onOpen("comparison")} />
          <Row title="Data controls" onClick={() => onOpen("privacy")} />
          <Row title="Capabilities" detail="Chat, voice, images" onClick={() => onOpen("capabilities")} />
          <Row title="Plugins" detail="Coming soon" onClick={() => onOpen("coming")} />
        </Card>
        <p className="mt-5 mb-2 px-1 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Interface</p>
        <Card>
          <Row title="Color mode" detail={prefs.theme} onClick={() => onOpen("appearance")} />
          <Row title="Font style" detail={prefs.font} onClick={() => onOpen("font")} />
          <Row title="Voice" detail={prefs.voice} onClick={() => onOpen("voice-settings")} />
          <Row
            title="Haptic feedback"
            trailing={<Toggle checked={prefs.haptics} onChange={(haptics) => onChange({ haptics })} label="Haptic feedback" />}
          />
        </Card>
      </div>
    </div>
  );
}

export function AppearanceScreen({
  prefs,
  onBack,
  onChange,
}: {
  prefs: Preferences;
  onBack: () => void;
  onChange: (patch: Partial<Preferences>) => void;
}) {
  const themes: Array<{ id: ThemeChoice; label: string; detail: string }> = [
    { id: "dark", label: "Dark", detail: "Low light" },
    { id: "light", label: "Light", detail: "High visibility" },
    { id: "system", label: "Device setting", detail: "Follow this device" },
  ];
  return (
    <div className="scrollbar-thin min-h-0 flex-1 overflow-y-auto pb-6">
      <TopBar title="Appearance" onBack={onBack} />
      <div className="px-4">
        <p className="mb-2 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Interface style</p>
        <p className="mb-3 text-sm text-[var(--muted)]">Customize how Dua AI renders on your device.</p>
        <Card>
          {themes.map((theme) => (
            <Row
              key={theme.id}
              title={theme.label}
              detail={theme.detail}
              onClick={() => onChange({ theme: theme.id })}
              trailing={prefs.theme === theme.id ? <span className="text-[var(--accent-2)]">●</span> : <span />}
            />
          ))}
        </Card>
        <div className="mt-4 rounded-2xl bg-[var(--card)] px-4 py-4 text-sm">
          “Welcome back, always with you.”
        </div>
        <p className="mt-5 mb-2 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Display options</p>
        <Card>
          <Row
            title="Reduce motion"
            trailing={
              <Toggle
                checked={prefs.reduceMotion}
                onChange={(reduceMotion) => onChange({ reduceMotion })}
                label="Reduce motion"
              />
            }
          />
          <Row
            title="High contrast text"
            trailing={
              <Toggle
                checked={prefs.highContrast}
                onChange={(highContrast) => onChange({ highContrast })}
                label="High contrast text"
              />
            }
          />
        </Card>
      </div>
    </div>
  );
}

export function LanguageScreen({
  locale,
  onBack,
  onPick,
}: {
  locale: string;
  onBack: () => void;
  onPick: (id: string) => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <TopBar title="Language" onBack={onBack} />
      <div className="px-4">
        <p className="mb-3 text-sm text-[var(--muted)]">Choose your preferred language for Dua AI menus.</p>
        <Card>
          <Row title="Use device language" detail="Off" />
          {LOCALES.map((item) => (
            <Row
              key={item.id}
              title={item.label}
              onClick={() => onPick(item.id)}
              trailing={locale === item.id ? <span className="text-[var(--accent-2)]">●</span> : <span />}
            />
          ))}
        </Card>
      </div>
    </div>
  );
}

export function MemoryScreen({
  prefs,
  onBack,
  onChange,
  onClear,
}: {
  prefs: Preferences;
  onBack: () => void;
  onChange: (patch: Partial<Preferences>) => void;
  onClear: () => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto pb-6">
      <TopBar title="Memory" onBack={onBack} />
      <div className="px-4">
        <Card>
          <Row
            title="Remember preferences"
            detail="Theme, language, and voice stay on this device."
            trailing={
              <Toggle
                checked={prefs.rememberPreferences}
                onChange={(rememberPreferences) => onChange({ rememberPreferences })}
                label="Remember preferences"
              />
            }
          />
          <Row title="View saved memories" trailing={<Pill>Coming soon</Pill>} />
          <Row title="Personal instructions" trailing={<Pill>Coming soon</Pill>} />
        </Card>
        <p className="mt-5 mb-2 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Your choice</p>
        <Card>
          <Row title="Clear chat history" danger onClick={onClear} />
        </Card>
      </div>
    </div>
  );
}

export function PrivacyScreen({
  onBack,
  onExport,
  onClear,
  onDelete,
}: {
  onBack: () => void;
  onExport: () => void;
  onClear: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto pb-6">
      <TopBar title="Privacy & Data" onBack={onBack} />
      <div className="px-4">
        <Card>
          <Row title="Chat history" detail="Stored in this browser" />
          <Row title="Export your data" onClick={onExport} />
          <Row title="Delete chat history" danger onClick={onClear} />
        </Card>
        <p className="mt-5 mb-2 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Account</p>
        <Card>
          <Row title="Delete account" danger onClick={onDelete} />
        </Card>
      </div>
    </div>
  );
}

export function NotificationsScreen({
  prefs,
  onBack,
  onChange,
  saved,
}: {
  prefs: Preferences;
  onBack: () => void;
  onChange: (patch: Partial<Preferences>) => void;
  saved: boolean;
}) {
  return (
    <div className="relative min-h-0 flex-1 overflow-y-auto pb-6">
      <TopBar title="Notifications" onBack={onBack} />
      <div className="px-4">
        <Card>
          <Row
            title="Allow notifications"
            detail="App notification controls"
            trailing={
              <Toggle
                checked={prefs.notifications}
                onChange={(notifications) => onChange({ notifications })}
                label="Allow notifications"
              />
            }
          />
        </Card>
        <p className="mt-5 mb-2 text-[11px] tracking-[0.16em] text-[var(--faint)] uppercase">Topics</p>
        <Card>
          <Row
            title="Chat updates"
            trailing={<Toggle checked={prefs.chatUpdates} onChange={(chatUpdates) => onChange({ chatUpdates })} label="Chat updates" />}
          />
          <Row
            title="Reminders"
            trailing={<Toggle checked={prefs.reminders} onChange={(reminders) => onChange({ reminders })} label="Reminders" />}
          />
        </Card>
        {saved ? <p className="mt-4 text-center text-sm text-[var(--muted)]">Notification settings saved</p> : null}
      </div>
    </div>
  );
}

export function AboutScreen({
  version,
  onBack,
  onUpdate,
  onComing,
}: {
  version: string;
  onBack: () => void;
  onUpdate: () => void;
  onComing: () => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto pb-8">
      <TopBar title="About & Help" onBack={onBack} />
      <div className="flex flex-col items-center px-6 pt-6 text-center">
        <CubeMark size={72} />
        <p className="mt-3 text-xl font-semibold">Dua AI</p>
        <p className="text-sm text-[var(--muted)]">always with you</p>
      </div>
      <div className="mt-6 px-4">
        <Card>
          <Row title="Help center" trailing={<Pill>Coming soon</Pill>} onClick={onComing} />
          <Row title="FAQ" trailing={<Pill>Coming soon</Pill>} onClick={onComing} />
          <Row title="Contact support" trailing={<Pill>Coming soon</Pill>} onClick={onComing} />
          <Row title="App version" detail={version} onClick={onUpdate} />
          <Row title="Legal information" trailing={<Pill>Coming soon</Pill>} onClick={onComing} />
        </Card>
        <div className="mt-4">
          <PrimaryButton
            onClick={() => {
              const share = `Dua AI ${version}`;
              if (navigator.share) void navigator.share({ title: "Dua AI", text: share });
              else void navigator.clipboard.writeText(share);
            }}
          >
            Share app
          </PrimaryButton>
        </div>
      </div>
    </div>
  );
}

export function UpdateScreen({ onBack, onLater }: { onBack: () => void; onLater: () => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <TopBar title="App Update" onBack={onBack} trailing={<CubeMark size={22} />} />
      <div className="flex flex-1 flex-col px-6">
        <div className="mx-auto mt-6 grid h-20 w-20 place-items-center rounded-full bg-[var(--card)] text-3xl">↓</div>
        <h2 className="mt-4 text-center text-2xl font-semibold">Update available</h2>
        <p className="mt-2 text-center text-sm text-[var(--muted)]">
          When a new version is ready, you can update Dua AI here.
        </p>
        <Card>
          <div className="flex items-center justify-between px-4 py-3 text-sm">
            <span>Current version</span>
            <Pill>2.1.0</Pill>
          </div>
          <div className="flex items-center justify-between px-4 py-3 text-sm">
            <span>New version</span>
            <span className="rounded-full bg-[var(--accent)] px-2 py-0.5 text-xs text-white">2.2.0</span>
          </div>
        </Card>
        <p className="mt-4 text-sm font-medium">What’s new</p>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-[var(--muted)]">
          <li>Improved response speed and accuracy</li>
          <li>New model picker experience</li>
          <li>Bug fixes and performance improvements</li>
        </ul>
        <div className="mt-auto space-y-2 pb-6">
          <PrimaryButton onClick={onBack}>Update app</PrimaryButton>
          <button type="button" onClick={onLater} className="w-full py-2 text-sm text-[var(--muted)]">
            Not now
          </button>
          <p className="text-center text-[11px] text-[var(--faint)]">This demo stays on the version in this browser.</p>
        </div>
      </div>
    </div>
  );
}

export function ComingSoon({ onBack }: { onBack: () => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <TopBar title="Dua AI" onBack={onBack} trailing={<CubeMark size={22} />} />
      <div className="flex flex-1 flex-col items-center px-6 pt-10 text-center">
        <div className="grid h-16 w-16 place-items-center rounded-full bg-[var(--card)] text-2xl">◷</div>
        <h2 className="mt-5 text-2xl font-semibold">Coming soon</h2>
        <p className="mt-2 text-sm text-[var(--muted)]">This feature is in the works. Keep chatting with Dua for now.</p>
        <div className="mt-8 w-full space-y-2 text-left">
          {[
            ["Image generation", "Create visuals directly from chat"],
            ["Voice conversations", "Talk to Dua hands-free"],
            ["Collaborative chats", "Invite others to your session"],
          ].map(([title, detail]) => (
            <div key={title} className="rounded-2xl bg-[var(--card)] px-4 py-3">
              <p className="text-sm font-medium">{title}</p>
              <p className="text-xs text-[var(--muted)]">{detail}</p>
            </div>
          ))}
        </div>
        <div className="mt-auto w-full pb-6">
          <PrimaryButton onClick={onBack}>Back to chat</PrimaryButton>
        </div>
      </div>
    </div>
  );
}

export function ConfirmDialog({
  title,
  body,
  confirm,
  danger,
  onConfirm,
  onCancel,
}: {
  title: string;
  body: string;
  confirm: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="absolute inset-0 z-40 grid place-items-center bg-black/60 px-6">
      <div className="w-full rounded-[1.6rem] bg-[var(--bg-elev)] px-5 py-6 text-center">
        <h2 className={`text-xl font-semibold ${danger ? "text-[var(--danger)]" : ""}`}>{title}</h2>
        <p className="mt-2 text-sm text-[var(--muted)]">{body}</p>
        <div className="mt-5">
          <button
            type="button"
            onClick={onConfirm}
            className={`w-full rounded-full px-4 py-3 text-white ${danger ? "bg-[var(--danger)]" : "bg-[var(--accent)]"}`}
          >
            {confirm}
          </button>
          <button type="button" onClick={onCancel} className="mt-2 w-full py-2 text-sm">
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

export function PreviewScreen({
  name,
  url,
  caption,
  onCaption,
  onRemove,
  onAdd,
  onClose,
}: {
  name: string;
  url: string;
  caption: string;
  onCaption: (value: string) => void;
  onRemove: () => void;
  onAdd: () => void;
  onClose: () => void;
}) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <TopBar title="Preview" onBack={onClose} trailing={<button type="button" onClick={onClose}>Close</button>} />
      <div className="min-h-0 flex-1 overflow-y-auto px-4">
        <img src={url} alt="" className="max-h-72 w-full rounded-3xl object-cover" />
        <p className="mt-3 text-[11px] tracking-[0.14em] text-[var(--faint)] uppercase">Preview placeholder</p>
        <p className="text-sm">Selected photo</p>
        <p className="text-xs text-[var(--muted)]">{name}</p>
        <label className="mt-4 block text-[11px] tracking-[0.14em] text-[var(--faint)] uppercase" htmlFor="caption">
          Add a message
        </label>
        <textarea
          id="caption"
          value={caption}
          onChange={(event) => onCaption(event.target.value)}
          placeholder="Ask about this photo…"
          className="mt-2 min-h-20 w-full rounded-2xl bg-[var(--card)] px-3 py-2 outline-none"
        />
      </div>
      <div className="flex gap-3 px-4 pb-5">
        <button type="button" onClick={onRemove} className="flex-1 rounded-full bg-[var(--card)] py-3 text-sm">
          Remove
        </button>
        <button type="button" onClick={onAdd} className="flex-1 rounded-full bg-[var(--accent)] py-3 text-sm text-white">
          Add to chat
        </button>
      </div>
    </div>
  );
}

const VOICES: Array<{ id: VoiceChoice; label: string; detail: string }> = [
  { id: "calm", label: "Dua Calm", detail: "Selected" },
  { id: "breeze", label: "Soft Breeze", detail: "Lighter pace" },
  { id: "echo", label: "Low Echo", detail: "Lower tone" },
];

export function VoicePane({
  phase,
  transcript,
  reply,
  voice,
  onVoice,
  onBack,
  onMic,
  onAllow,
  onDeny,
  onOpenSettings,
}: {
  phase: "permission" | "idle" | "listening" | "transcript" | "settings";
  transcript: string;
  reply: string;
  voice: VoiceChoice;
  onVoice: (voice: VoiceChoice) => void;
  onBack: () => void;
  onMic: () => void;
  onAllow: () => void;
  onDeny: () => void;
  onOpenSettings: () => void;
}) {
  if (phase === "settings") {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <TopBar title="Voice settings" onBack={onBack} />
        <div className="px-4">
          <p className="mb-3 text-sm text-[var(--muted)]">Preview how Dua AI could sound.</p>
          <Card>
            {VOICES.map((item) => (
              <Row
                key={item.id}
                title={item.label}
                detail={voice === item.id ? "Selected" : item.detail}
                onClick={() => onVoice(item.id)}
                trailing={voice === item.id ? <span className="text-[var(--accent-2)]">●</span> : <span />}
              />
            ))}
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="relative flex min-h-0 flex-1 flex-col">
      <TopBar title="Voice" onBack={onBack} />
      <div className="flex flex-1 flex-col items-center px-6 pt-8 text-center">
        <div className="relative grid h-36 w-36 place-items-center">
          {phase === "listening" ? <span className="listening-ring absolute inset-0 rounded-full bg-[var(--accent)]" /> : null}
          <span className="relative grid h-24 w-24 place-items-center rounded-full bg-[var(--accent)] text-white">●</span>
        </div>
        <p className="mt-6 text-lg font-medium">
          {phase === "listening" ? "Listening…" : phase === "transcript" ? "Dua is answering" : "Tap to talk"}
        </p>
        {transcript ? (
          <div className="mt-6 w-full rounded-2xl bg-[var(--card)] px-4 py-3 text-left text-sm">
            <p className="text-xs text-[var(--muted)]">You</p>
            <p>{transcript}</p>
            {reply ? (
              <>
                <p className="mt-3 text-xs text-[var(--muted)]">Dua</p>
                <p>{reply}</p>
              </>
            ) : null}
          </div>
        ) : (
          <p className="mt-3 max-w-xs text-sm text-[var(--muted)]">Ask a question out loud. Dua answers in this chat.</p>
        )}
      </div>
      <div className="flex items-center justify-between px-8 pb-6">
        <button type="button" onClick={onOpenSettings} className="text-xs text-[var(--muted)]">
          Voice settings
        </button>
        <button type="button" aria-label="Microphone" onClick={onMic} className="grid h-16 w-16 place-items-center rounded-full bg-[var(--accent)] text-xl text-white">
          ●
        </button>
        <span className="w-16" />
      </div>
      {phase === "permission" ? (
        <div className="absolute inset-0 grid place-items-end bg-black/70 px-4 pb-6">
          <div className="w-full rounded-[1.6rem] bg-[var(--bg-elev)] p-5 text-center">
            <h2 className="text-lg font-semibold">Use your microphone?</h2>
            <p className="mt-2 text-sm text-[var(--muted)]">Dua uses it to hear the question you want answered.</p>
            <div className="mt-4 flex gap-2">
              <button type="button" onClick={onDeny} className="flex-1 rounded-full bg-[var(--card)] py-3 text-sm">
                Not now
              </button>
              <button type="button" onClick={onAllow} className="flex-1 rounded-full bg-[var(--accent)] py-3 text-sm text-white">
                Continue
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function ImaginePane({
  screen,
  prompt,
  style,
  ratio,
  gallery,
  onPrompt,
  onStyle,
  onRatio,
  onCreate,
  onBack,
  onSave,
  onDelete,
  onOpen,
}: {
  screen: "compose" | "working" | "result" | "gallery";
  prompt: string;
  style: string;
  ratio: Artwork["ratio"];
  gallery: Artwork[];
  onPrompt: (value: string) => void;
  onStyle: (value: string) => void;
  onRatio: (value: Artwork["ratio"]) => void;
  onCreate: () => void;
  onBack: () => void;
  onSave: () => void;
  onDelete: () => void;
  onOpen: (screen: "compose" | "gallery") => void;
}) {
  const preview = artworkSvg(prompt || "A quiet garden", style, ratio);
  if (screen === "gallery") {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <TopBar title="Your gallery" onBack={onBack} />
        <div className="grid flex-1 grid-cols-2 gap-2 overflow-y-auto px-4 pb-4">
          {gallery.length === 0 ? (
            <p className="col-span-2 pt-16 text-center text-sm text-[var(--muted)]">No creations yet</p>
          ) : (
            gallery.map((item) => (
              <button key={item.id} type="button" onClick={() => onOpen("compose")} className="overflow-hidden rounded-2xl bg-[var(--card)]">
                <img alt="" src={svgDataUrl(artworkSvg(item.prompt, item.style, item.ratio))} className="h-36 w-full object-cover" />
                <span className="block truncate px-2 py-2 text-left text-xs">{item.prompt}</span>
              </button>
            ))
          )}
        </div>
      </div>
    );
  }
  if (screen === "working") {
    return (
      <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
        <div className="h-12 w-12 animate-spin rounded-full border-2 border-white/15 border-t-[var(--accent)]" />
        <p className="mt-4 text-lg font-medium">Creating your image</p>
        <p className="mt-1 text-sm text-[var(--muted)]">Bringing your idea into color.</p>
      </div>
    );
  }
  if (screen === "result") {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <TopBar title="Image result" onBack={onBack} />
        <div className="min-h-0 flex-1 overflow-y-auto px-4">
          <img alt="" src={svgDataUrl(preview)} className="w-full rounded-3xl" />
          <p className="mt-3 text-sm">{prompt}</p>
          <p className="text-xs text-[var(--muted)]">{style}</p>
          <div className="mt-3 flex gap-2">
            {["Realistic", "Cinematic", "Watercolor"].map((item) => (
              <button key={item} type="button" onClick={() => onStyle(item)} className={`rounded-full px-3 py-1 text-xs ${style === item ? "bg-[var(--accent)] text-white" : "bg-[var(--card)]"}`}>
                {item}
              </button>
            ))}
          </div>
        </div>
        <div className="flex gap-2 px-4 pb-5">
          <button type="button" onClick={onSave} className="flex-1 rounded-full bg-[var(--accent)] py-3 text-sm text-white">
            Save
          </button>
          <button
            type="button"
            onClick={() => void navigator.clipboard.writeText(prompt)}
            className="flex-1 rounded-full bg-[var(--card)] py-3 text-sm"
          >
            Share
          </button>
          <button type="button" onClick={onDelete} className="rounded-full bg-[var(--card)] px-4 py-3 text-sm">
            Delete
          </button>
        </div>
      </div>
    );
  }
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <TopBar title="Create an image" onBack={onBack} trailing={<button type="button" onClick={() => onOpen("gallery")} className="text-xs">Gallery</button>} />
      <div className="min-h-0 flex-1 overflow-y-auto px-4">
        <p className="text-2xl font-semibold">What will you create?</p>
        <label className="mt-4 block text-xs text-[var(--muted)]" htmlFor="image-prompt">
          Describe your idea
        </label>
        <textarea
          id="image-prompt"
          value={prompt}
          onChange={(event) => onPrompt(event.target.value)}
          placeholder="A quiet garden at sunrise, soft mist over the flowers"
          className="mt-2 min-h-24 w-full rounded-2xl bg-[var(--card)] px-3 py-3 outline-none"
        />
        <p className="mt-4 text-xs tracking-[0.14em] text-[var(--faint)] uppercase">Style</p>
        <div className="mt-2 flex gap-2">
          {["Realistic", "Cinematic", "Watercolor"].map((item) => (
            <button key={item} type="button" onClick={() => onStyle(item)} className={`rounded-full px-3 py-1.5 text-sm ${style === item ? "bg-[var(--accent)] text-white" : "bg-[var(--card)]"}`}>
              {item}
            </button>
          ))}
        </div>
        <p className="mt-4 text-xs tracking-[0.14em] text-[var(--faint)] uppercase">Format</p>
        <div className="mt-2 flex gap-2">
          {(["square", "portrait", "wide"] as const).map((item) => (
            <button key={item} type="button" onClick={() => onRatio(item)} className={`rounded-full px-3 py-1.5 text-sm capitalize ${ratio === item ? "bg-[var(--accent)] text-white" : "bg-[var(--card)]"}`}>
              {item}
            </button>
          ))}
        </div>
      </div>
      <div className="px-4 pb-5">
        <PrimaryButton onClick={onCreate} disabled={!prompt.trim()}>
          Create
        </PrimaryButton>
      </div>
    </div>
  );
}

export function SimpleList({
  title,
  onBack,
  rows,
}: {
  title: string;
  onBack: () => void;
  rows: Array<{ title: string; detail?: string; onClick?: () => void }>;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <TopBar title={title} onBack={onBack} />
      <div className="px-4">
        <Card>
          {rows.map((row) => (
            <Row key={row.title} title={row.title} detail={row.detail} onClick={row.onClick} />
          ))}
        </Card>
      </div>
    </div>
  );
}

export function FontScreen({
  font,
  onBack,
  onPick,
}: {
  font: FontChoice;
  onBack: () => void;
  onPick: (font: FontChoice) => void;
}) {
  const options: Array<{ id: FontChoice; label: string }> = [
    { id: "default", label: "Default" },
    { id: "rounded", label: "Rounded" },
    { id: "mono", label: "Mono" },
  ];
  return (
    <div>
      <TopBar title="Font style" onBack={onBack} />
      <div className="px-4">
        <Card>
          {options.map((option) => (
            <Row
              key={option.id}
              title={option.label}
              onClick={() => onPick(option.id)}
              trailing={font === option.id ? <span className="text-[var(--accent-2)]">●</span> : <span />}
            />
          ))}
        </Card>
      </div>
    </div>
  );
}
