"use client";

import { useEffect, useRef } from "react";
import { CubeMark } from "@/components/Chrome";
import { MarkdownView } from "@/components/MarkdownView";
import type { ChatMessage, Conversation, ModelInfo } from "@/lib/types";

const SUGGESTIONS = [
  "Help me break down a big idea into steps",
  "Explain a small piece of code",
  "Draft a plan I can finish today",
  "Compare two options and pick one",
];

export function ChatPane({
  conversation,
  model,
  models,
  draft,
  streaming,
  error,
  drawerOpen,
  conversations,
  onDraft,
  onSend,
  onStop,
  onNew,
  onPickModel,
  modelOpen,
  onToggleModels,
  onOpenDrawer,
  onCloseDrawer,
  onSelectConversation,
  onOpenVoice,
  onOpenImagine,
  onAttach,
  onRegenerate,
  onCopy,
}: {
  conversation: Conversation | null;
  model: ModelInfo | undefined;
  models: ModelInfo[];
  draft: string;
  streaming: boolean;
  error: string | null;
  drawerOpen: boolean;
  conversations: Conversation[];
  onDraft: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  onNew: () => void;
  onPickModel: (id: string) => void;
  modelOpen: boolean;
  onToggleModels: () => void;
  onOpenDrawer: () => void;
  onCloseDrawer: () => void;
  onSelectConversation: (id: string) => void;
  onOpenVoice: () => void;
  onOpenImagine: () => void;
  onAttach: () => void;
  onRegenerate: () => void;
  onCopy: (text: string) => void;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const messages = conversation?.messages ?? [];

  useEffect(() => {
    const node = scroller.current;
    if (!node) return;
    node.scrollTop = node.scrollHeight;
  }, [messages, streaming]);

  return (
    <div className="relative flex min-h-0 flex-1 flex-col">
      <header className="flex h-14 shrink-0 items-center gap-1 px-2">
        <button type="button" aria-label="Chats" onClick={onOpenDrawer} className="grid h-10 w-10 place-items-center text-lg">
          ☰
        </button>
        <button type="button" onClick={onToggleModels} className="mx-auto flex items-center gap-2 text-[15px] font-medium">
          <span>{model?.label ?? "Dua Demo"}</span>
          <span className="text-xs text-[var(--muted)]">▾</span>
        </button>
        <button type="button" aria-label="New chat" onClick={onNew} className="grid h-10 w-10 place-items-center text-xl">
          +
        </button>
      </header>

      <div ref={scroller} className="scrollbar-thin min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        {messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center pb-10 text-center">
            <CubeMark size={92} />
            <p className="mt-5 max-w-[16rem] text-lg font-medium">What should we untangle?</p>
            <div className="mt-6 grid w-full gap-2">
              {SUGGESTIONS.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => onDraft(suggestion)}
                  className="rounded-lg bg-[var(--card)] px-4 py-3 text-left text-sm text-[var(--text)]"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-4 py-2">
            {messages.map((message) => (
              <Message
                key={message.id}
                message={message}
                onCopy={onCopy}
                onRegenerate={message.role === "assistant" ? onRegenerate : undefined}
              />
            ))}
          </div>
        )}
      </div>

      {error ? <p className="px-4 pb-2 text-sm text-[var(--danger)]">{error}</p> : null}

      <form
        className="px-3 pb-2"
        onSubmit={(event) => {
          event.preventDefault();
          if (!streaming) onSend();
        }}
      >
        <div className="flex items-end gap-2 rounded-[1.6rem] bg-[var(--card)] px-2 py-2">
          <button type="button" aria-label="Attach" onClick={onAttach} className="grid h-10 w-10 place-items-center text-lg text-[var(--muted)]">
            +
          </button>
          <textarea
            id="composer"
            rows={1}
            value={draft}
            placeholder="Message Dua"
            onChange={(event) => onDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                if (!streaming) onSend();
              }
            }}
            className="max-h-28 min-h-10 flex-1 resize-none bg-transparent py-2 text-[15px] outline-none placeholder:text-[var(--faint)]"
          />
          <button type="button" aria-label="Voice" onClick={onOpenVoice} className="grid h-10 w-10 place-items-center text-[var(--muted)]">
            ●
          </button>
          {streaming ? (
            <button type="button" onClick={onStop} className="grid h-10 w-10 place-items-center rounded-full bg-white text-sm text-black">
              ■
            </button>
          ) : (
            <button
              type="submit"
              disabled={!draft.trim()}
              aria-label="Send"
              className="m3-filled grid h-10 w-10 place-items-center rounded-full disabled:opacity-40"
            >
              ↑
            </button>
          )}
        </div>
        <div className="mt-2 flex justify-center">
          <button type="button" onClick={onOpenImagine} className="text-xs text-[var(--muted)]">
            Create an image
          </button>
        </div>
      </form>

      {modelOpen ? (
        <div className="absolute inset-0 z-30 flex items-end bg-black/50" onClick={onToggleModels}>
          <div className="max-h-full w-full overflow-y-auto rounded-t-[1.6rem] bg-[var(--bg-elev)] p-4" onClick={(event) => event.stopPropagation()}>
            <p className="mb-3 text-center text-sm text-[var(--muted)]">Model</p>
            <div className="space-y-2 pb-2">
              {models.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  disabled={!item.available}
                  onClick={() => onPickModel(item.id)}
                  className="flex w-full items-center justify-between rounded-2xl bg-[var(--card)] px-4 py-3 text-left disabled:opacity-45"
                >
                  <span>
                    <span className="block text-sm font-medium">{item.label}</span>
                    <span className="block text-xs text-[var(--muted)]">
                      {item.available ? item.description : item.unavailableReason}
                    </span>
                  </span>
                  {item.id === model?.id ? <span className="text-[var(--accent-2)]">●</span> : null}
                </button>
              ))}
            </div>
          </div>
        </div>
      ) : null}

      {drawerOpen ? (
        <div className="absolute inset-0 z-30 flex bg-black/50" onClick={onCloseDrawer}>
          <aside className="h-full w-[82%] overflow-y-auto bg-[var(--bg)] p-4" onClick={(event) => event.stopPropagation()}>
            <div className="mb-4 flex items-center justify-between">
              <p className="text-lg font-semibold">Chats</p>
              <button type="button" onClick={onNew} className="m3-filled rounded-full px-4 py-1.5 text-sm font-medium">
                New
              </button>
            </div>
            {conversations.length === 0 ? (
              <p className="text-sm text-[var(--muted)]">No chats yet.</p>
            ) : (
              <ul className="space-y-1">
                {conversations.map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => onSelectConversation(item.id)}
                      className="w-full truncate rounded-2xl px-3 py-3 text-left text-sm hover:bg-[var(--card)]"
                    >
                      {item.title}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </aside>
        </div>
      ) : null}
    </div>
  );
}

function Message({
  message,
  onCopy,
  onRegenerate,
}: {
  message: ChatMessage;
  onCopy: (text: string) => void;
  onRegenerate?: () => void;
}) {
  const mine = message.role === "user";
  return (
    <article className={`flex ${mine ? "justify-end" : "justify-start"}`}>
      <div className={`max-w-[88%] ${mine ? "" : "w-full"}`}>
        {!mine ? (
          <div className="mb-1 flex items-center gap-2 text-xs text-[var(--muted)]">
            <CubeMark size={16} />
            <span>{message.status === "streaming" ? "Writing" : "Dua"}</span>
          </div>
        ) : null}
        <div className={`rounded-[1.3rem] px-3.5 py-2.5 ${mine ? "bg-[var(--user)]" : "bg-[var(--card)]"}`}>
          {message.imageName ? (
            <p className="mb-1 text-xs text-[var(--muted)]">Photo · {message.imageName}</p>
          ) : null}
          {mine ? <p className="whitespace-pre-wrap text-[15px]">{message.content}</p> : <MarkdownView content={message.content || " "} />}
        </div>
        {message.content ? (
          <div className="mt-1 flex gap-3 px-1 text-[11px] text-[var(--faint)]">
            <button type="button" onClick={() => onCopy(message.content)}>
              Copy
            </button>
            {onRegenerate ? (
              <button type="button" onClick={onRegenerate}>
                Regenerate
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </article>
  );
}
