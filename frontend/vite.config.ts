import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  // Puerto del dev server por env (VITE_DEV_PORT), default 5173. Alinealo con el CORS del backend:
  // en máquinas con imedba/GIA ocupando 5173 se usa 5174 (ya permitido en APP_CORS_ALLOWED_ORIGINS).
  const port = Number(env.VITE_DEV_PORT ?? 5173);
  return {
    plugins: [react()],
    server: {
      // localhost (no 127.0.0.1): el browser trata localhost / 127.0.0.1 como orígenes distintos
      // y el CORS del backend está fijado a localhost.
      host: "localhost",
      port,
      strictPort: true,
    },
  };
});
