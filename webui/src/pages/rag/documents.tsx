import { useEffect, useState } from 'react';
import { Typography, Table, Input, Space } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { listDocuments, type RagDocumentSummary } from '@/services/rag';

export default function RagDocumentsPage() {
  const [data, setData] = useState<RagDocumentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');

  const fetchData = async () => {
    setLoading(true);
    try {
      setData(await listDocuments());
    } catch {
      // 全局 errorHandler 兜底 message.error
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const filtered = keyword
    ? data.filter((d) => d.title.toLowerCase().includes(keyword.toLowerCase()))
    : data;

  const columns: ColumnsType<RagDocumentSummary> = [
    { title: 'animeId', dataIndex: 'animeId', width: 120 },
    { title: '标题', dataIndex: 'title' },
    { title: '原文名', dataIndex: 'originalName' },
    { title: '放送日期', dataIndex: 'airDate', width: 120 },
    {
      title: '评分',
      key: 'score',
      render: (_, r) =>
        r.score != null ? `${r.score} (${r.ratingTotal ?? 0}人)` : '-',
    },
    { title: '集数', dataIndex: 'totalEpisodes', width: 80 },
  ];

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: 24 }}>
      <Typography.Title level={3}>已入库番剧</Typography.Title>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Input.Search
          placeholder="按标题搜索"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
        />
        <Table
          rowKey="animeId"
          columns={columns}
          dataSource={filtered}
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
          size="middle"
        />
      </Space>
    </div>
  );
}
