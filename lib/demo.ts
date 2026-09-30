const CODE_HINT =
  /\b(code|function|typescript|javascript|python|bug|snippet|regex|sql|component)\b/i;
const PLAN_HINT = /\b(plan|roadmap|steps|outline|schedule|checklist)\b/i;
const COMPARE_HINT = /\b(compare|vs\.?|versus|difference between|trade-?offs?)\b/i;
const EXPLAIN_HINT = /\b(explain|what is|why|how does|eli5|summarize|summary)\b/i;

function clip(text: string, max = 180): string {
  const clean = text.replace(/\s+/g, " ").trim();
  if (clean.length <= max) return clean;
  return `${clean.slice(0, max - 1).trimEnd()}…`;
}

function blocks(lines: Array<string | false>): string {
  return lines.filter((line): line is string => line !== false).join("\n");
}

function subject(latest: string): string {
  const clean = latest.replace(/\s+/g, " ").trim();
  if (!clean) return "that";
  return clean.length > 90 ? `${clean.slice(0, 87).trimEnd()}…` : clean;
}

export function titleFromMessage(text: string): string {
  const clean = text.replace(/\s+/g, " ").trim();
  if (!clean) return "New chat";
  const sentence = clean.split(/(?<=[.!?])\s/)[0] ?? clean;
  return sentence.length > 52 ? `${sentence.slice(0, 49).trimEnd()}…` : sentence;
}

export function buildDemoReply(latest: string, priorUserMessages: string[] = []): string {
  const question = latest.trim();
  if (!question) {
    return "Send a message and I will answer it here. This demo model runs in the app, with no API key.";
  }

  const earlier = priorUserMessages
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(-2);
  const memory = earlier.length
    ? `Earlier you also asked about ${earlier.map((item) => `“${clip(item, 80)}”`).join(" and ")}.`
    : "";

  if (/^(hi|hello|hey|yo|good morning|good evening)\b/i.test(question)) {
    return blocks([
      "Hello. I am **Dua Demo**, the built-in model for this workspace.",
      "",
      "I can sketch a plan, explain an idea, or draft a small code sample. Hosted models from OpenAI, Anthropic, and Gemini appear in the model menu once their API keys are set.",
      "",
      "What do you want to work on?",
    ]);
  }

  if (CODE_HINT.test(question)) {
    return blocks([
      `Here is a small, readable starting point for: **${subject(question)}**.`,
      "",
      "```ts",
      "export function focusReply(prompt: string): string[] {",
      "  const goal = prompt.trim();",
      "  return [",
      "    `Goal: ${goal}`,",
      "    \"Constraint: keep the first version small enough to review.\",",
      "    \"Check: one happy path and one empty input.\",",
      "  ];",
      "}",
      "```",
      "",
      "How to use it:",
      "",
      "1. Replace the placeholder lines with the real rule from your prompt.",
      "2. Keep the function pure so you can test it without a server.",
      "3. Add one assertion for an empty string and one for a typical sentence.",
      "",
      memory || false,
      memory ? "" : false,
      "If you paste the real function or error, I can walk through a tighter version.",
    ]);
  }

  if (PLAN_HINT.test(question)) {
    return blocks([
      `A practical sequence for **${subject(question)}**:`,
      "",
      "1. **Name the outcome.** Write one sentence a reviewer could check.",
      "2. **Cut the first slice.** Ship the path a person can complete in one sitting.",
      "3. **List the risks.** Call out data, permissions, and empty states before polish.",
      "4. **Prove it.** Exercise the path once, then fix what the exercise shows.",
      "",
      memory || false,
      memory ? "" : false,
      "I can turn this into a checklist for a specific stack if you name it.",
    ]);
  }

  if (COMPARE_HINT.test(question)) {
    return blocks([
      `A useful comparison for **${subject(question)}** looks at the decision, not a feature grid.`,
      "",
      "| Lens | Prefer the simpler option when | Prefer the richer option when |",
      "| --- | --- | --- |",
      "| Time to first result | You need an answer in this session | Quality changes a decision |",
      "| Cost | Traffic is exploratory | Each answer is high stakes |",
      "| Control | Defaults are acceptable | You must pin behavior and logs |",
      "",
      memory || false,
      memory ? "" : false,
      "Tell me the two options and the constraint that matters most, and I will pick one.",
    ]);
  }

  if (EXPLAIN_HINT.test(question)) {
    return blocks([
      `**${subject(question)}**`,
      "",
      "In short: start from the outcome you can observe, then name the moving parts that produce it.",
      "",
      "- The core idea is the smallest claim that still answers the question.",
      "- A concrete check is an input you can run and an output you can compare.",
      "- The usual trap is adding mechanism before that check exists.",
      "",
      memory || false,
      memory ? "" : false,
      "If you share the audience (beginner, teammate, or customer), I will retell this at that depth.",
    ]);
  }

  return blocks([
    `Here is a direct take on **${subject(question)}**.`,
    "",
    "- State the result you want in one sentence.",
    "- Do the smallest action that produces evidence.",
    "- Keep the next step specific enough to finish today.",
    "",
    memory || false,
    memory ? "" : false,
    "Dua Demo drafts this structure locally. Switch to a hosted model in the menu when you want a full generative answer.",
  ]);
}

export function chunkText(text: string, size = 18): string[] {
  if (!text) return [];
  const chunks: string[] = [];
  for (let index = 0; index < text.length; index += size) {
    chunks.push(text.slice(index, index + size));
  }
  return chunks;
}
