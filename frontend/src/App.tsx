import {
  ApartmentOutlined,
  ControlOutlined,
  FileSearchOutlined,
  LogoutOutlined,
  SafetyOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, ConfigProvider, Dropdown, Layout, Menu, Space, Spin, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import logoIcon from './assets/HuatuoDX_LOGO_ICON.png';
import logoTitle from './assets/HuatuoDX_LOGO_TITLE.png';
import type { AgentNode } from './lib/api';
import { api } from './lib/api';
import { DiagnosticRecordDetailPage } from './pages/DiagnosticRecordDetailPage';
import { DiagnosticRecordsPage } from './pages/DiagnosticRecordsPage';
import { LoginPage } from './pages/LoginPage';
import { DiagnosticRuleDetailPage } from './pages/DiagnosticRuleDetailPage';
import { DiagnosticRulesPage } from './pages/DiagnosticRulesPage';
import { ManagerLogCollectionPage } from './pages/ManagerLogCollectionPage';
import { ManagerNodesPage } from './pages/ManagerNodesPage';
import { ManagerUsersPage } from './pages/ManagerUsersPage';
import { UserLogsPage } from './pages/UserLogsPage';

type PortalMode = 'user' | 'manager';
type PageKey = 'user-logs' | 'user-diagnose' | 'user-records' | 'manager-nodes' | 'manager-rules' | 'manager-users' | 'manager-records';
type MenuItem = { key: PageKey; icon: JSX.Element; label: string; path: string };

const menuMap: Record<PortalMode, MenuItem[]> = {
  user: [
    { key: 'user-logs', icon: <FileSearchOutlined />, label: '日志查询', path: '/user/logs' },
    { key: 'user-diagnose', icon: <SafetyOutlined />, label: '诊断规则', path: '/user/diagnostics' },
    { key: 'user-records', icon: <ControlOutlined />, label: '诊断记录', path: '/user/records' },
  ],
  manager: [
    { key: 'manager-nodes', icon: <ApartmentOutlined />, label: '节点管理', path: '/manager/nodes' },
    { key: 'manager-rules', icon: <ControlOutlined />, label: '诊断规则', path: '/manager/rules' },
    { key: 'manager-records', icon: <SafetyOutlined />, label: '诊断记录', path: '/manager/records' },
    { key: 'manager-users', icon: <TeamOutlined />, label: '用户管理', path: '/manager/users' },
  ],
};

const NODE_POLL_INTERVAL_MS = 10_000;

function resolveRoute(pathname: string): { portal: PortalMode; page: PageKey } {
  if (pathname.startsWith('/manager/nodes/') && pathname.endsWith('/logs')) {
    return { portal: 'manager', page: 'manager-nodes' };
  }
  if (pathname.startsWith('/manager/rules/')) {
    return { portal: 'manager', page: 'manager-rules' };
  }
  if (pathname.startsWith('/manager/users')) {
    return { portal: 'manager', page: 'manager-users' };
  }
  if (pathname.startsWith('/manager/records/')) {
    return { portal: 'manager', page: 'manager-records' };
  }
  if (pathname.startsWith('/user/diagnostics/')) {
    return { portal: 'user', page: 'user-diagnose' };
  }
  if (pathname.startsWith('/user/records/')) {
    return { portal: 'user', page: 'user-records' };
  }
  const matchedUser = menuMap.user.find((item) => item.path === pathname);
  if (matchedUser) {
    return { portal: 'user', page: matchedUser.key };
  }
  const matchedManager = menuMap.manager.find((item) => item.path === pathname);
  if (matchedManager) {
    return { portal: 'manager', page: matchedManager.key };
  }
  return { portal: 'user', page: 'user-logs' };
}

function RedirectByRole() {
  const { user, isAdmin } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={isAdmin ? '/manager/nodes' : '/user/logs'} replace />;
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }
  if (!user) {
    const callback = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?callback=${encodeURIComponent(callback)}`} replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const { user, loading, isAdmin } = useAuth();
  if (loading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (!isAdmin) {
    return <Navigate to="/user/logs" replace />;
  }
  return children;
}

function AppShell() {
  const { user, loading: authLoading, isAdmin, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const routeState = useMemo(() => resolveRoute(location.pathname), [location.pathname]);
  const [nodes, setNodes] = useState<AgentNode[]>([]);
  const [loadingNodes, setLoadingNodes] = useState(true);
  const [messageApi, holder] = message.useMessage();

  const loadNodes = async (silent = false) => {
    if (!user) {
      setNodes([]);
      setLoadingNodes(false);
      return;
    }
    if (!silent) {
      setLoadingNodes(true);
    }
    try {
      setNodes(await api.listNodes());
    } catch {
      setNodes([]);
    } finally {
      if (!silent) {
        setLoadingNodes(false);
      }
    }
  };

  const refresh = async (silent = false) => loadNodes(silent);

  useEffect(() => {
    if (!user) {
      setNodes([]);
      setLoadingNodes(false);
      return;
    }
    void refresh();
  }, [user?.id]);

  useEffect(() => {
    if (!user || routeState.portal !== 'manager' || routeState.page !== 'manager-nodes') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadNodes(true);
    }, NODE_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [user?.id, routeState.portal, routeState.page]);

  const currentPortal = isAdmin ? routeState.portal : 'user';
  const currentMenuItems = menuMap[currentPortal];

  async function switchPortal(nextPortal: PortalMode) {
    if (!isAdmin) {
      return;
    }
    const firstItem = menuMap[nextPortal][0];
    navigate(firstItem.path);
  }

  async function submitLogout() {
    await logout();
    messageApi.success('已退出登录');
    navigate('/login', { replace: true });
  }

  if (authLoading) {
    return (
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: '#95b83d',
            colorInfo: '#95b83d',
            colorLink: '#577326',
            borderRadius: 4,
          },
        }}
      >
        <div className="flex min-h-screen items-center justify-center">
          <Spin size="large" />
        </div>
      </ConfigProvider>
    );
  }

  if (!user) {
    return (
      <>
        {holder}
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </>
    );
  }

  return (
    <>
      {holder}
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: '#95b83d',
            colorInfo: '#95b83d',
            colorLink: '#577326',
            borderRadius: 4,
          },
          components: {
            Button: {
              colorPrimary: '#6f8f2a',
              colorPrimaryHover: '#84a935',
              colorPrimaryActive: '#577326',
            },
            Select: {
              optionSelectedBg: '#f0f6df',
              optionActiveBg: '#f7f9ee',
            },
            Switch: {
              colorPrimary: '#6f8f2a',
              colorPrimaryHover: '#84a935',
            },
            Pagination: {
              colorPrimary: '#6f8f2a',
              colorPrimaryHover: '#84a935',
              colorPrimaryBorder: '#6f8f2a',
            },
            Input: {
              activeBorderColor: '#6f8f2a',
              hoverBorderColor: '#84a935',
            },
            Radio: {
              buttonSolidCheckedBg: '#6f8f2a',
              buttonSolidCheckedHoverBg: '#84a935',
              buttonSolidCheckedActiveBg: '#577326',
              buttonSolidCheckedColor: '#fcfcf6',
              buttonBg: '#fbfcf6',
              buttonCheckedBg: '#f0f6df',
              buttonColor: '#455036',
            },
            Table: {
              headerBg: '#f2f4e8',
              headerColor: '#455036',
              rowHoverBg: '#f8faef',
            },
          },
        }}
      >
        <Layout className="h-screen overflow-hidden bg-transparent">
          <header className="dx-topbar px-4">
            <div className="grid w-full grid-cols-1 gap-2 xl:grid-cols-[220px_minmax(0,1fr)_320px] xl:items-center">
              <div className="min-w-0">
                <div className="inline-flex items-center gap-3 px-2 py-1">
                  <img src={logoIcon} alt="Huatuo DX 图标" className="h-12 w-auto object-contain" />
                  <div className="flex min-w-0 flex-col items-center justify-center gap-0.5">
                    <img src={logoTitle} alt="Huatuo DX 标题" className="h-8 w-auto max-w-[180px] object-contain" />
                    <div className="text-center text-[11px] leading-none tracking-[0.18em] text-[#dfe8c7]">
                      诊断控制台
                    </div>
                  </div>
                </div>
              </div>
              <div className="min-w-0">
                <Menu
                  mode="horizontal"
                  selectedKeys={[routeState.page]}
                  items={currentMenuItems}
                  onClick={(event) => {
                    const target = currentMenuItems.find((item) => item.key === event.key);
                    if (target) {
                      navigate(target.path);
                    }
                  }}
                  theme="dark"
                  style={{ border: 'none', background: 'transparent', justifyContent: 'center' as never, color: '#d7e2eb' }}
                />
              </div>
              <div className="flex flex-nowrap items-center justify-end gap-3">
                <Space size={10} className="shrink-0">
                  <a
                    href="https://github.com/shuangbofu/huatuo-dx"
                    target="_blank"
                    rel="noreferrer"
                    className="dx-header-action dx-github-link"
                    aria-label="GitHub 仓库"
                    title="GitHub 仓库"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true" className="h-4 w-4 fill-current">
                      <path d="M12 2C6.48 2 2 6.58 2 12.24c0 4.53 2.87 8.37 6.84 9.73.5.09.66-.22.66-.49 0-.24-.01-1.03-.01-1.87-2.78.62-3.37-1.21-3.37-1.21-.45-1.19-1.11-1.5-1.11-1.5-.91-.64.07-.63.07-.63 1 .08 1.53 1.06 1.53 1.06.9 1.58 2.35 1.12 2.92.86.09-.67.35-1.12.63-1.38-2.22-.26-4.56-1.15-4.56-5.14 0-1.14.39-2.07 1.03-2.8-.1-.26-.45-1.32.1-2.75 0 0 .84-.28 2.75 1.07A9.18 9.18 0 0 1 12 6.84c.85 0 1.7.12 2.5.35 1.9-1.35 2.74-1.07 2.74-1.07.55 1.43.2 2.49.1 2.75.64.73 1.03 1.66 1.03 2.8 0 4-2.34 4.87-4.57 5.13.36.32.68.94.68 1.9 0 1.37-.01 2.47-.01 2.8 0 .27.17.59.67.49A10.27 10.27 0 0 0 22 12.24C22 6.58 17.52 2 12 2Z" />
                    </svg>
                  </a>
                  {isAdmin ? (
                    <Button className="dx-header-action" onClick={() => void switchPortal(currentPortal === 'manager' ? 'user' : 'manager')}>
                      {currentPortal === 'manager' ? '用户端' : '管理端'}
                    </Button>
                  ) : null}
                  <Dropdown
                    overlayClassName="dx-user-dropdown"
                    trigger={['click']}
                    menu={{
                      items: [
                        {
                          key: 'profile',
                          label: (
                            <div className="flex flex-col leading-tight">
                              <span className="font-medium">{user.displayName || user.username}</span>
                              <span className="text-xs text-slate-500">@{user.username}</span>
                            </div>
                          ),
                          disabled: true,
                        },
                        {
                          key: 'logout',
                          icon: <LogoutOutlined />,
                          label: '退出登录',
                          onClick: () => void submitLogout(),
                        },
                      ],
                    }}
                  >
                    <Button className="user-menu-trigger">
                      <Space size={10}>
                        <span className="user-menu-trigger__avatar">
                          <UserOutlined />
                        </span>
                        <span className="user-menu-trigger__text">
                          <span className="user-menu-trigger__display-name">{user.displayName || user.username}</span>
                          <span className="user-menu-trigger__username">@{user.username}</span>
                        </span>
                      </Space>
                    </Button>
                  </Dropdown>
                </Space>
              </div>
            </div>
          </header>
          <Layout.Content className="min-h-0 flex-1 overflow-hidden px-0 py-0">
            <div className="h-full min-h-0 overflow-hidden px-4 py-4">
              {loadingNodes ? (
                <div className="flex min-h-[40vh] items-center justify-center">
                  <Spin size="large" />
                </div>
              ) : (
                <Routes>
                  <Route path="/" element={<RedirectByRole />} />
                  <Route path="/login" element={<Navigate to={isAdmin ? '/manager/nodes' : '/user/logs'} replace />} />
                  <Route path="/user/logs" element={<RequireAuth><UserLogsPage fullHeight /></RequireAuth>} />
                  <Route path="/user/diagnostics" element={<RequireAuth><DiagnosticRulesPage portal="user" nodes={nodes} /></RequireAuth>} />
                  <Route path="/user/diagnostics/:ruleId" element={<RequireAuth><DiagnosticRuleDetailPage portal="user" nodes={nodes} /></RequireAuth>} />
                  <Route path="/user/records" element={<RequireAuth><DiagnosticRecordsPage portal="user" nodes={nodes} /></RequireAuth>} />
                  <Route path="/user/records/:recordId" element={<RequireAuth><DiagnosticRecordDetailPage portal="user" nodes={nodes} /></RequireAuth>} />
                  <Route path="/manager/nodes" element={<RequireAdmin><ManagerNodesPage nodes={nodes} loading={loadingNodes} refresh={refresh} /></RequireAdmin>} />
                  <Route path="/manager/nodes/:nodeId/logs" element={<RequireAdmin><ManagerLogCollectionPage nodes={nodes} refresh={refresh} /></RequireAdmin>} />
                  <Route path="/manager/rules" element={<RequireAdmin><DiagnosticRulesPage portal="manager" nodes={nodes} /></RequireAdmin>} />
                  <Route path="/manager/rules/:ruleId" element={<RequireAdmin><DiagnosticRuleDetailPage portal="manager" nodes={nodes} /></RequireAdmin>} />
                  <Route path="/manager/users" element={<RequireAdmin><ManagerUsersPage /></RequireAdmin>} />
                  <Route path="/manager/records" element={<RequireAdmin><DiagnosticRecordsPage portal="manager" nodes={nodes} /></RequireAdmin>} />
                  <Route path="/manager/records/:recordId" element={<RequireAdmin><DiagnosticRecordDetailPage portal="manager" nodes={nodes} /></RequireAdmin>} />
                  <Route path="*" element={<RedirectByRole />} />
                </Routes>
              )}
            </div>
          </Layout.Content>
        </Layout>
      </ConfigProvider>
    </>
  );
}

export default function RootApp() {
  return <AppShell />;
}
