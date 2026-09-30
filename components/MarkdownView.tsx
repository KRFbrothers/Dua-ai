"use client";

import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

function CodeBlock({ code, language }: { code: string; language: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  }

  return (
    <div className="overflow-hidden rounded-2xl bg-[#141418] text-[#f4f4f6]">
      <div className="flex items-center justify-between px-3 py-2 text-[11px] tracking-[0.14em] text-white/60 uppercase">
        <span>{language || "code"}</span>
        <button type="button" onClick={() => void copy()} className="tracking-normal text-white/80">
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
      <pre className="px-3 pb-3">
        <code>{code}</code>
      </pre>
    </div>
  );
}

export function MarkdownView({ content }: { content: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre({ children }) {
            return <>{children}</>;
          },
          code({ className, children }) {
            const text = String(children).replace(/\n$/, "");
            const language = className?.replace("language-", "") ?? "";
            const fenced = Boolean(className) || text.includes("\n");
            if (!fenced) return <code>{text}</code>;
            return <CodeBlock code={text} language={language} />;
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
