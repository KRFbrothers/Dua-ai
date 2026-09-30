export const LOCALES = [
  { id: "en", label: "English (United States)", dir: "ltr" },
  { id: "es", label: "Español", dir: "ltr" },
  { id: "fr", label: "Français", dir: "ltr" },
  { id: "de", label: "Deutsch", dir: "ltr" },
  { id: "he", label: "עברית", dir: "rtl" },
] as const;

export type LocaleId = (typeof LOCALES)[number]["id"];

const copy = {
  en: {
    chat: "Chat",
    search: "Search",
    profile: "Profile",
    newChat: "New chat",
    message: "Message Dua",
    send: "Send",
    settings: "Settings",
    back: "Back",
    comingSoon: "Coming soon",
    tryNow: "Try it now",
    learnMore: "Learn more",
  },
  es: {
    chat: "Chat",
    search: "Buscar",
    profile: "Perfil",
    newChat: "Chat nuevo",
    message: "Escribe a Dua",
    send: "Enviar",
    settings: "Ajustes",
    back: "Atrás",
    comingSoon: "Próximamente",
    tryNow: "Probar ahora",
    learnMore: "Más información",
  },
  fr: {
    chat: "Discussion",
    search: "Recherche",
    profile: "Profil",
    newChat: "Nouvelle discussion",
    message: "Écrire à Dua",
    send: "Envoyer",
    settings: "Réglages",
    back: "Retour",
    comingSoon: "Bientôt",
    tryNow: "Essayer",
    learnMore: "En savoir plus",
  },
  de: {
    chat: "Chat",
    search: "Suche",
    profile: "Profil",
    newChat: "Neuer Chat",
    message: "Nachricht an Dua",
    send: "Senden",
    settings: "Einstellungen",
    back: "Zurück",
    comingSoon: "Demnächst",
    tryNow: "Jetzt testen",
    learnMore: "Mehr erfahren",
  },
  he: {
    chat: "צ׳אט",
    search: "חיפוש",
    profile: "פרופיל",
    newChat: "צ׳אט חדש",
    message: "כתבו ל-Dua",
    send: "שליחה",
    settings: "הגדרות",
    back: "חזרה",
    comingSoon: "בקרוב",
    tryNow: "נסו עכשיו",
    learnMore: "למידע נוסף",
  },
} as const;

export type CopyKey = keyof (typeof copy)["en"];

export function t(locale: string, key: CopyKey): string {
  const table = copy[(locale as LocaleId) in copy ? (locale as LocaleId) : "en"];
  return table[key];
}

export function direction(locale: string): "rtl" | "ltr" {
  return LOCALES.find((item) => item.id === locale)?.dir ?? "ltr";
}
