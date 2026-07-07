import { history, useModel } from '@umijs/max';
import { Button, Card, Form, Input, message } from 'antd';
import { login } from '@/services/user';
import type { LoginParams } from '@/types/user';

export default function LoginPage() {
  const { setInitialState } = useModel('@@initialState');

  const onFinish = async (values: LoginParams) => {
    const result = await login(values);
    localStorage.setItem('token', result.token);
    localStorage.setItem('userInfo', JSON.stringify(result.userInfo));
    setInitialState({ currentUser: result.userInfo });
    message.success('登录成功');
    history.push('/anime/search');
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: '#f0f2f5',
      }}
    >
      <Card title="登录 anitrack" style={{ width: 360 }}>
        <Form<LoginParams> onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              登录
            </Button>
          </Form.Item>
          <Button type="link" block onClick={() => history.push('/register')}>
            还没有账号？去注册
          </Button>
        </Form>
      </Card>
    </div>
  );
}
