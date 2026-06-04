import { useEffect }                  from 'react'
import { useNavigate }                from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useLoginMutation }           from '../authApi'
import { useAppSelector }             from '@/app/hooks'
import { selectIsAuthed }             from '../authSlice'

const { Title, Text } = Typography

export default function LoginPage() {
  const navigate    = useNavigate()
  const isAuthed    = useAppSelector(selectIsAuthed)
  const [login, { isLoading, error }] = useLoginMutation()

  useEffect(() => { if (isAuthed) navigate('/', { replace: true }) }, [isAuthed, navigate])

  const onFinish = async (values: { username: string; password: string }) => {
    await login(values)
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex',
      alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
    }}>
      <Card style={{ width: 400, borderRadius: 12, boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <Title level={2} style={{ margin: 0 }}>StoreLense</Title>
            <Text type="secondary">RFID Store Operations Platform</Text>
          </div>

          {error && (
            <Alert
              type="error"
              message="Login failed"
              description="Invalid username or password."
              showIcon
            />
          )}

          <Form layout="vertical" onFinish={onFinish} autoComplete="off">
            <Form.Item
              name="username"
              rules={[{ required: true, message: 'Username is required' }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="Username"
                size="large"
                autoFocus
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: 'Password is required' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Password"
                size="large"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                size="large"
                loading={isLoading}
                block
              >
                Sign In
              </Button>
            </Form.Item>
          </Form>
        </Space>
      </Card>
    </div>
  )
}
