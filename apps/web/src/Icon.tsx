const paths = {
  menu: 'M4 7h16M4 12h16M4 17h16', plus: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6 6 18', search: 'M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13Zm5-1 5 5',
  send: 'M12 20V4M5 11l7-7 7 7', mic: 'M9 6a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0V6ZM5 11a7 7 0 0 0 14 0M12 18v3',
  wave: 'M6 9v6M12 4v16M18 8v8', chat: 'M5 5h14v11H9l-4 4V5Z',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2',
  stop: 'M6 6h12v12H6V6Z', folder: 'M3 6h7l2 2h9v12H3V6Z',
} as const;
export default function Icon({ name }: { name: keyof typeof paths }) {
  return <svg className="web-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={paths[name]} /></svg>;
}
