import { useEffect } from 'react';
import { Modal, Form, Rate, Input, message } from 'antd';
import { addReview, updateReview } from '@/services/review';
import type { Review } from '@/types/review';

interface ReviewFormValues {
  score: number;
  content?: string;
}

interface ReviewFormModalProps {
  open: boolean;
  animeId: number;
  initialValues?: { score: number; content: string | null };
  onCancel: () => void;
  onSuccess: (review: Review) => void;
}

export default function ReviewFormModal({
  open,
  animeId,
  initialValues,
  onCancel,
  onSuccess,
}: ReviewFormModalProps) {
  const [form] = Form.useForm<ReviewFormValues>();

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        score: initialValues?.score ?? 5,
        content: initialValues?.content ?? '',
      });
    }
  }, [open, initialValues, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const review = initialValues
      ? await updateReview(animeId, values.score, values.content)
      : await addReview(animeId, values.score, values.content);
    message.success('评价已保存');
    onSuccess(review);
  };

  return (
    <Modal
      title={initialValues ? '编辑评价' : '写评价'}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      destroyOnClose
    >
      <Form<ReviewFormValues> form={form} layout="vertical">
        <Form.Item name="score" label="评分" rules={[{ required: true, message: '请打分' }]}>
          <Rate count={10} />
        </Form.Item>
        <Form.Item name="content" label="评论内容">
          <Input.TextArea rows={4} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
