import { useState } from 'react';
import { history, useRequest } from '@umijs/max';
import { Tabs, List, Tag, Select, InputNumber, Button, Space, message } from 'antd';
import { listWatchlist, changeWatchStatus, updateWatchProgress } from '@/services/watchlist';
import type { WatchStatus, WatchlistItemView } from '@/types/watchlist';

const TABS: { key: string; label: string; status?: WatchStatus }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'WANT_TO_WATCH', label: '想看', status: 'WANT_TO_WATCH' },
  { key: 'WATCHING', label: '在看', status: 'WATCHING' },
  { key: 'WATCHED', label: '看完', status: 'WATCHED' },
  { key: 'DROPPED', label: '弃番', status: 'DROPPED' },
];

const STATUS_OPTIONS: { label: string; value: WatchStatus }[] = [
  { label: '想看', value: 'WANT_TO_WATCH' },
  { label: '在看', value: 'WATCHING' },
  { label: '看完', value: 'WATCHED' },
  { label: '弃番', value: 'DROPPED' },
];

function WatchlistRow({ item, onChanged }: { item: WatchlistItemView; onChanged: () => void }) {
  const [progress, setProgress] = useState(item.currentEpisode);

  const handleStatusChange = async (status: WatchStatus) => {
    await changeWatchStatus(item.animeId, status);
    message.success('状态已更新');
    onChanged();
  };

  const handleProgressSave = async () => {
    await updateWatchProgress(item.animeId, progress);
    message.success('进度已更新');
    onChanged();
  };

  return (
    <List.Item
      actions={[
        <Select
          key="status"
          value={item.status}
          options={STATUS_OPTIONS}
          style={{ width: 100 }}
          onChange={handleStatusChange}
        />,
        <Space key="progress">
          <InputNumber min={0} value={progress} onChange={(v) => setProgress(v ?? 0)} />
          <Button onClick={handleProgressSave}>更新进度</Button>
        </Space>,
      ]}
    >
      <List.Item.Meta
        avatar={
          item.animeCoverUrl ? (
            <img src={item.animeCoverUrl} width={48} alt={item.animeTitleCn} />
          ) : undefined
        }
        title={<a onClick={() => history.push(`/anime/${item.animeId}`)}>{item.animeTitleCn}</a>}
        description={<Tag>{STATUS_OPTIONS.find((s) => s.value === item.status)?.label}</Tag>}
      />
    </List.Item>
  );
}

export default function WatchlistPage() {
  const [activeTab, setActiveTab] = useState('ALL');
  const activeStatus = TABS.find((t) => t.key === activeTab)?.status;

  const {
    data: items,
    loading,
    run: refetch,
  } = useRequest(() => listWatchlist(activeStatus), { refreshDeps: [activeStatus] });

  return (
    <div>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={TABS.map((t) => ({ key: t.key, label: t.label }))}
      />
      <List
        loading={loading}
        dataSource={items}
        renderItem={(item: WatchlistItemView) => (
          <WatchlistRow key={item.id} item={item} onChanged={refetch} />
        )}
      />
    </div>
  );
}
