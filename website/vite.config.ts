import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Built into web/site, which dua-api serves at / (same origin as /api/*).
// Local dev: DUA_API_URL=https://dua-api-228305912525.us-central1.run.app npm run dev
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { outDir: '../web/site', emptyOutDir: true, sourcemap: false },
  server: {
    port: 5173,
    proxy: process.env.DUA_API_URL ? { '/api': { target: process.env.DUA_API_URL, changeOrigin: true, secure: true } } : undefined
  }
});
