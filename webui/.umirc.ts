import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  model: {},
  initialState: {},
  request: {
    dataField: '',
  },
  npmClient: 'npm',
  routes: [
    { path: '/login', component: 'Login', layout: false },
    { path: '/register', component: 'Register', layout: false },
    { path: '/', redirect: '/anime/search' },
    { path: '/anime/search', component: 'AnimeSearch', wrappers: ['@/wrappers/auth'] },
    { path: '/anime/:animeId', component: 'AnimeDetail', wrappers: ['@/wrappers/auth'] },
    { path: '/watchlist', component: 'Watchlist', wrappers: ['@/wrappers/auth'] },
    { path: '/reviews', component: 'MyReviews', wrappers: ['@/wrappers/auth'] },
    { path: '/rag', component: '@/pages/rag/index', wrappers: ['@/wrappers/auth'] },
    { path: '/rag/ingest', component: '@/pages/rag/ingest', wrappers: ['@/wrappers/auth'] },
  ],
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
