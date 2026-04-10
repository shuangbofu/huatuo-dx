import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import logoIcon from '../assets/HuatuoDX_LOGO_ICON.png';
import logoTitle from '../assets/HuatuoDX_LOGO_TITLE.png';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);

  function resolveCallback() {
    const searchParams = new URLSearchParams(location.search);
    const callback = searchParams.get('callback');
    if (callback && callback.startsWith('/')) {
      return callback;
    }
    const stateRedirect = (location.state as { from?: string } | undefined)?.from;
    if (stateRedirect && stateRedirect.startsWith('/')) {
      return stateRedirect;
    }
    return undefined;
  }

  async function submit(values: { username: string; password: string }) {
    setSubmitting(true);
    try {
      const user = await login(values);
      const callback = resolveCallback();
      if (callback) {
        navigate(callback, { replace: true });
        return;
      }
      navigate(user.role === 'ADMIN' ? '/manager/nodes' : '/user/logs', { replace: true });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#1f3212_0,#35511d_38%,#eef1e6_38%,#eef1e6_100%)] px-6 py-10">
      <div className="mx-auto flex min-h-[calc(100vh-5rem)] max-w-5xl items-center justify-center">
        <Card className="w-full max-w-[460px] !border-[#d8deca] !bg-[#fbfcf6] shadow-panel">
          <div className="mb-8 text-center">
            <img src={logoIcon} alt="Huatuo DX 图标" className="mx-auto h-20 w-auto object-contain" />
            <img src={logoTitle} alt="Huatuo DX 标题" className="mx-auto mt-4 h-10 w-auto object-contain" />
            <Typography.Text className="mt-2 block text-[11px] tracking-[0.22em] !text-[#667455]">
              诊断控制台
            </Typography.Text>
            <Typography.Title level={2} className="!mb-0 !mt-4">
              登录
            </Typography.Title>
          </div>
          <Form layout="vertical" onFinish={submit} autoComplete="off">
            <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input size="large" prefix={<UserOutlined />} placeholder="请输入用户名" />
            </Form.Item>
            <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password size="large" prefix={<LockOutlined />} placeholder="请输入密码" />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" loading={submitting} className="!w-full">
              登录
            </Button>
          </Form>
        </Card>
      </div>
    </div>
  );
}
