import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type { AnimeInfo } from '@/types/anime';

export async function searchAnime(keyword: string) {
  const res = await request<ApiResult<AnimeInfo[]>>('/api/anime/search', {
    method: 'POST',
    data: { keyword },
  });
  return res.data;
}

export async function getAnimeDetail(animeId: number) {
  const res = await request<ApiResult<AnimeInfo>>('/api/anime/detail', {
    method: 'POST',
    data: { animeId },
  });
  return res.data;
}
