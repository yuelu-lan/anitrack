import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';

export async function ingestByCriteria(year: number, minRating: number) {
  const res = await request<ApiResult<number>>('/api/rag/ingest_by_criteria', {
    method: 'POST',
    params: { year, minRating },
    timeout: 120000,
  });
  return res.data;
}
