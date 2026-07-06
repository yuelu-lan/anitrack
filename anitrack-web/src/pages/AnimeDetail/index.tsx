import { useEffect, useState } from 'react';
import { useParams, useRequest } from '@umijs/max';
import {
  Card,
  Descriptions,
  Image,
  Tag,
  Select,
  InputNumber,
  Button,
  Table,
  Space,
  Typography,
  message,
} from 'antd';
import { getAnimeDetail } from '@/services/anime';
import {
  addToWatchlist,
  changeWatchStatus,
  updateWatchProgress,
  getWatchlistDetail,
} from '@/services/watchlist';
import { getMyReviewDetail, listReviewsByAnime } from '@/services/review';
import type { WatchStatus } from '@/types/watchlist';
import type { ReviewWithUser } from '@/types/review';
import ReviewFormModal from '@/components/ReviewFormModal';

const { Title, Paragraph } = Typography;

const STATUS_OPTIONS: { label: string; value: WatchStatus }[] = [
  { label: '想看', value: 'WANT_TO_WATCH' },
  { label: '在看', value: 'WATCHING' },
  { label: '看完', value: 'WATCHED' },
  { label: '弃番', value: 'DROPPED' },
];

const STATUS_LABEL: Record<WatchStatus, string> = {
  WANT_TO_WATCH: '想看',
  WATCHING: '在看',
  WATCHED: '看完',
  DROPPED: '弃番',
};

const REVIEW_COLUMNS = [
  { title: '用户', dataIndex: 'userNickname', key: 'userNickname' },
  { title: '评分', dataIndex: 'score', key: 'score' },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: '时间', dataIndex: 'createTime', key: 'createTime' },
];

export default function AnimeDetailPage() {
  const { animeId: animeIdParam } = useParams<{ animeId: string }>();
  const animeId = Number(animeIdParam);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [progress, setProgress] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const { data: anime, loading: animeLoading } = useRequest(() => getAnimeDetail(animeId), {
    refreshDeps: [animeId],
  });

  const {
    data: watchlistItem,
    loading: watchlistLoading,
    run: refetchWatchlist,
  } = useRequest(() => getWatchlistDetail(animeId).catch(() => undefined), {
    refreshDeps: [animeId],
  });

  const { data: myReview, run: refetchMyReview } = useRequest(
    () => getMyReviewDetail(animeId).catch(() => undefined),
    { refreshDeps: [animeId] },
  );

  const {
    data: reviewPage,
    loading: reviewLoading,
    run: refetchReviews,
  } = useRequest(() => listReviewsByAnime(animeId, page, pageSize), {
    refreshDeps: [animeId, page],
  });

  useEffect(() => {
    if (watchlistItem) {
      setProgress(watchlistItem.currentEpisode);
    }
  }, [watchlistItem]);

  const handleAdd = async () => {
    await addToWatchlist(animeId);
    message.success('已加入追番');
    refetchWatchlist();
  };

  const handleStatusChange = async (status: WatchStatus) => {
    await changeWatchStatus(animeId, status);
    message.success('状态已更新');
    refetchWatchlist();
  };

  const handleProgressSave = async () => {
    await updateWatchProgress(animeId, progress);
    message.success('进度已更新');
    refetchWatchlist();
  };

  return (
    <div>
      <Card loading={animeLoading}>
        {anime && (
          <Space align="start" size={24}>
            {anime.coverUrl && <Image src={anime.coverUrl} width={200} alt={anime.titleCn} />}
            <div>
              <Title level={3}>{anime.titleCn}</Title>
              <Paragraph type="secondary">{anime.titleOriginal}</Paragraph>
              <Descriptions column={1}>
                <Descriptions.Item label="集数">{anime.totalEpisodes ?? '未知'}</Descriptions.Item>
                <Descriptions.Item label="放送日期">{anime.airDate ?? '未知'}</Descriptions.Item>
              </Descriptions>
              <Paragraph>{anime.summary}</Paragraph>
            </div>
          </Space>
        )}
      </Card>

      <Card title="追番状态" loading={watchlistLoading} style={{ marginTop: 16 }}>
        {watchlistItem ? (
          <Space>
            <Tag color="blue">{STATUS_LABEL[watchlistItem.status]}</Tag>
            <Select
              value={watchlistItem.status}
              options={STATUS_OPTIONS}
              style={{ width: 120 }}
              onChange={handleStatusChange}
            />
            <span>第</span>
            <InputNumber min={0} value={progress} onChange={(v) => setProgress(v ?? 0)} />
            <span>话</span>
            <Button onClick={handleProgressSave}>更新进度</Button>
            {watchlistItem.status === 'WATCHED' && (
              <Button type="primary" onClick={() => setReviewModalOpen(true)}>
                {myReview ? '编辑评价' : '写评价'}
              </Button>
            )}
          </Space>
        ) : (
          <Button type="primary" onClick={handleAdd}>
            加入追番
          </Button>
        )}
      </Card>

      <Card title="评价列表" style={{ marginTop: 16 }}>
        <Table<ReviewWithUser>
          rowKey="id"
          loading={reviewLoading}
          columns={REVIEW_COLUMNS}
          dataSource={reviewPage?.list}
          pagination={{
            current: page,
            pageSize,
            total: reviewPage?.total,
            onChange: setPage,
          }}
        />
      </Card>

      <ReviewFormModal
        open={reviewModalOpen}
        animeId={animeId}
        initialValues={myReview ? { score: myReview.score, content: myReview.content } : undefined}
        onCancel={() => setReviewModalOpen(false)}
        onSuccess={() => {
          setReviewModalOpen(false);
          refetchMyReview();
          refetchReviews();
        }}
      />
    </div>
  );
}
