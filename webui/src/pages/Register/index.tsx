import { history } from '@umijs/max';
import { Button, Card, Form, Input, message } from 'antd';
import { register } from '@/services/user';
import type { RegisterParams } from '@/types/user';

export default function RegisterPage() {
  const onFinish = async (values: RegisterParams) => {
    try {
      await register(values);
      message.success('注册成功，请登录');
      history.push('/login');
    } catch {
      return;
    }
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
      <Card title="注册 anitrack" style={{ width: 360 }}>
        <Form<RegisterParams> onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nickname" label="昵称" rules={[{ required: true, message: '请输入昵称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              注册
            </Button>
          </Form.Item>
          <Button type="link" block onClick={() => history.push('/login')}>
            已有账号？去登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
