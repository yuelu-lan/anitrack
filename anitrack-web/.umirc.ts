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
    { path: '/login', component: 'Login' },
    { path: '/register', component: 'Register' },
    {
      path: '/',
      component: '@/layouts/index',
      wrappers: ['@/wrappers/auth'],
      routes: [
        { path: '/', redirect: '/anime/search' },
        { path: '/anime/search', component: 'AnimeSearch' },
        { path: '/anime/:animeId', component: 'AnimeDetail' },
        { path: '/watchlist', component: 'Watchlist' },
        { path: '/reviews', component: 'MyReviews' },
      ],
    },
  ],
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
