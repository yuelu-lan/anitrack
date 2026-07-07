export interface AnimeInfo {
  id: number;
  bangumiId: number;
  titleCn: string;
  titleOriginal: string;
  coverUrl: string | null;
  totalEpisodes: number | null;
  airDate: string | null;
  summary: string | null;
}
