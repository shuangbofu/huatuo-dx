import { Alert, Button, Checkbox, Empty, Form, Input, InputNumber, Modal, Select, Space, Table, message } from 'antd';
import { DownloadOutlined, EditOutlined, FileSearchOutlined, PoweroffOutlined, RadarChartOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, formatDateTime, formatNodeStatus } from '../lib/api';
import type { AgentNode, ArthasStatus, ProcessView } from '../lib/api';
import { Pill } from '../components/Pill';

interface Props {
  nodes: AgentNode[];
  loading: boolean;
  refresh: (silent?: boolean) => Promise<void>;
}

export function ManagerNodesPage({ nodes, loading, refresh }: Props) {
  const navigate = useNavigate();
  const [pagedNodes, setPagedNodes] = useState<AgentNode[]>([]);
  const [processes, setProcesses] = useState<ProcessView[]>([]);
  const [activeNode, setActiveNode] = useState<AgentNode | null>(null);
  const [open, setOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<AgentNode | null>(null);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ONLINE' | 'OFFLINE'>('ALL');
  const [form] = Form.useForm();
  const [messageApi, holder] = message.useMessage();
  const [arthasStatusMap, setArthasStatusMap] = useState<Record<number, ArthasStatus>>({});
  const [savingVisibleProcesses, setSavingVisibleProcesses] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  async function loadPagedNodes() {
    const result = await api.listNodesPage({
      keyword: keyword.trim() || undefined,
      status: statusFilter,
      page,
      pageSize,
    });
    setPagedNodes(result.items);
    setTotal(result.total);
  }

  const companionRestartNode = nodes.find((node) => node.localCompanion && node.companionRestartRecommended);

  useEffect(() => {
    void loadPagedNodes();
  }, [keyword, statusFilter, page, pageSize, nodes.length]);

  useEffect(() => {
    void Promise.all(pagedNodes
      .filter((node) => node.status === 'ONLINE')
      .map(async (node) => {
        try {
          const status = await api.getArthasStatus(node.id);
          return [node.id, status] as const;
        } catch {
          return [node.id, { installed: false, status: 'FAILED', message: '检测失败' } satisfies ArthasStatus] as const;
        }
      }))
      .then((entries) => {
        const nextEntries = Object.fromEntries(entries);
        setArthasStatusMap((current) => {
          const offlineEntries = Object.fromEntries(
            nodes
              .filter((node) => node.status !== 'ONLINE')
              .map((node) => [node.id, current[node.id]])
              .filter((entry) => !!entry[1]),
          );
          return { ...offlineEntries, ...nextEntries };
        });
      });
  }, [pagedNodes, nodes]);

  async function openProcesses(node: AgentNode) {
    setActiveNode(node);
    setOpen(true);
    setProcesses(await api.getAllProcesses(node.id));
  }

  async function toggleVisibleProcess(process: ProcessView, checked: boolean) {
    if (!activeNode) {
      return;
    }
    const nextVisibleProcessNames = checked
      ? Array.from(new Set([...(activeNode.visibleProcessNames || []), process.displayName]))
      : (activeNode.visibleProcessNames || []).filter((item) => item !== process.displayName);
    setSavingVisibleProcesses(true);
    try {
      await api.updateNode(activeNode.id, {
        nodeName: activeNode.nodeName,
        host: activeNode.host,
        port: activeNode.port,
        baseUrl: activeNode.baseUrl,
        secret: '',
        processPattern: activeNode.processPattern,
        visibleProcessNames: nextVisibleProcessNames,
        logDirectories: activeNode.logDirectories,
        logSourceConfigs: activeNode.logSourceConfigs,
        companionBootstrapLogDirectories: activeNode.companionBootstrapLogDirectories,
        logCollectEnabled: activeNode.logCollectEnabled,
        logCollectIntervalSeconds: activeNode.logCollectIntervalSeconds,
        tags: activeNode.tags,
        arthasBootJar: activeNode.arthasBootJar,
      });
      setActiveNode({ ...activeNode, visibleProcessNames: nextVisibleProcessNames });
      await refresh(true);
    } finally {
      setSavingVisibleProcesses(false);
    }
  }

  async function refreshArthasStatus(nodeId: number) {
    const node = nodes.find((item) => item.id === nodeId);
    if (!node || node.status !== 'ONLINE') {
      return;
    }
    const status = await api.getArthasStatus(nodeId);
    setArthasStatusMap((current) => ({ ...current, [nodeId]: status }));
    return status;
  }

  async function installArthas(nodeId: number) {
    const node = nodes.find((item) => item.id === nodeId);
    if (!node || node.status !== 'ONLINE') {
      return;
    }
    const status = await api.installArthas(nodeId);
    setArthasStatusMap((current) => ({ ...current, [nodeId]: status }));
    messageApi.success(status.message || '诊断引擎安装完成');
  }

  function openEditor(node: AgentNode) {
    setEditingNode(node);
    form.setFieldsValue({
      nodeName: node.nodeName,
      host: node.host,
      port: node.port,
      baseUrl: node.baseUrl,
      secret: '',
      processPattern: node.processPattern,
      companionBootstrapLogDirectories: node.companionBootstrapLogDirectories.join('\n'),
      tags: node.tags.join(','),
    });
    setEditOpen(true);
  }

  async function submitNodeUpdate() {
    if (!editingNode) {
      return;
    }
    const values = await form.validateFields();
    await api.updateNode(editingNode.id, {
      nodeName: values.nodeName,
      host: values.host,
      port: values.port,
      baseUrl: values.baseUrl,
      secret: values.secret || '',
      processPattern: values.processPattern,
      visibleProcessNames: editingNode.visibleProcessNames,
      logDirectories: editingNode.logDirectories,
      logSourceConfigs: editingNode.logSourceConfigs,
      companionBootstrapLogDirectories: String(values.companionBootstrapLogDirectories || '')
        .split('\n')
        .map((item) => item.trim())
        .filter(Boolean),
      logCollectEnabled: editingNode.logCollectEnabled,
      logCollectIntervalSeconds: editingNode.logCollectIntervalSeconds,
      tags: String(values.tags || '')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
      arthasBootJar: editingNode.arthasBootJar,
    });
    messageApi.success('节点配置已更新，日志采集配置已保存');
    setEditOpen(false);
    await refresh();
  }

  if (!nodes.length && !loading) {
    return <Empty description="暂无节点，请先启动 agent 完成注册" />;
  }

  return (
    <>
      {holder}
      {companionRestartNode ? (
        <Alert
          className="mb-3"
          type="info"
          showIcon
          message={`检测到伴生 Agent 已在运行：${companionRestartNode.nodeName}`}
          description="Manager 已重启，但伴生 Agent 没有跟着重启。当前可以继续使用；如果你希望重新对齐配置，可以手动执行一次重启。"
              action={(
            <Button
              size="small"
              type="primary"
              onClick={() => api.restartLocalCompanion(companionRestartNode.id).then(() => refresh())}
            >
              重启伴生 Agent
            </Button>
          )}
        />
      ) : null}
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <Space wrap>
          <Input
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(1);
            }}
            placeholder="节点名 / 编码 / 主机"
            className="w-64"
          />
          <Select
            value={statusFilter}
            onChange={(value) => {
              setStatusFilter(value);
              setPage(1);
            }}
            options={[
              { label: '全部状态', value: 'ALL' },
              { label: '在线', value: 'ONLINE' },
              { label: '离线', value: 'OFFLINE' },
            ]}
            className="w-36"
          />
          <Button onClick={() => {
            setKeyword('');
            setStatusFilter('ALL');
            setPage(1);
          }}
          >
            重置
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>
            刷新
          </Button>
        </Space>
      </div>
      <div className="dx-section p-4 shadow-panel">
        <div className="mb-2 flex items-center justify-between gap-3">
          <div className="dx-section-title text-xl">节点管理</div>
        </div>
        <Table
          className="dx-table"
          rowKey="id"
          loading={loading}
          dataSource={pagedNodes}
          scroll={{ x: 1480 }}
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
          columns={[
            {
              title: '节点',
              width: 260,
              render: (_, node: AgentNode) => (
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-800">{node.nodeName}</span>
                    {node.localCompanion ? <Pill tone="blue">伴生</Pill> : null}
                    <Pill tone={node.status === 'ONLINE' ? 'green' : 'red'}>{formatNodeStatus(node.status)}</Pill>
                  </div>
                  <div className="text-xs text-slate-500">{node.nodeCode}</div>
                </div>
              ),
            },
            { title: '地址', width: 220, render: (_, node: AgentNode) => `${node.host}:${node.port}` },
            { title: '默认进程匹配', dataIndex: 'processPattern', width: 240, ellipsis: true },
            { title: '可见进程', width: 100, render: (_, node: AgentNode) => node.visibleProcessNames.length ? `${node.visibleProcessNames.length} 项` : '未勾选' },
            { title: '日志配置', width: 100, render: (_, node: AgentNode) => `${node.logSourceConfigs.length} 项` },
            { title: '运行时', dataIndex: 'runtimeVersion', width: 160, render: (value?: string) => value || '-' },
            { title: '当前匹配', dataIndex: 'matchedProcessSummary', width: 180, render: (value?: string) => value || '-' },
            { title: '最近心跳', dataIndex: 'lastHeartbeatAt', width: 180, render: (value: string) => formatDateTime(value) },
            {
              title: '诊断引擎',
              width: 236,
              render: (_, node: AgentNode) => {
                const arthas = arthasStatusMap[node.id];
                return (
                  <div className="flex items-center gap-2 whitespace-nowrap">
                    <div className="shrink-0 text-sm text-slate-800">
                      {arthas
                        ? arthas.installed
                          ? '已就绪'
                          : arthas.message || '未安装'
                        : node.status === 'ONLINE'
                          ? '待检测'
                          : '节点离线'}
                    </div>
                    <Space size="small" className="whitespace-nowrap">
                      <Button
                        className="whitespace-nowrap"
                        icon={<SearchOutlined />}
                        disabled={node.status !== 'ONLINE'}
                        onClick={() => void refreshArthasStatus(node.id)}
                      >
                        检测
                      </Button>
                      {arthas?.installed ? null : (
                        <Button
                          className="whitespace-nowrap"
                          icon={<DownloadOutlined />}
                          type="primary"
                          disabled={node.status !== 'ONLINE'}
                          onClick={() => void installArthas(node.id)}
                        >
                          安装
                        </Button>
                      )}
                    </Space>
                  </div>
                );
              },
            },
            {
              title: '操作',
              width: 360,
              render: (_, node: AgentNode) => (
                <Space size="small" wrap>
                  <Button icon={<RadarChartOutlined />} onClick={() => void openProcesses(node)}>
                    查看 Java 进程
                  </Button>
                  <Button icon={<EditOutlined />} onClick={() => openEditor(node)}>
                    编辑配置
                  </Button>
                  <Button icon={<FileSearchOutlined />} onClick={() => navigate(`/manager/nodes/${node.id}/logs`)}>
                    日志采集
                  </Button>
                  {node.localCompanion ? (
                    <Button icon={<PoweroffOutlined />} onClick={() => api.restartLocalCompanion(node.id).then(() => refresh())}>
                      重启伴生 Agent
                    </Button>
                  ) : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Modal className="dx-modal" open={open} onCancel={() => setOpen(false)} footer={null} width={1080} title={activeNode ? `${activeNode.nodeName} 的 Java 进程` : 'Java 进程'}>
        <Table
          className="dx-table"
          rowKey="pid"
          dataSource={processes}
          pagination={false}
          scroll={{ x: 980 }}
          columns={[
            {
              title: '可见',
              dataIndex: 'displayName',
              width: 90,
              render: (displayName: string, process: ProcessView) => (
                <Checkbox
                  checked={(activeNode?.visibleProcessNames || []).includes(displayName)}
                  disabled={savingVisibleProcesses}
                  onChange={(event) => void toggleVisibleProcess(process, event.target.checked)}
                />
              ),
            },
            { title: 'PID', dataIndex: 'pid', width: 120 },
            { title: '显示名', dataIndex: 'displayName' },
            { title: '用户', dataIndex: 'user', width: 120 },
            { title: '命令行', dataIndex: 'commandLine' },
          ]}
        />
      </Modal>
      <Modal
        className="dx-modal"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        width={760}
        title={editingNode ? `编辑节点配置 · ${editingNode.nodeName}` : '编辑节点配置'}
        footer={
          <Space>
            <Button onClick={() => setEditOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submitNodeUpdate()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form layout="vertical" form={form}>
          <Form.Item name="nodeName" label="节点名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item name="host" label="主机" rules={[{ required: true }]}>
              <Input disabled={editingNode?.localCompanion} />
            </Form.Item>
            <Form.Item name="port" label="端口" rules={[{ required: true }]}>
              <InputNumber className="w-full" min={1} max={65535} disabled={editingNode?.localCompanion} />
            </Form.Item>
          </div>
          <Form.Item name="baseUrl" label="Agent 访问地址" rules={[{ required: true }]}>
            <Input disabled={editingNode?.localCompanion} />
          </Form.Item>
          {editingNode?.localCompanion ? (
            <Form.Item label="节点密钥">
              <div className="border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500">
                伴生节点密钥由 Manager 启动时自动维护，这里不提供手工修改。
              </div>
            </Form.Item>
          ) : (
            <Form.Item name="secret" label="节点密钥" extra="这里建议填与 agent 配置一致的 secret；为空则保留现有值。">
              <Input.Password placeholder="change-me" />
            </Form.Item>
          )}
          <Form.Item name="processPattern" label="默认 Java 进程匹配" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          {editingNode?.localCompanion ? (
            <Form.Item
              name="companionBootstrapLogDirectories"
              label="伴生 log-directories"
              extra="一行一个目录。伴生 Agent 启动时会先带上这组 log-directories。"
            >
              <Input.TextArea rows={4} placeholder={"/data/logs/order-service\n/Users/you/logs/demo"} />
            </Form.Item>
          ) : null}
          <Form.Item name="tags" label="标签">
            <Input placeholder="test, order, app1" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
