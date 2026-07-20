import { Outlet, useModel, useLocation, history, Link } from '@umijs/max';
import { Layout, Menu, Button, Space, Typography } from 'antd';

const { Sider, Content, Header } = Layout;
const { Text } = Typography;

const MENU_ITEMS = [
  { key: '/anime/search', label: <Link to="/anime/search">番剧搜索</Link> },
  { key: '/watchlist', label: <Link to="/watchlist">我的追番</Link> },
  { key: '/reviews', label: <Link to="/reviews">我的评价</Link> },
  { key: '/rag/ingest', label: <Link to="/rag/ingest">RAG 采集</Link> },
  { key: '/rag/documents', label: <Link to="/rag/documents">已入库</Link> },
  { key: '/rag', label: <Link to="/rag">番剧问答</Link> },
];

export default function MainLayout() {
  const { initialState, setInitialState } = useModel('@@initialState');
  const location = useLocation();

  const selectedKey =
    MENU_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ?? '/anime/search';

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    setInitialState({ currentUser: undefined });
    history.push('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider>
        <div style={{ color: '#fff', textAlign: 'center', padding: 16, fontSize: 18 }}>
          anitrack
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={MENU_ITEMS} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
          <Space>
            <Text>{initialState?.currentUser?.nickname}</Text>
            <Button onClick={handleLogout}>退出登录</Button>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
