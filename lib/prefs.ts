export type ThemeChoice = "dark" | "light" | "system";
export type FontChoice = "default" | "rounded" | "mono";
export type VoiceChoice = "calm" | "breeze" | "echo";

export type Preferences = {
  onboarded: boolean;
  signedIn: boolean;
  displayName: string;
  email: string;
  theme: ThemeChoice;
  font: FontChoice;
  language: string;
  reduceMotion: boolean;
  highContrast: boolean;
  haptics: boolean;
  notifications: boolean;
  chatUpdates: boolean;
  reminders: boolean;
  rememberPreferences: boolean;
  comparison: boolean;
  voice: VoiceChoice;
  version: string;
};

export const PREFS_KEY = "dua.prefs.v1";

export const defaultPreferences = (): Preferences => ({
  onboarded: false,
  signedIn: false,
  displayName: "Dua User",
  email: "user@dua.ai",
  theme: "dark",
  font: "default",
  language: "en",
  reduceMotion: false,
  highContrast: false,
  haptics: true,
  notifications: true,
  chatUpdates: false,
  reminders: false,
  rememberPreferences: true,
  comparison: false,
  voice: "calm",
  version: "1.4.0",
});

export function loadPreferences(): Preferences {
  if (typeof window === "undefined") return defaultPreferences();
  try {
    const raw = window.localStorage.getItem(PREFS_KEY);
    if (!raw) return defaultPreferences();
    return { ...defaultPreferences(), ...(JSON.parse(raw) as Partial<Preferences>) };
  } catch {
    return defaultPreferences();
  }
}

export function savePreferences(prefs: Preferences): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
}

export function resolvedTheme(choice: ThemeChoice): "dark" | "light" {
  if (choice === "system" && typeof window !== "undefined") {
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  }
  return choice === "light" ? "light" : "dark";
}
