import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type { WatchlistItem, WatchlistItemView, WatchStatus } from '@/types/watchlist';

export async function addToWatchlist(animeId: number) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/add', {
    method: 'POST',
    data: { animeId },
  });
  return res.data;
}

export async function changeWatchStatus(animeId: number, status: WatchStatus) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/change_status', {
    method: 'POST',
    data: { animeId, status },
  });
  return res.data;
}

export async function updateWatchProgress(animeId: number, episode: number) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/update_progress', {
    method: 'POST',
    data: { animeId, episode },
  });
  return res.data;
}

export async function getWatchlistDetail(animeId: number) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/detail', {
    method: 'POST',
    data: { animeId },
    skipErrorHandler: true,
  });
  return res.data;
}

export async function listWatchlist(status?: WatchStatus) {
  const res = await request<ApiResult<WatchlistItemView[]>>('/api/watchlist/list', {
    method: 'POST',
    data: { status },
  });
  return res.data;
}
