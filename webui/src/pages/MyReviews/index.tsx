import { useState } from 'react';
import { history, useRequest } from '@umijs/max';
import { List, Rate, Typography, Button, Space } from 'antd';
import { listMyReviews } from '@/services/review';
import ReviewFormModal from '@/components/ReviewFormModal';
import type { ReviewWithAnime } from '@/types/review';
import { formatDateTime } from '@/utils/format';

const { Paragraph, Text } = Typography;

export default function MyReviewsPage() {
  const [editing, setEditing] = useState<ReviewWithAnime | null>(null);

  const { data: reviews, loading, run: refetch } = useRequest(() => listMyReviews());

  return (
    <div>
      <List
        loading={loading}
        dataSource={reviews}
        renderItem={(review: ReviewWithAnime) => (
          <List.Item actions={[<Button key="edit" onClick={() => setEditing(review)}>编辑</Button>]}>
            <List.Item.Meta
              avatar={
                review.animeCoverUrl ? (
                  <img src={review.animeCoverUrl} width={48} alt={review.animeTitleCn} />
                ) : undefined
              }
              title={
                <a onClick={() => history.push(`/anime/${review.animeId}`)}>{review.animeTitleCn}</a>
              }
              description={
                <Space direction="vertical">
                  <Rate disabled count={10} value={review.score} />
                  <Paragraph>{review.content}</Paragraph>
                  <Text type="secondary">{formatDateTime(review.createTime)}</Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />

      {editing && (
        <ReviewFormModal
          open
          animeId={editing.animeId}
          initialValues={{ score: editing.score, content: editing.content }}
          onCancel={() => setEditing(null)}
          onSuccess={() => {
            setEditing(null);
            refetch();
          }}
        />
      )}
    </div>
  );
}
