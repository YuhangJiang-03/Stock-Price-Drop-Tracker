import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Vite dev server. /api is proxied to the Spring Boot backend with the
// path preserved — the backend now serves every controller under /api/**
// (see WebConfig.configurePathMatch on the server) so the dev and prod
// URL shapes match exactly. In production the SPA is served by Spring
// Boot itself and there's no proxy involved.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
