import type { ModelInfo, ProviderId } from "./types.ts";

export const DEMO_MODEL_ID = "dua-demo";

type ModelSeed = {
  id: string;
  label: string;
  provider: ProviderId;
  description: string;
  envKeys: string[];
};

const SEEDS: ModelSeed[] = [
  {
    id: DEMO_MODEL_ID,
    label: "Dua Demo",
    provider: "demo",
    description: "On-device replies. No API key required.",
    envKeys: [],
  },
  {
    id: "gpt-4o-mini",
    label: "GPT-4o mini",
    provider: "openai",
    description: "Fast OpenAI model for everyday chat.",
    envKeys: ["OPENAI_API_KEY"],
  },
  {
    id: "gpt-4o",
    label: "GPT-4o",
    provider: "openai",
    description: "OpenAI flagship model for harder tasks.",
    envKeys: ["OPENAI_API_KEY"],
  },
  {
    id: "claude-sonnet-4-5",
    label: "Claude Sonnet 4.5",
    provider: "anthropic",
    description: "Anthropic model for long, careful answers.",
    envKeys: ["ANTHROPIC_API_KEY"],
  },
  {
    id: "gemini-2.5-flash",
    label: "Gemini 2.5 Flash",
    provider: "gemini",
    description: "Google Gemini model tuned for speed.",
    envKeys: ["GEMINI_API_KEY"],
  },
];

function keyConfigured(name: string, env: Record<string, string | undefined>): boolean {
  const value = env[name];
  return typeof value === "string" && value.trim().length > 0;
}

export function listModels(env: Record<string, string | undefined> = process.env): ModelInfo[] {
  return SEEDS.map((seed) => {
    const missing = seed.envKeys.filter((name) => !keyConfigured(name, env));
    const available = missing.length === 0;
    return {
      id: seed.id,
      label: seed.label,
      provider: seed.provider,
      description: seed.description,
      available,
      unavailableReason: available
        ? undefined
        : `Set ${missing.join(" and ")} to use this model.`,
    };
  });
}

export function findModel(
  id: string,
  env: Record<string, string | undefined> = process.env,
): ModelInfo | undefined {
  return listModels(env).find((model) => model.id === id);
}

export function providerFor(id: string): ProviderId | undefined {
  return SEEDS.find((seed) => seed.id === id)?.provider;
}
