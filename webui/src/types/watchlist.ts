export type WatchStatus = 'WANT_TO_WATCH' | 'WATCHING' | 'WATCHED' | 'DROPPED';

export interface WatchlistItem {
  id: number;
  animeId: number;
  status: WatchStatus;
  currentEpisode: number;
  updateTime: string;
}

export interface WatchlistItemView extends WatchlistItem {
  animeTitleCn: string;
  animeTitleOriginal: string;
  animeCoverUrl: string | null;
}
