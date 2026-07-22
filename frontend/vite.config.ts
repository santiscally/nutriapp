import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // localhost (no 127.0.0.1): el back tiene CORS fijado a http://localhost:5173
    // y el browser trata localhost / 127.0.0.1 como orígenes distintos.
    host: "localhost",
    port: 5173,
    strictPort: true,
  },
});
