/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: {
          base: '#0F172A',
          card: '#1E293B',
          workspace: '#0A0F1D',
          border: '#334155',
        },
        pedagogy: {
          accent: '#38BDF8',
          success: '#22C55E',
          remediation: '#F97316',
          warning: '#FACC15',
          error: '#EF4444',
        },
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', 'Menlo', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
