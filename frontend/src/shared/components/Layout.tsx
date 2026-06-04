import { useState }        from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import {
  Layout as AntLayout, Menu, Avatar, Dropdown,
  Typography, Space, Badge, Button, Tag,
} from 'antd'
import {
  DashboardOutlined, ShopOutlined, InboxOutlined,
  ScanOutlined, ReloadOutlined, BarChartOutlined,
  TeamOutlined, LogoutOutlined, BellOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined,
} from '@ant-design/icons'
import { useAppSelector, useAppDispatch } from '@/app/hooks'
import { selectCurrentUser } from '@/modules/auth/authSlice'
import { useLogoutMutationMutation } from '@/modules/auth/authApi'

const { Sider, Header, Content } = AntLayout
const { Text } = Typography

const navItems = [
  { key: '/',           label: 'Dashboard',  icon: <DashboardOutlined /> },
  { key: '/soh',        label: 'SOH',        icon: <ScanOutlined /> },
  { key: '/refill',     label: 'Refill',     icon: <ReloadOutlined /> },
  { key: '/inventory',  label: 'Inventory',  icon: <InboxOutlined /> },
  { key: '/reporting',  label: 'Reports',    icon: <BarChartOutlined /> },
  { key: '/admin',      label: 'Admin',      icon: <TeamOutlined />,
    children: [
      { key: '/admin/stores', label: 'Stores' },
      { key: '/admin/users',  label: 'Users' },
    ]
  },
]

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate  = useNavigate()
  const location  = useLocation()
  const user      = useAppSelector(selectCurrentUser)
  const [logoutMut] = useLogoutMutationMutation()

  const handleLogout = async () => {
    await logoutMut({})
    navigate('/login', { replace: true })
  }

  const userMenu = {
    items: [
      { key: 'logout', icon: <LogoutOutlined />, label: 'Sign out', danger: true },
    ],
    onClick: ({ key }: { key: string }) => { if (key === 'logout') handleLogout() },
  }

  const visibleItems = user?.role === 'ADMIN'
    ? navItems
    : navItems.filter(i => i.key !== '/admin')

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        trigger={null}
        width={220}
        style={{ background: '#001529' }}
      >
        <div style={{ padding: '16px', textAlign: 'center' }}>
          <Text strong style={{ color: '#fff', fontSize: collapsed ? 12 : 18 }}>
            {collapsed ? 'SL' : 'StoreLense'}
          </Text>
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['/admin']}
          items={visibleItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>

      <AntLayout>
        <Header style={{
          background: '#fff', padding: '0 24px',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          boxShadow: '0 1px 4px rgba(0,21,41,.08)',
        }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />

          <Space>
            {user?.storeId && <Tag color="blue">Store: {user.storeId.slice(-8)}</Tag>}
            <Tag color={user?.role === 'ADMIN' ? 'red' : 'green'}>{user?.role}</Tag>
            <Badge count={0} size="small">
              <Button type="text" icon={<BellOutlined />} />
            </Badge>
            <Dropdown menu={userMenu} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar style={{ backgroundColor: '#1677ff' }}>
                  {user?.username?.charAt(0).toUpperCase()}
                </Avatar>
                {!collapsed && <Text>{user?.username}</Text>}
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ margin: '24px', background: '#f5f5f5', minHeight: 280 }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  )
}
