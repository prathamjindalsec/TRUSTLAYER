/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#07111f',
          900: '#0b1728',
          850: '#101f33',
          800: '#14263d',
          700: '#1a314d',
        },
        trust: {
          50: '#effcf5',
          100: '#d8f8e6',
          200: '#b5efd0',
          300: '#82e1ae',
          400: '#48cb83',
          500: '#1fa968',
          600: '#148653',
          700: '#116b45',
          800: '#0d5437',
        },
        alert: {
          amber: '#b45309',
          red: '#c2413c',
        },
      },
      boxShadow: {
        panel: '0 16px 40px rgba(2, 12, 27, 0.08)',
        'panel-dark': '0 16px 40px rgba(0, 0, 0, 0.23)',
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
