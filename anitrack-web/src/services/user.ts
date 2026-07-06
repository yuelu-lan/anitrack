import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type { UserInfo, LoginResult, RegisterParams, LoginParams } from '@/types/user';

export async function register(params: RegisterParams) {
  const res = await request<ApiResult<UserInfo>>('/api/user/register', {
    method: 'POST',
    data: params,
  });
  return res.data;
}

export async function login(params: LoginParams) {
  const res = await request<ApiResult<LoginResult>>('/api/user/login', {
    method: 'POST',
    data: params,
  });
  return res.data;
}
