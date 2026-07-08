import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type {
  WatchlistItem,
  WatchlistItemDTO,
  WatchlistItemView,
  WatchlistItemViewDTO,
  WatchStatus,
} from '@/types/watchlist';

function toWatchlistItem(dto: WatchlistItemDTO): WatchlistItem {
  return { ...dto, status: dto.status.name as WatchStatus };
}

export async function addToWatchlist(animeId: number) {
  const res = await request<ApiResult<WatchlistItemDTO>>('/api/watchlist/add', {
    method: 'POST',
    data: { animeId },
  });
  return toWatchlistItem(res.data);
}

export async function changeWatchStatus(animeId: number, status: WatchStatus) {
  const res = await request<ApiResult<WatchlistItemDTO>>('/api/watchlist/change_status', {
    method: 'POST',
    data: { animeId, status },
  });
  return toWatchlistItem(res.data);
}

export async function updateWatchProgress(animeId: number, episode: number) {
  const res = await request<ApiResult<WatchlistItemDTO>>('/api/watchlist/update_progress', {
    method: 'POST',
    data: { animeId, episode },
  });
  return toWatchlistItem(res.data);
}

export async function getWatchlistDetail(animeId: number) {
  const res = await request<ApiResult<WatchlistItemDTO>>('/api/watchlist/detail', {
    method: 'POST',
    data: { animeId },
    skipErrorHandler: true,
  });
  return toWatchlistItem(res.data);
}

export async function listWatchlist(status?: WatchStatus) {
  const res = await request<ApiResult<WatchlistItemViewDTO[]>>('/api/watchlist/list', {
    method: 'POST',
    data: { status },
  });
  return res.data.map(toWatchlistItem) as WatchlistItemView[];
}
