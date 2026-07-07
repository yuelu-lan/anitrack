export interface Review {
  id: number;
  animeId: number;
  score: number;
  content: string | null;
  createTime: string;
}

export interface ReviewWithUser {
  id: number;
  userId: number;
  userNickname: string;
  userAvatarUrl: string | null;
  score: number;
  content: string | null;
  createTime: string;
}

export interface ReviewWithAnime {
  id: number;
  animeId: number;
  animeTitleCn: string;
  animeTitleOriginal: string;
  animeCoverUrl: string | null;
  score: number;
  content: string | null;
  createTime: string;
}
