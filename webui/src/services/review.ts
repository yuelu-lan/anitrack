import { request } from '@umijs/max';
import type { ApiResult, PageResult } from '@/types/common';
import type { Review, ReviewWithUser, ReviewWithAnime } from '@/types/review';

export async function addReview(animeId: number, score: number, content?: string) {
  const res = await request<ApiResult<Review>>('/api/review/add', {
    method: 'POST',
    data: { animeId, score, content },
  });
  return res.data;
}

export async function updateReview(animeId: number, score: number, content?: string) {
  const res = await request<ApiResult<Review>>('/api/review/update', {
    method: 'POST',
    data: { animeId, score, content },
  });
  return res.data;
}

export async function getMyReviewDetail(animeId: number) {
  const res = await request<ApiResult<Review>>('/api/review/detail', {
    method: 'POST',
    data: { animeId },
    skipErrorHandler: true,
  });
  return res.data;
}

export async function listReviewsByAnime(animeId: number, page: number, pageSize: number) {
  const res = await request<ApiResult<PageResult<ReviewWithUser>>>('/api/review/list_by_anime', {
    method: 'POST',
    data: { animeId, page, pageSize },
  });
  return res.data;
}

export async function listMyReviews() {
  const res = await request<ApiResult<ReviewWithAnime[]>>('/api/review/my_list', {
    method: 'POST',
  });
  return res.data;
}
