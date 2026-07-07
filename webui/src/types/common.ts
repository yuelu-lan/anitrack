export interface ApiResult<T> {
  status: number;
  message: string | null;
  data: T;
}

export interface PageResult<T> {
  pageNum: number;
  pageSize: number;
  total: number;
  list: T[];
}
