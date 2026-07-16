import { useState } from 'react';
import { Typography, InputNumber, Button, Alert, Space, message } from 'antd';
import { ingestByCriteria } from '@/services/rag';

export default function RagIngestPage() {
  const [year, setYear] = useState(2026);
  const [minRating, setMinRating] = useState(7.0);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<number | null>(null);

  const handleIngest = async () => {
    setLoading(true);
    setResult(null);
    try {
      const n = await ingestByCriteria(year, minRating);
      setResult(n);
      message.success(`采集完成，入库 ${n} 部`);
    } catch {
      // 全局 errorHandler 兜底 message.error
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 600, margin: '0 auto', padding: 24 }}>
      <Typography.Title level={3}>RAG 知识库采集</Typography.Title>
      <Alert
        type="info"
        showIcon
        message="采集会调用 Bangumi API 拉取年度动画并 embedding 入库，约 40 秒，请勿重复点击。"
        style={{ marginBottom: 16 }}
      />
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div>
          <Typography.Text>年份</Typography.Text>
          <InputNumber
            min={2000}
            step={1}
            value={year}
            onChange={(v) => setYear(v ?? 2026)}
            style={{ width: '100%', marginTop: 4 }}
          />
        </div>
        <div>
          <Typography.Text>最低评分</Typography.Text>
          <InputNumber
            min={0}
            max={10}
            step={0.1}
            value={minRating}
            onChange={(v) => setMinRating(v ?? 7.0)}
            style={{ width: '100%', marginTop: 4 }}
          />
        </div>
        <Button type="primary" loading={loading} onClick={handleIngest}>
          开始采集
        </Button>
        {result !== null && (
          <Alert type="success" showIcon message={`采集完成，入库 ${result} 部番剧百科`} />
        )}
      </Space>
    </div>
  );
}
