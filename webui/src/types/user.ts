export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  avatarUrl: string | null;
}

export interface LoginResult {
  token: string;
  userInfo: UserInfo;
}

export interface RegisterParams {
  username: string;
  password: string;
  nickname: string;
}

export interface LoginParams {
  username: string;
  password: string;
}
