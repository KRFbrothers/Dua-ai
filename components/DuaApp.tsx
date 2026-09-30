"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ChatPane } from "@/components/ChatPane";
import { PhoneFrame, TabBar, Toast } from "@/components/Chrome";
import {
  AboutScreen,
  AppearanceScreen,
  ComingSoon,
  ConfirmDialog,
  FontScreen,
  ImaginePane,
  LanguageScreen,
  MemoryScreen,
  NotificationsScreen,
  Onboarding,
  PreviewScreen,
  PrivacyScreen,
  ProfilePane,
  SearchPane,
  SettingsHome,
  SimpleList,
  Splash,
  UpdateScreen,
  VoicePane,
} from "@/components/MoreScreens";
import { titleFromMessage } from "@/lib/demo";
import { loadGallery, saveGallery, type Artwork } from "@/lib/gallery";
import { direction, t } from "@/lib/i18n";
import { DEMO_MODEL_ID } from "@/lib/models";
import {
  defaultPreferences,
  loadPreferences,
  resolvedTheme,
  savePreferences,
  type Preferences,
} from "@/lib/prefs";
import { loadWorkspace, saveWorkspace, type WorkspaceState } from "@/lib/storage";
import {
  DEFAULT_SYSTEM_PROMPT,
  DEFAULT_TEMPERATURE,
  type ChatMessage,
  type Conversation,
  type ModelInfo,
} from "@/lib/types";

type Screen =
  | "splash"
  | "intro"
  | "chat"
  | "search"
  | "profile"
  | "settings"
  | "appearance"
  | "language"
  | "memory"
  | "privacy"
  | "notifications"
  | "about"
  | "update"
  | "coming"
  | "preview"
  | "voice"
  | "voice-settings"
  | "imagine"
  | "imagine-working"
  | "imagine-result"
  | "gallery"
  | "comparison"
  | "capabilities"
  | "font"
  | "signout"
  | "delete";

function uid(): string {
  return crypto.randomUUID();
}

export function DuaApp() {
  const [ready, setReady] = useState(false);
  const [screen, setScreen] = useState<Screen>("splash");
  const [history, setHistory] = useState<Screen[]>([]);
  const [prefs, setPrefs] = useState<Preferences>(defaultPreferences);
  const [workspace, setWorkspace] = useState<WorkspaceState | null>(null);
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [draft, setDraft] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [modelOpen, setModelOpen] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [photo, setPhoto] = useState<{ name: string; url: string } | null>(null);
  const [caption, setCaption] = useState("");
  const [voicePhase, setVoicePhase] = useState<"permission" | "idle" | "listening" | "transcript" | "settings">("permission");
  const [voiceTranscript, setVoiceTranscript] = useState("");
  const [voiceReply, setVoiceReply] = useState("");
  const [voiceAllowed, setVoiceAllowed] = useState(false);
  const [imagePrompt, setImagePrompt] = useState("A quiet garden at sunrise, soft mist over the flowers");
  const [imageStyle, setImageStyle] = useState("Realistic");
  const [imageRatio, setImageRatio] = useState<Artwork["ratio"]>("square");
  const [gallery, setGallery] = useState<Artwork[]>([]);
  const [noticeSaved, setNoticeSaved] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const storedPrefs = loadPreferences();
    const storedWorkspace = loadWorkspace();
    setPrefs(storedPrefs);
    setWorkspace(storedWorkspace);
    setGallery(loadGallery());
    setScreen(storedPrefs.onboarded ? "chat" : "splash");
    setReady(true);
    if (!storedPrefs.onboarded) {
      const timer = window.setTimeout(() => setScreen("intro"), 900);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, []);

  useEffect(() => {
    if (!ready) return;
    savePreferences(prefs);
  }, [prefs, ready]);

  useEffect(() => {
    if (!ready || !workspace) return;
    saveWorkspace(workspace);
  }, [workspace, ready]);

  useEffect(() => {
    if (!ready) return;
    saveGallery(gallery);
  }, [gallery, ready]);

  useEffect(() => {
    fetch("/api/models")
      .then((response) => response.json())
      .then((payload: { models: ModelInfo[] }) => setModels(payload.models))
      .catch(() => setModels([]));
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 1800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const conversations = workspace?.conversations ?? [];
  const active = conversations.find((item) => item.id === workspace?.activeId) ?? null;
  const modelId = active?.modelId ?? workspace?.draftModelId ?? DEMO_MODEL_ID;
  const model = models.find((item) => item.id === modelId);

  const theme = resolvedTheme(prefs.theme);
  const fontClass = prefs.font === "rounded" ? "font-rounded" : prefs.font === "mono" ? "font-mono-ui" : "";

  function patchPrefs(patch: Partial<Preferences>) {
    setPrefs((current) => ({ ...current, ...patch }));
    if ("notifications" in patch || "chatUpdates" in patch || "reminders" in patch) {
      setNoticeSaved(true);
    }
  }

  function patchWorkspace(recipe: (current: WorkspaceState) => WorkspaceState) {
    setWorkspace((current) => (current ? recipe(current) : current));
  }

  function push(next: Screen) {
    setHistory((stack) => [...stack, screen]);
    setScreen(next);
  }

  function back() {
    setHistory((stack) => {
      const previous = stack[stack.length - 1] ?? "chat";
      setScreen(previous);
      return stack.slice(0, -1);
    });
  }

  function goTab(tab: "chat" | "search" | "profile") {
    setHistory([]);
    setScreen(tab);
  }

  function showTabs() {
    return screen === "chat" || screen === "search" || screen === "profile" || screen === "signout" || screen === "delete";
  }

  async function send(content: string, historyMessages: ChatMessage[], conversationId: string, imageName?: string) {
    const selected = models.find((item) => item.id === modelId);
    if (selected && !selected.available) {
      setError(selected.unavailableReason ?? "Choose an available model.");
      return;
    }
    const userMessage: ChatMessage = {
      id: uid(),
      role: "user",
      content,
      createdAt: Date.now(),
      status: "complete",
      imageName,
    };
    const assistantId = uid();
    const assistantMessage: ChatMessage = {
      id: assistantId,
      role: "assistant",
      content: "",
      createdAt: Date.now(),
      status: "streaming",
      modelId,
    };
    const nextMessages = [...historyMessages, userMessage, assistantMessage];
    const now = Date.now();
    patchWorkspace((current) => {
      const existing = current.conversations.find((item) => item.id === conversationId);
      const conversationsNext = existing
        ? current.conversations.map((item) =>
            item.id === conversationId
              ? {
                  ...item,
                  messages: nextMessages,
                  modelId,
                  updatedAt: now,
                  title: item.messages.length === 0 ? titleFromMessage(content) : item.title,
                }
              : item,
          )
        : [
            {
              id: conversationId,
              title: titleFromMessage(content),
              messages: nextMessages,
              modelId,
              systemPrompt: current.draftSystemPrompt || DEFAULT_SYSTEM_PROMPT,
              temperature: current.draftTemperature || DEFAULT_TEMPERATURE,
              pinned: false,
              createdAt: now,
              updatedAt: now,
            } satisfies Conversation,
            ...current.conversations,
          ];
      return { ...current, conversations: conversationsNext, activeId: conversationId, draftModelId: modelId };
    });
    setDraft("");
    setError(null);
    setStreaming(true);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const response = await fetch("/api/chat", {
        method: "POST",
        signal: controller.signal,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: modelId,
          systemPrompt: workspace?.draftSystemPrompt || DEFAULT_SYSTEM_PROMPT,
          temperature: workspace?.draftTemperature || DEFAULT_TEMPERATURE,
          messages: [...historyMessages, userMessage].map((message) => ({
            role: message.role,
            content: message.imageName ? `${message.content}\n\n[Attached photo: ${message.imageName}]` : message.content,
          })),
        }),
      });
      if (!response.ok) {
        const payload = (await response.json().catch(() => null)) as { error?: string } | null;
        throw new Error(payload?.error || "The request failed.");
      }
      if (!response.body) throw new Error("The response had no stream.");
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let accumulated = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split("\n\n");
        buffer = parts.pop() ?? "";
        for (const part of parts) {
          const dataLine = part
            .split("\n")
            .map((line) => line.trim())
            .find((line) => line.startsWith("data:"));
          if (!dataLine) continue;
          const data = dataLine.slice(5).trim();
          if (data === "[DONE]") continue;
          const payload = JSON.parse(data) as { delta?: string; error?: string };
          if (payload.error) throw new Error(payload.error);
          if (payload.delta) {
            accumulated += payload.delta;
            const snapshot = accumulated;
            patchWorkspace((current) => ({
              ...current,
              conversations: current.conversations.map((item) =>
                item.id === conversationId
                  ? {
                      ...item,
                      updatedAt: Date.now(),
                      messages: item.messages.map((message) =>
                        message.id === assistantId ? { ...message, content: snapshot } : message,
                      ),
                    }
                  : item,
              ),
            }));
          }
        }
      }
      patchWorkspace((current) => ({
        ...current,
        conversations: current.conversations.map((item) =>
          item.id === conversationId
            ? {
                ...item,
                messages: item.messages.map((message) =>
                  message.id === assistantId
                    ? { ...message, content: accumulated, status: controller.signal.aborted ? "stopped" : "complete" }
                    : message,
                ),
              }
            : item,
        ),
      }));
      return accumulated;
    } catch (caught) {
      if (!controller.signal.aborted) {
        const message = caught instanceof Error ? caught.message : "Something went wrong.";
        setError(message);
        patchWorkspace((current) => ({
          ...current,
          conversations: current.conversations.map((item) =>
            item.id === conversationId
              ? {
                  ...item,
                  messages: item.messages.map((entry) =>
                    entry.id === assistantId ? { ...entry, status: "error", content: entry.content || message } : entry,
                  ),
                }
              : item,
          ),
        }));
      }
      return "";
    } finally {
      setStreaming(false);
      abortRef.current = null;
    }
  }

  function submitDraft() {
    const content = draft.trim();
    if (!content || streaming) return;
    const conversationId = active?.id ?? uid();
    const prior = active?.messages.filter((message) => message.status !== "streaming") ?? [];
    void send(content, prior, conversationId);
  }

  function finishOnboarding(email: string) {
    const local = email.split("@")[0]?.replace(/[._-]+/g, " ") || "dua user";
    const displayName =
      local.toLowerCase() === "user" ? "Dua User" : local.replace(/\b\w/g, (letter) => letter.toUpperCase());
    setPrefs((current) => ({
      ...current,
      onboarded: true,
      signedIn: true,
      email,
      displayName: displayName || "Dua User",
    }));
    setScreen("chat");
    setHistory([]);
  }

  const results = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) {
      return conversations.map((item) => ({
        id: item.id,
        title: item.title,
        snippet: item.messages.at(-1)?.content ?? "Empty chat",
      }));
    }
    return conversations
      .filter((item) =>
        [item.title, ...item.messages.map((message) => message.content)].join("\n").toLowerCase().includes(needle),
      )
      .map((item) => ({
        id: item.id,
        title: item.title,
        snippet: item.messages.find((message) => message.content.toLowerCase().includes(needle))?.content ?? item.title,
      }));
  }, [conversations, query]);

  async function listen() {
    if (!voiceAllowed) {
      setVoicePhase("permission");
      return;
    }
    setVoicePhase("listening");
    const Recognition = (
      window as unknown as {
        SpeechRecognition?: new () => SpeechRecognitionLike;
        webkitSpeechRecognition?: new () => SpeechRecognitionLike;
      }
    ).SpeechRecognition ||
      (window as unknown as { webkitSpeechRecognition?: new () => SpeechRecognitionLike }).webkitSpeechRecognition;
    const heard = await new Promise<string>((resolve) => {
      if (!Recognition) {
        window.setTimeout(() => resolve("Help me break down a big idea into steps"), 900);
        return;
      }
      const recognition = new Recognition();
      recognition.lang = prefs.language === "en" ? "en-US" : prefs.language;
      recognition.onresult = (event) => resolve(event.results[0]?.[0]?.transcript || "");
      recognition.onerror = () => resolve("Help me break down a big idea into steps");
      recognition.start();
    });
    setVoiceTranscript(heard);
    setVoicePhase("transcript");
    const conversationId = active?.id ?? uid();
    const prior = active?.messages.filter((message) => message.status !== "streaming") ?? [];
    const reply = (await send(heard, prior, conversationId)) ?? "";
    setVoiceReply(reply);
    if ("speechSynthesis" in window && reply) {
      const utterance = new SpeechSynthesisUtterance(reply.replace(/[#*`]/g, " ").slice(0, 400));
      utterance.rate = prefs.voice === "breeze" ? 1.05 : prefs.voice === "echo" ? 0.9 : 1;
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utterance);
    }
    setScreen("chat");
  }

  function exportData() {
    const blob = new Blob([JSON.stringify({ prefs, conversations, gallery }, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "dua-export.json";
    link.click();
    URL.revokeObjectURL(url);
    setToast("Export downloaded");
  }

  function clearHistory() {
    patchWorkspace((current) => ({ ...current, conversations: [], activeId: null }));
    setToast("Chat history cleared");
    setScreen("chat");
  }

  function deleteAccount() {
    window.localStorage.removeItem("dua.conversations.v1");
    window.localStorage.removeItem("dua.prefs.v1");
    window.localStorage.removeItem("dua.gallery.v1");
    setPrefs(defaultPreferences());
    setWorkspace({
      conversations: [],
      activeId: null,
      theme: "dark",
      draftModelId: DEMO_MODEL_ID,
      draftSystemPrompt: DEFAULT_SYSTEM_PROMPT,
      draftTemperature: DEFAULT_TEMPERATURE,
    });
    setGallery([]);
    setScreen("intro");
    setHistory([]);
    setToast("Account deleted on this device");
  }

  if (!ready || !workspace) {
    return (
      <PhoneFrame>
        <Splash />
      </PhoneFrame>
    );
  }

  const tab = screen === "search" ? "search" : screen === "profile" || screen === "signout" || screen === "delete" ? "profile" : "chat";

  return (
    <PhoneFrame
      dir={direction(prefs.language)}
      className={`${theme === "light" ? "theme-light" : ""} ${prefs.highContrast ? "theme-contrast" : ""} ${prefs.reduceMotion ? "reduce-motion" : ""} ${fontClass}`}
    >
      {screen === "splash" ? <Splash /> : null}
      {screen === "intro" ? <Onboarding onDone={finishOnboarding} onSkip={() => finishOnboarding(prefs.email)} /> : null}
      {screen === "chat" ? (
        <ChatPane
          conversation={active}
          model={model}
          models={models}
          draft={draft}
          streaming={streaming}
          error={error}
          drawerOpen={drawerOpen}
          conversations={conversations}
          onDraft={setDraft}
          onSend={submitDraft}
          onStop={() => abortRef.current?.abort()}
          onNew={() => {
            abortRef.current?.abort();
            patchWorkspace((current) => ({ ...current, activeId: null }));
            setDraft("");
            setDrawerOpen(false);
            setError(null);
          }}
          onPickModel={(id) => {
            patchWorkspace((current) => ({
              ...current,
              draftModelId: id,
              conversations: current.conversations.map((item) =>
                item.id === current.activeId ? { ...item, modelId: id } : item,
              ),
            }));
            setModelOpen(false);
          }}
          modelOpen={modelOpen}
          onToggleModels={() => setModelOpen((open) => !open)}
          onOpenDrawer={() => setDrawerOpen(true)}
          onCloseDrawer={() => setDrawerOpen(false)}
          onSelectConversation={(id) => {
            patchWorkspace((current) => ({ ...current, activeId: id }));
            setDrawerOpen(false);
          }}
          onOpenVoice={() => {
            setVoicePhase(voiceAllowed ? "idle" : "permission");
            setVoiceTranscript("");
            setVoiceReply("");
            push("voice");
          }}
          onOpenImagine={() => push("imagine")}
          onAttach={() => fileRef.current?.click()}
          onRegenerate={() => {
            if (!active || streaming) return;
            const messages = [...active.messages];
            if (messages.at(-1)?.role === "assistant") messages.pop();
            const previous = messages.at(-1);
            if (!previous || previous.role !== "user") return;
            const prior = messages.slice(0, -1);
            patchWorkspace((current) => ({
              ...current,
              conversations: current.conversations.map((item) =>
                item.id === active.id ? { ...item, messages: prior } : item,
              ),
            }));
            void send(previous.content, prior, active.id, previous.imageName);
          }}
          onCopy={(text) => {
            void navigator.clipboard.writeText(text);
            setToast("Copied");
          }}
        />
      ) : null}
      {screen === "search" ? (
        <SearchPane
          query={query}
          onQuery={setQuery}
          results={results}
          onOpen={(id) => {
            patchWorkspace((current) => ({ ...current, activeId: id }));
            goTab("chat");
          }}
        />
      ) : null}
      {screen === "profile" ? (
        <ProfilePane
          prefs={prefs}
          locale={prefs.language}
          onSettings={() => push("settings")}
          onAbout={() => push("about")}
          onTheme={(dark) => patchPrefs({ theme: dark ? "dark" : "light" })}
          onSignOut={() => setScreen("signout")}
          onDelete={() => push("delete")}
        />
      ) : null}
      {screen === "settings" ? (
        <SettingsHome
          prefs={prefs}
          onBack={back}
          onChange={patchPrefs}
          onOpen={(next) => push(next as Screen)}
        />
      ) : null}
      {screen === "appearance" ? <AppearanceScreen prefs={prefs} onBack={back} onChange={patchPrefs} /> : null}
      {screen === "language" ? (
        <LanguageScreen locale={prefs.language} onBack={back} onPick={(language) => patchPrefs({ language })} />
      ) : null}
      {screen === "memory" ? <MemoryScreen prefs={prefs} onBack={back} onChange={patchPrefs} onClear={clearHistory} /> : null}
      {screen === "privacy" ? (
        <PrivacyScreen onBack={back} onExport={exportData} onClear={clearHistory} onDelete={() => push("delete")} />
      ) : null}
      {screen === "notifications" ? (
        <NotificationsScreen prefs={prefs} onBack={back} onChange={patchPrefs} saved={noticeSaved} />
      ) : null}
      {screen === "about" ? (
        <AboutScreen version={prefs.version} onBack={back} onUpdate={() => push("update")} onComing={() => push("coming")} />
      ) : null}
      {screen === "update" ? <UpdateScreen onBack={() => { setToast("You’re already on this demo build"); back(); }} onLater={back} /> : null}
      {screen === "coming" ? <ComingSoon onBack={() => goTab("chat")} /> : null}
      {screen === "preview" && photo ? (
        <PreviewScreen
          name={photo.name}
          url={photo.url}
          caption={caption}
          onCaption={setCaption}
          onClose={() => setScreen("chat")}
          onRemove={() => {
            setPhoto(null);
            setScreen("chat");
          }}
          onAdd={() => {
            const conversationId = active?.id ?? uid();
            const prior = active?.messages ?? [];
            const text = caption.trim() || "What do you notice in this photo?";
            void send(text, prior, conversationId, photo.name);
            setPhoto(null);
            setCaption("");
            setScreen("chat");
          }}
        />
      ) : null}
      {screen === "voice" || screen === "voice-settings" ? (
        <VoicePane
          phase={screen === "voice-settings" ? "settings" : voicePhase}
          transcript={voiceTranscript}
          reply={voiceReply}
          voice={prefs.voice}
          onVoice={(voice) => patchPrefs({ voice })}
          onBack={back}
          onMic={() => void listen()}
          onAllow={() => {
            setVoiceAllowed(true);
            setVoicePhase("idle");
          }}
          onDeny={() => back()}
          onOpenSettings={() => push("voice-settings")}
        />
      ) : null}
      {screen === "imagine" || screen === "imagine-working" || screen === "imagine-result" || screen === "gallery" ? (
        <ImaginePane
          screen={
            screen === "imagine-working"
              ? "working"
              : screen === "imagine-result"
                ? "result"
                : screen === "gallery"
                  ? "gallery"
                  : "compose"
          }
          prompt={imagePrompt}
          style={imageStyle}
          ratio={imageRatio}
          gallery={gallery}
          onPrompt={setImagePrompt}
          onStyle={setImageStyle}
          onRatio={setImageRatio}
          onBack={back}
          onCreate={() => {
            setScreen("imagine-working");
            window.setTimeout(() => setScreen("imagine-result"), 900);
          }}
          onSave={() => {
            setGallery((current) => [
              {
                id: uid(),
                prompt: imagePrompt,
                style: imageStyle,
                ratio: imageRatio,
                createdAt: Date.now(),
              },
              ...current,
            ]);
            setToast("Saved to your gallery");
          }}
          onDelete={() => {
            setToast("Image discarded");
            setScreen("imagine");
          }}
          onOpen={(next) => setScreen(next === "gallery" ? "gallery" : "imagine")}
        />
      ) : null}
      {screen === "font" ? <FontScreen font={prefs.font} onBack={back} onPick={(font) => patchPrefs({ font })} /> : null}
      {screen === "comparison" ? (
        <SimpleList
          title="Comparison mode"
          onBack={back}
          rows={[
            {
              title: prefs.comparison ? "Turn off" : "Turn on",
              detail: "Marks the composer so you can compare a hosted model with Dua Demo.",
              onClick: () => patchPrefs({ comparison: !prefs.comparison }),
            },
          ]}
        />
      ) : null}
      {screen === "capabilities" ? (
        <SimpleList
          title="Capabilities"
          onBack={back}
          rows={[
            { title: "Chat", detail: "Active" },
            { title: "Search", detail: "Active" },
            { title: "Voice", detail: "Active in this browser" },
            { title: "Images", detail: "Local previews" },
            { title: "Collaborative chats", detail: t(prefs.language, "comingSoon"), onClick: () => push("coming") },
          ]}
        />
      ) : null}
      {screen === "signout" ? (
        <ConfirmDialog
          title="Sign out?"
          body="You will leave the active session on this device. Chats stay in this browser until you delete them."
          confirm="Sign out"
          onCancel={() => setScreen("profile")}
          onConfirm={() => {
            patchPrefs({ signedIn: false, onboarded: false });
            setScreen("intro");
          }}
        />
      ) : null}
      {screen === "delete" ? (
        <ConfirmDialog
          title="Delete account?"
          body="This removes the profile, chats, and gallery stored in this browser."
          confirm="Delete account"
          danger
          onCancel={back}
          onConfirm={deleteAccount}
        />
      ) : null}
      {showTabs() && screen !== "signout" && screen !== "delete" ? (
        <TabBar
          tab={tab}
          labels={{
            chat: t(prefs.language, "chat"),
            search: t(prefs.language, "search"),
            profile: t(prefs.language, "profile"),
          }}
          onChange={goTab}
        />
      ) : null}
      {toast ? <Toast text={toast} /> : null}
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = "";
          if (!file) return;
          const url = URL.createObjectURL(file);
          setPhoto({ name: file.name, url });
          setCaption("");
          setScreen("preview");
        }}
      />
    </PhoneFrame>
  );
}

type SpeechRecognitionLike = {
  lang: string;
  start: () => void;
  onresult: ((event: { results: Array<Array<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
};
