import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Vite dev server. /api is proxied to the Spring Boot backend
// so the frontend code can call relative paths like "/api/auth/login".
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
