import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#102133',
        mist: '#eef4f7',
        ember: '#ff7a59',
        steel: '#4e697d',
      },
      boxShadow: {
        panel: '0 24px 60px rgba(16, 33, 51, 0.12)',
      },
      backgroundImage: {
        grid: 'linear-gradient(rgba(255,255,255,0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.08) 1px, transparent 1px)',
      },
    },
  },
  plugins: [],
} satisfies Config;
