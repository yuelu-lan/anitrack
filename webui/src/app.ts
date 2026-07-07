import { history } from '@umijs/max';
import type { RequestConfig } from '@umijs/max';
import { message } from 'antd';
import type { UserInfo } from '@/types/user';

export async function getInitialState(): Promise<{ currentUser?: UserInfo }> {
  const raw = localStorage.getItem('userInfo');
  if (!raw) return {};
  try {
    return { currentUser: JSON.parse(raw) as UserInfo };
  } catch {
    return {};
  }
}

interface RawApiResponse {
  status: number;
  message: string | null;
  data: unknown;
}

export const request: RequestConfig = {
  timeout: 10000,
  errorConfig: {
    errorHandler: (error: any, opts: any) => {
      if (opts?.skipErrorHandler) {
        throw error;
      }
      if (error.name === 'BizError') {
        message.error(error.message);
        return;
      }
      if (error.response) {
        if (error.response.status === 401) {
          localStorage.removeItem('token');
          localStorage.removeItem('userInfo');
          history.push('/login');
          return;
        }
        message.error(`请求失败（HTTP ${error.response.status}）`);
        return;
      }
      message.error('网络异常，请稍后重试');
    },
  },
  requestInterceptors: [
    (config: any) => {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers = { ...(config.headers ?? {}), Authorization: `Bearer ${token}` };
      }
      return config;
    },
  ],
  responseInterceptors: [
    (response: any) => {
      const body = response.data as RawApiResponse | undefined;
      if (body && body.status === 0) {
        const error: any = new Error(body.message ?? '请求失败');
        error.name = 'BizError';
        throw error;
      }
      return response;
    },
  ],
};
