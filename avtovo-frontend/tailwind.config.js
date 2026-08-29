/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                avtovo: {
                    bg: '#0f0f0f',
                    card: '#1a1a1a',
                    border: '#2a2a2a',
                    accent: '#3b82f6',
                    'accent-hover': '#2563eb',
                    muted: '#6b7280',
                    text: '#f5f5f5',
                    'text-secondary': '#a1a1aa',
                }
            },
            fontFamily: {
                sans: ['Inter', 'system-ui', 'sans-serif'],
            }
        },
    },
    plugins: [],
}