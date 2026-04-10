import { Button, Modal, Popconfirm, Select, Space, Switch, Table, Form, Input, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import type { UserPayload, UserProfile } from '../lib/api';
import { api, formatDateTime } from '../lib/api';

const emptyForm: UserPayload = {
  username: '',
  displayName: '',
  role: 'USER',
  enabled: true,
};

export function ManagerUsersPage() {
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState<'ADMIN' | 'USER' | undefined>();
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserProfile>();
  const [form] = Form.useForm<UserPayload>();

  const loadUsers = async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const result = await api.listUsersPage({ keyword: keyword.trim() || undefined, role: roleFilter, enabled: enabledFilter, page, pageSize });
      setUsers(result.items);
      setTotal(result.total);
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    void loadUsers();
  }, [keyword, roleFilter, enabledFilter, page, pageSize]);

  function openCreate() {
    setEditingUser(undefined);
    form.setFieldsValue(emptyForm);
    setModalOpen(true);
  }

  function openEdit(user: UserProfile) {
    setEditingUser(user);
    form.setFieldsValue({
      username: user.username,
      displayName: user.displayName,
      role: user.role,
      enabled: user.enabled,
    });
    setModalOpen(true);
  }

  async function saveUser() {
    const values = await form.validateFields();
    if (editingUser) {
      await api.updateUser(editingUser.id, values);
      message.success('用户已更新');
    } else {
      await api.createUser(values);
      message.success('用户已创建');
    }
    setModalOpen(false);
    setEditingUser(undefined);
    form.resetFields();
    await loadUsers(true);
  }

  async function resetPassword(user: UserProfile) {
    await api.resetUserPassword(user.id);
    message.success(`已重置 ${user.displayName} 的密码`);
    await loadUsers(true);
  }

  async function deleteUser(user: UserProfile) {
    await api.deleteUser(user.id);
    message.success('用户已删除');
    await loadUsers(true);
  }

  return (
    <>
      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Space wrap>
            <Input
              value={keyword}
              placeholder="搜索用户名 / 显示名称"
              onChange={(event) => {
                setKeyword(event.target.value);
                setPage(1);
              }}
              className="w-72"
            />
            <Select
              allowClear
              value={roleFilter}
              placeholder="筛选角色"
              options={[
                { label: '管理员', value: 'ADMIN' },
                { label: '普通用户', value: 'USER' },
              ]}
              onChange={(value) => {
                setRoleFilter(value);
                setPage(1);
              }}
              className="w-36"
            />
            <Select
              allowClear
              value={enabledFilter}
              placeholder="筛选状态"
              options={[
                { label: '启用', value: true },
                { label: '停用', value: false },
              ]}
              onChange={(value) => {
                setEnabledFilter(value);
                setPage(1);
              }}
              className="w-36"
            />
            <Button
              onClick={() => {
                setKeyword('');
                setRoleFilter(undefined);
                setEnabledFilter(undefined);
                setPage(1);
              }}
            >
              重置
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => void loadUsers()}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新增用户
            </Button>
          </Space>
        </div>

        <div className="dx-section p-4 shadow-panel">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="dx-section-title text-xl">用户管理</div>
          </div>

          <Table
            className="dx-table"
            rowKey="id"
            loading={loading}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              position: ['bottomRight'],
              onChange: (current, size) => {
                setPage(current);
                setPageSize(size);
              },
            }}
            dataSource={users}
            scroll={{ x: 980 }}
            columns={[
              { title: '用户名', dataIndex: 'username', width: 180 },
              { title: '显示名称', dataIndex: 'displayName', width: 180 },
              {
                title: '角色',
                width: 120,
                render: (_, record) => (record.role === 'ADMIN' ? '管理员' : '普通用户'),
              },
              {
                title: '状态',
                width: 120,
                render: (_, record) => (record.enabled ? '启用' : '停用'),
              },
              {
                title: '更新时间',
                width: 180,
                render: (_, record) => formatDateTime(record.updatedAt),
              },
              {
                title: '操作',
                width: 260,
                render: (_, record) => (
                  <Space size="small" wrap>
                    <Button onClick={() => openEdit(record)}>编辑</Button>
                    <Popconfirm title={`确认重置 ${record.displayName} 的密码吗？`} onConfirm={() => void resetPassword(record)}>
                      <Button>重置密码</Button>
                    </Popconfirm>
                    <Popconfirm title="确认删除这个用户吗？" onConfirm={() => void deleteUser(record)}>
                      <Button danger>删除</Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        </div>
      </div>
      <Modal
        open={modalOpen}
        title={editingUser ? '编辑用户' : '新增用户'}
        onCancel={() => {
          setModalOpen(false);
          setEditingUser(undefined);
        }}
        onOk={() => void saveUser()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={emptyForm}>
          <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="请输入用户名" disabled={Boolean(editingUser)} />
          </Form.Item>
          <Form.Item label="显示名称" name="displayName" rules={[{ required: true, message: '请输入显示名称' }]}>
            <Input placeholder="请输入显示名称" />
          </Form.Item>
          <Form.Item label="角色" name="role" rules={[{ required: true, message: '请选择角色' }]}>
            <Select
              options={[
                { label: '管理员', value: 'ADMIN' },
                { label: '普通用户', value: 'USER' },
              ]}
            />
          </Form.Item>
          <Form.Item label="启用状态" name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
