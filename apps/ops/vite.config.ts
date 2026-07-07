/// <reference types="vitest" />
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

// vitest@2 ships its own (slightly older) `vite` types — casting the
// plugin array avoids a transient type mismatch across the duplicated
// vite installs. Runtime is unaffected.
export default defineConfig({
  // The backend serves this SPA under /ops/* in dev and prod (single
  // origin = no CORS, cookies just work). All built asset URLs must
  // resolve against that prefix.
  base: "/ops/",
  plugins: [react(), tailwindcss()] as never,
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
