export type Artwork = {
  id: string;
  prompt: string;
  style: string;
  ratio: "square" | "portrait" | "wide";
  createdAt: number;
};

const KEY = "dua.gallery.v1";

export function loadGallery(): Artwork[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Artwork[]) : [];
  } catch {
    return [];
  }
}

export function saveGallery(items: Artwork[]): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(KEY, JSON.stringify(items));
}

function hash(text: string): number {
  let value = 0;
  for (let index = 0; index < text.length; index += 1) {
    value = (value * 33 + text.charCodeAt(index)) >>> 0;
  }
  return value;
}

export function artworkSvg(prompt: string, style: string, ratio: Artwork["ratio"]): string {
  const seed = hash(`${style}:${prompt}`);
  const hue = seed % 360;
  const hue2 = (hue + 40) % 360;
  const width = ratio === "portrait" ? 360 : ratio === "wide" ? 640 : 480;
  const height = ratio === "portrait" ? 560 : ratio === "wide" ? 360 : 480;
  const wash =
    style === "Watercolor"
      ? `hsla(${hue} 70% 60% / 0.85)`
      : style === "Cinematic"
        ? `hsl(${hue} 35% 18%)`
        : `hsl(${hue} 45% 42%)`;
  const sky = style === "Cinematic" ? "#120c18" : `hsl(${hue2} 55% ${style === "Watercolor" ? 78 : 62}%)`;
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${sky}"/>
      <stop offset="1" stop-color="${wash}"/>
    </linearGradient>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#sky)"/>
  <circle cx="${width * 0.78}" cy="${height * 0.18}" r="${height * 0.08}" fill="rgba(255,255,255,0.85)"/>
  <path d="M0 ${height * 0.62} C ${width * 0.25} ${height * 0.5}, ${width * 0.4} ${height * 0.74}, ${width} ${height * 0.58} L ${width} ${height} L 0 ${height} Z" fill="rgba(0,0,0,0.28)"/>
  <path d="M0 ${height * 0.74} C ${width * 0.3} ${height * 0.66}, ${width * 0.55} ${height * 0.86}, ${width} ${height * 0.7} L ${width} ${height} L 0 ${height} Z" fill="rgba(255,255,255,0.18)"/>
  <text x="28" y="${height - 28}" fill="white" font-family="ui-sans-serif, system-ui" font-size="16" opacity="0.9">${escapeXml(prompt.slice(0, 42))}</text>
</svg>`;
}

function escapeXml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

export function svgDataUrl(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}
