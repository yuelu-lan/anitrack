import { useState } from 'react';
import { history } from '@umijs/max';
import { Input, Card, Row, Col, Empty, Spin } from 'antd';
import { searchAnime } from '@/services/anime';
import type { AnimeInfo } from '@/types/anime';

const { Search } = Input;
const { Meta } = Card;

export default function AnimeSearchPage() {
  const [results, setResults] = useState<AnimeInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async (keyword: string) => {
    if (!keyword.trim()) return;
    setLoading(true);
    try {
      const data = await searchAnime(keyword.trim());
      setResults(data);
    } finally {
      setLoading(false);
      setSearched(true);
    }
  };

  return (
    <div>
      <Search
        placeholder="搜索番剧标题"
        onSearch={handleSearch}
        enterButton
        style={{ maxWidth: 480, marginBottom: 24 }}
      />
      <Spin spinning={loading}>
        {searched && results.length === 0 ? (
          <Empty description="没有找到相关番剧" />
        ) : (
          <Row gutter={[16, 16]}>
            {results.map((anime) => (
              <Col key={anime.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  hoverable
                  cover={
                    anime.coverUrl ? (
                      <img
                        src={anime.coverUrl}
                        alt={anime.titleCn}
                        style={{ height: 280, objectFit: 'cover' }}
                      />
                    ) : undefined
                  }
                  onClick={() => history.push(`/anime/${anime.id}`)}
                >
                  <Meta title={anime.titleCn} description={`共 ${anime.totalEpisodes ?? '?'} 话`} />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Spin>
    </div>
  );
}
