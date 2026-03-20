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
                    accent: '#e85d26',
                    'accent-hover': '#d14e1a',
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