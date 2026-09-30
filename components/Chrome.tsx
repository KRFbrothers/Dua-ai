"use client";

import type { ReactNode } from "react";

export function StatusBar() {
  return (
    <div className="hidden h-11 shrink-0 items-center justify-between px-6 text-[13px] font-medium md:flex">
      <span>9:30</span>
      <span className="flex items-center gap-1.5 text-[11px] tracking-wide">
        <span aria-hidden>●●●</span>
        <span className="rounded-sm border border-current px-1 py-px text-[10px]">5G</span>
      </span>
    </div>
  );
}

export function PhoneFrame({
  children,
  className = "",
  dir = "ltr",
}: {
  children: ReactNode;
  className?: string;
  dir?: "ltr" | "rtl";
}) {
  return (
    <div className="min-h-dvh bg-[#0e0d11] text-[var(--text)] md:grid md:place-items-center md:py-6">
      <div
        dir={dir}
        className={`relative mx-auto flex h-dvh w-full max-w-[430px] flex-col overflow-hidden bg-[var(--bg)] md:h-[min(920px,calc(100dvh-3rem))] md:rounded-[1.75rem] md:border md:border-[var(--line)] ${className}`}
      >
        <StatusBar />
        {children}
      </div>
    </div>
  );
}

export function TopBar({
  title,
  onBack,
  backLabel = "Back",
  trailing,
}: {
  title: string;
  onBack?: () => void;
  backLabel?: string;
  trailing?: ReactNode;
}) {
  return (
    <header className="flex h-14 shrink-0 items-center gap-2 px-3">
      {onBack ? (
        <button
          type="button"
          onClick={onBack}
          aria-label={backLabel}
          className="grid h-10 w-10 place-items-center rounded-full text-lg"
        >
          ‹
        </button>
      ) : (
        <span className="w-10" />
      )}
      <h1 className="flex-1 truncate text-center text-[17px] font-semibold">{title}</h1>
      <div className="flex w-10 justify-end">{trailing}</div>
    </header>
  );
}

export function CubeMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <polygon points="32,4 58,18 32,32 6,18" fill="#e8e8ea" />
      <polygon points="6,18 32,32 32,58 6,44" fill="#5f5f66" />
      <polygon points="58,18 32,32 32,58 58,44" fill="#8d8d96" />
      <polygon points="32,32 46,24 32,40 18,32" fill="#f7f7f8" />
      <polygon points="32,32 46,40 32,48 18,40" fill="#f4f4f5" opacity="0.95" />
    </svg>
  );
}

export function TabBar({
  tab,
  labels,
  onChange,
}: {
  tab: "chat" | "search" | "profile";
  labels: { chat: string; search: string; profile: string };
  onChange: (tab: "chat" | "search" | "profile") => void;
}) {
  const items = [
    { id: "chat" as const, label: labels.chat, icon: "💬" },
    { id: "search" as const, label: labels.search, icon: "⌕" },
    { id: "profile" as const, label: labels.profile, icon: "☰" },
  ];
  return (
    <nav className="grid h-20 shrink-0 grid-cols-3 bg-[var(--card)] px-2 pb-[max(0.35rem,env(safe-area-inset-bottom))]">
      {items.map((item) => {
        const active = tab === item.id;
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => onChange(item.id)}
            className={`flex flex-col items-center justify-center gap-1 text-[12px] font-medium tracking-wide ${active ? "text-[var(--text)]" : "text-[var(--muted)]"}`}
          >
            <span
              className={`grid h-8 w-16 place-items-center rounded-2xl text-base leading-none ${active ? "bg-[var(--indicator)] text-[var(--text)]" : ""}`}
            >
              {item.icon}
            </span>
            {item.label}
          </button>
        );
      })}
    </nav>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative h-8 w-[3.25rem] rounded-full transition ${checked ? "bg-[var(--accent)]" : "bg-[var(--card-2)]"}`}
    >
      <span
        className={`absolute top-1 grid h-6 w-6 place-items-center rounded-full text-[10px] transition ${checked ? "left-6 bg-[var(--on-accent)] text-[var(--accent)]" : "left-1 bg-[var(--muted)] text-[var(--card)]"}`}
      >
        {checked ? "✓" : ""}
      </span>
    </button>
  );
}

export function Sheet({
  title,
  children,
  onClose,
}: {
  title?: string;
  children: ReactNode;
  onClose?: () => void;
}) {
  return (
    <div className="absolute inset-0 z-40 flex items-end bg-black/55" onClick={onClose}>
      <div
        className="w-full rounded-t-[1.6rem] bg-[var(--bg-elev)] px-5 pt-3 pb-6"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mx-auto mb-4 h-1.5 w-10 rounded-full bg-white/15" />
        {title ? <h2 className="mb-3 text-center text-lg font-semibold">{title}</h2> : null}
        {children}
      </div>
    </div>
  );
}

export function Toast({ text }: { text: string }) {
  return (
    <div className="pointer-events-none absolute inset-x-4 bottom-24 z-20 rounded-2xl bg-[var(--card)] px-4 py-3 text-center text-sm shadow-lg">
      {text}
    </div>
  );
}

export function Row({
  title,
  detail,
  onClick,
  trailing,
  danger = false,
}: {
  title: string;
  detail?: string;
  onClick?: () => void;
  trailing?: ReactNode;
  danger?: boolean;
}) {
  const Comp = onClick ? "button" : "div";
  return (
    <Comp
      type={onClick ? "button" : undefined}
      onClick={onClick}
      className={`flex w-full items-center gap-3 px-4 py-3 text-left ${danger ? "text-[var(--danger)]" : ""}`}
    >
      <span className="min-w-0 flex-1">
        <span className="block text-[15px]">{title}</span>
        {detail ? <span className="block text-xs text-[var(--muted)]">{detail}</span> : null}
      </span>
      {trailing ?? (onClick ? <span className="text-[var(--faint)]">›</span> : null)}
    </Comp>
  );
}

export function Card({ children }: { children: ReactNode }) {
  return <div className="overflow-hidden rounded-xl bg-[var(--card-2)]">{children}</div>;
}

export function Pill({ children }: { children: ReactNode }) {
  return (
    <span className="rounded-lg bg-[var(--indicator)] px-2 py-0.5 text-[11px] font-medium tracking-wide text-[var(--text)]">
      {children}
    </span>
  );
}

export function PrimaryButton({
  children,
  onClick,
  type = "button",
  disabled = false,
}: {
  children: ReactNode;
  onClick?: () => void;
  type?: "button" | "submit";
  disabled?: boolean;
}) {
  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      className="m3-filled w-full rounded-full px-6 py-3 text-[15px] font-medium tracking-wide"
    >
      {children}
    </button>
  );
}
