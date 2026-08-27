/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        // Navbar y fondos oscuros estilo Riwi
        brand: {
          dark: '#1e1b4b',    // Índigo/Azul muy oscuro (Navbar superior)
          500: '#6366f1',     // Azul/Morado vibrante
          600: '#4f46e5',     // Morado principal de marca
          700: '#4338ca',     // Hover de botones/pestañas
        },
        // Verde aguamarina brillante para badges, resaltados y hovers
        accent: {
          300: '#6ee7b7',
          400: '#34d399',     // Verde brillante de destacado
          500: '#10b981',     // Aguamarina base
        },
        // Modo oscuro / claro
        dark: {
          bg: '#0f172a',      // Fondo general nocturno
          surface: '#1e293b', // Tarjetas modo noche
          border: '#334155'
        },
        light: {
          bg: '#f8fafc',      // Fondo gris/blanco sutil
          surface: '#ffffff', // Tarjetas blancas
          border: '#e2e8f0'
        }
      }
    },
  },
  plugins: [],
}