import { useChat } from '@ai-sdk/react';
import { TextStreamChatTransport } from 'ai';
import { Input, Button, Typography, Spin, Alert } from 'antd';
import { useMemo, useState } from 'react';

const { Text } = Typography;

export default function RagPage() {
  const [input, setInput] = useState('');

  const transport = useMemo(
    () =>
      new TextStreamChatTransport({
        api: '/api/rag/chat',
        headers: () => ({
          Authorization: `Bearer ${localStorage.getItem('token') ?? ''}`,
        }),
        prepareSendMessagesRequest: ({ messages }) => {
          const last = messages[messages.length - 1];
          const text =
            last?.parts
              ?.filter((p): p is { type: 'text'; text: string } => p.type === 'text')
              .map((p) => p.text)
              .join('') ?? '';
          return { body: { message: text } };
        },
      }),
    [],
  );

  const { messages, sendMessage, status, error } = useChat({ transport });

  const onSubmit = () => {
    if (!input.trim()) return;
    sendMessage({ text: input });
    setInput('');
  };

  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: 24 }}>
      <Typography.Title level={3}>番剧百科问答</Typography.Title>
      <div style={{ minHeight: 300, marginBottom: 16 }}>
        {messages.map((m) => (
          <div key={m.id} style={{ marginBottom: 8 }}>
            <Text strong>{m.role === 'user' ? '我' : '助手'}：</Text>
            <Text>{m.parts.filter((p) => p.type === 'text').map((p) => p.text).join('')}</Text>
          </div>
        ))}
        {status === 'streaming' && <Spin />}
        {status === 'error' && (
          <Alert
            type="error"
            message="回答生成失败"
            description={error?.message ?? '请稍后重试'}
            style={{ marginTop: 8 }}
          />
        )}
      </div>
      <Input.TextArea
        rows={2}
        value={input}
        onChange={(e) => setInput(e.target.value)}
        placeholder="问点关于番剧的……"
      />
      <Button
        type="primary"
        style={{ marginTop: 8 }}
        onClick={onSubmit}
        loading={status === 'submitted'}
      >
        发送
      </Button>
    </div>
  );
}
