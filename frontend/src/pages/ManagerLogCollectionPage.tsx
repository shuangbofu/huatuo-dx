import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Empty, Form, Input, InputNumber, Modal, Select, Space, Table, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, formatDateTime } from '../lib/api';
import type { AgentNode, LogCollectionStatus, LogSourceConfig } from '../lib/api';
import { Pill } from '../components/Pill';

interface Props {
  nodes: AgentNode[];
  refresh: () => Promise<void>;
}

interface LogSourceRow {
  id: string;
  name: string;
  description?: string;
  path: string;
  mode: string;
  parseMode: string;
  parsePattern?: string;
  logbackConfigPath?: string;
  interval: number;
}

export function ManagerLogCollectionPage({ nodes, refresh }: Props) {
  const navigate = useNavigate();
  const params = useParams();
  const [open, setOpen] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [parseTestResult, setParseTestResult] = useState<string>('');
  const [statusMap, setStatusMap] = useState<Record<string, LogCollectionStatus>>({});
  const [statusLoading, setStatusLoading] = useState(false);
  const [form] = Form.useForm();
  const [messageApi, holder] = message.useMessage();

  const nodeId = Number(params.nodeId);
  const node = nodes.find((item) => item.id === nodeId);

  const rows = useMemo<LogSourceRow[]>(() => {
    if (!node) {
      return [];
    }
    const sourceConfigs: LogSourceConfig[] = node.logSourceConfigs?.length
      ? node.logSourceConfigs
      : node.logDirectories.map((path) => ({
        name: path,
        description: '',
        path,
        mode: 'QUERY',
        parseMode: 'RAW' as const,
        collectIntervalSeconds: 5,
      }));
    return sourceConfigs.map((source, index) => ({
      id: `${node.id}-${index}`,
      name: source.name,
      description: source.description,
      path: source.path,
      mode: source.mode || 'QUERY',
      parseMode: source.parseMode || 'RAW',
      parsePattern: source.parsePattern,
      logbackConfigPath: source.logbackConfigPath,
      interval: source.collectIntervalSeconds || 5,
    }));
  }, [node]);

  useEffect(() => {
    const collectRows = rows.filter((row) => row.mode === 'COLLECT');
    if (!node || node.status !== 'ONLINE' || !collectRows.length) {
      setStatusMap({});
      return;
    }
    let cancelled = false;
    setStatusLoading(true);
    Promise.all(collectRows.map(async (row) => [row.path, await api.getLogCollectionStatus(node.id, row.path)] as const))
      .then((entries) => {
        if (cancelled) {
          return;
        }
        setStatusMap(Object.fromEntries(entries));
      })
      .catch(() => {
        if (!cancelled) {
          setStatusMap({});
        }
      })
      .finally(() => {
        if (!cancelled) {
          setStatusLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [node?.id, node?.status, rows.map((row) => `${row.mode}:${row.path}`).join('|')]);

  function openEditor(index?: number) {
    const row = index == null ? null : rows[index];
    setEditingIndex(index ?? null);
    form.setFieldsValue({
      name: row?.name ?? '',
      description: row?.description ?? '',
      path: row?.path ?? '',
      mode: row?.mode ?? 'QUERY',
      parseMode: row?.parseMode ?? 'RAW',
      parsePattern: row?.parsePattern ?? '',
      logbackConfigPath: row?.logbackConfigPath ?? '',
      interval: row?.interval ?? 5,
    });
    setParseTestResult('');
    setOpen(true);
  }

  async function save() {
    if (!node) {
      return;
    }
    const values = await form.validateFields();
    const nextSources: LogSourceConfig[] = [...(node.logSourceConfigs?.length
      ? node.logSourceConfigs
      : node.logDirectories.map((path) => ({
        name: path,
        description: '',
        path,
        mode: 'QUERY',
        parseMode: 'RAW' as const,
        collectIntervalSeconds: 5,
      })))];
    const nextSource = {
      name: String(values.name || '').trim(),
      description: String(values.description || '').trim(),
      path: String(values.path || '').trim(),
      mode: String(values.mode || 'QUERY').trim(),
      parseMode: String(values.parseMode || 'RAW').trim(),
      parsePattern: String(values.parsePattern || '').trim() || undefined,
      logbackConfigPath: String(values.logbackConfigPath || '').trim() || undefined,
      collectIntervalSeconds: Number(values.interval || 5),
    };
    if (editingIndex == null) {
      nextSources.push(nextSource);
    } else {
      nextSources[editingIndex] = nextSource;
    }
    const collectSources = nextSources.filter((item) => String(item.mode || 'QUERY').toUpperCase() === 'COLLECT');
    await api.updateNode(node.id, {
      nodeName: node.nodeName,
      host: node.host,
      port: node.port,
      baseUrl: node.baseUrl,
      secret: '',
      processPattern: node.processPattern,
      visibleProcessNames: node.visibleProcessNames,
      logDirectories: nextSources.map((item) => item.path).filter(Boolean),
      logSourceConfigs: nextSources.filter((item) => item.path),
      companionBootstrapLogDirectories: node.companionBootstrapLogDirectories,
      logCollectEnabled: collectSources.length > 0,
      logCollectIntervalSeconds: collectSources.length ? Math.min(...collectSources.map((item) => item.collectIntervalSeconds || 5)) : 5,
      tags: node.tags,
      arthasBootJar: node.arthasBootJar,
    });
    setOpen(false);
    await refresh();
    messageApi.success('日志采集配置已保存');
  }

  async function testParse() {
    if (!node) {
      return;
    }
    const values = await form.validateFields(['path']);
    const targetPath = String(values.path || '').trim();
    if (!targetPath) {
      messageApi.warning('请先填写日志路径');
      return;
    }
    const sampleLine = await resolveSampleLine(node.id, targetPath);
    if (!sampleLine) {
      setParseTestResult('没有从真实日志中取到可用样本，请检查路径下是否有日志文件和内容');
      return;
    }
    const parseMode = String(form.getFieldValue('parseMode') || 'RAW');
    const parsePattern = String(form.getFieldValue('parsePattern') || '');
    const preview = buildParsePreview(sampleLine, parseMode, parsePattern);
    setParseTestResult(preview);
  }

  async function remove(index: number) {
    if (!node) {
      return;
    }
    const nextSources = (node.logSourceConfigs?.length
      ? node.logSourceConfigs
      : node.logDirectories.map((path) => ({
        name: path,
        description: '',
        path,
        mode: 'QUERY',
        parseMode: 'RAW' as const,
        collectIntervalSeconds: 5,
      })))
      .filter((_, current) => current !== index);
    const collectSources = nextSources.filter((item) => String(item.mode || 'QUERY').toUpperCase() === 'COLLECT');
    await api.updateNode(node.id, {
      nodeName: node.nodeName,
      host: node.host,
      port: node.port,
      baseUrl: node.baseUrl,
      secret: '',
      processPattern: node.processPattern,
      visibleProcessNames: node.visibleProcessNames,
      logDirectories: nextSources.map((item) => item.path),
      logSourceConfigs: nextSources,
      companionBootstrapLogDirectories: node.companionBootstrapLogDirectories,
      logCollectEnabled: collectSources.length > 0,
      logCollectIntervalSeconds: collectSources.length ? Math.min(...collectSources.map((item) => item.collectIntervalSeconds || 5)) : 5,
      tags: node.tags,
      arthasBootJar: node.arthasBootJar,
    });
    await refresh();
    messageApi.success('日志采集配置已删除');
  }

  if (!node) {
    return <Empty description="节点不存在" />;
  }

  return (
    <>
      {holder}
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-2">
          <div>
            <div className="dx-section-title text-xl">日志采集</div>
            <div className="text-sm text-slate-500">{node.nodeName} · {node.host}:{node.port}</div>
          </div>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/manager/nodes')}>返回节点管理</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>新增日志配置</Button>
          </Space>
        </div>

        <div className="dx-section p-4 shadow-panel">
          {!rows.length ? (
            <Empty description="还没有日志采集配置" />
          ) : (
            <Table
              className="dx-table"
              rowKey="id"
              dataSource={rows}
              pagination={{ pageSize: 10, showSizeChanger: false, position: ['bottomRight'] }}
              columns={[
                { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
                { title: '描述', dataIndex: 'description', width: 220, ellipsis: true, render: (value?: string) => value || '-' },
                { title: '日志路径', dataIndex: 'path', ellipsis: true },
                {
                  title: '模式',
                  dataIndex: 'mode',
                  width: 100,
                  render: (value: string) => <Pill tone={value === 'COLLECT' ? 'green' : 'blue'}>{value === 'COLLECT' ? '采集' : '查询'}</Pill>,
                },
                {
                  title: '解析',
                  dataIndex: 'parseMode',
                  width: 140,
                  render: (value: string) => value === 'LOGBACK_PATTERN' ? 'Logback Pattern' : value === 'JSON' ? 'JSON' : 'RAW',
                },
                { title: '间隔(秒)', dataIndex: 'interval', width: 100, render: (value: number, row) => row.mode === 'COLLECT' ? value : '-' },
                {
                  title: '已采文件',
                  width: 100,
                  render: (_, row) => {
                    if (row.mode !== 'COLLECT') {
                      return '-';
                    }
                    const status = statusMap[row.path];
                    if (node.status !== 'ONLINE') {
                      return '-';
                    }
                    return status ? status.indexedFileCount : (statusLoading ? '...' : '0');
                  },
                },
                {
                  title: '已采行数',
                  width: 110,
                  render: (_, row) => {
                    if (row.mode !== 'COLLECT') {
                      return '-';
                    }
                    const status = statusMap[row.path];
                    if (node.status !== 'ONLINE') {
                      return '-';
                    }
                    return status ? status.indexedLineCount : (statusLoading ? '...' : '0');
                  },
                },
                {
                  title: '最近采集',
                  width: 180,
                  render: (_, row) => {
                    if (row.mode !== 'COLLECT') {
                      return '查询模式';
                    }
                    if (node.status !== 'ONLINE') {
                      return '节点离线';
                    }
                    const status = statusMap[row.path];
                    if (!status) {
                      return statusLoading ? '读取中' : '未采集';
                    }
                    return status.latestCollectedAtEpochMs ? formatDateTime(new Date(status.latestCollectedAtEpochMs).toISOString()) : '未采集';
                  },
                },
                {
                  title: '操作',
                  width: 220,
                  render: (_, __, index) => (
                    <Space wrap>
                      <Button icon={<EditOutlined />} onClick={() => openEditor(index)}>编辑</Button>
                      <Button danger icon={<DeleteOutlined />} onClick={() => void remove(index)}>删除</Button>
                    </Space>
                  ),
                },
              ]}
            />
          )}
        </div>
      </div>

      <Modal
        className="dx-modal"
        open={open}
        onCancel={() => setOpen(false)}
        width={760}
        title={editingIndex == null ? '新增日志配置' : '编辑日志配置'}
        footer={(
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void save()}>保存</Button>
          </Space>
        )}
      >
        <Form layout="vertical" form={form}>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input placeholder="订单服务主日志" />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input placeholder="说明这组日志是干什么的，给谁查的" />
            </Form.Item>
          </div>
          <Form.Item name="path" label="日志路径" rules={[{ required: true, message: '请输入日志路径' }]}>
            <Input placeholder="/data/logs/order-service" />
          </Form.Item>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item name="mode" label="工作模式" rules={[{ required: true, message: '请选择工作模式' }]}>
              <Select
                options={[
                  { label: '查询模式', value: 'QUERY' },
                  { label: '采集模式', value: 'COLLECT' },
                ]}
              />
            </Form.Item>
            <Form.Item name="parseMode" label="解析方式" rules={[{ required: true, message: '请选择解析方式' }]}>
              <Select
                options={[
                  { label: 'RAW', value: 'RAW' },
                  { label: 'Logback Pattern', value: 'LOGBACK_PATTERN' },
                  { label: 'JSON', value: 'JSON' },
                ]}
              />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {() => {
                const parseMode = form.getFieldValue('parseMode');
                if (parseMode === 'LOGBACK_PATTERN') {
                  return (
                    <Form.Item name="parsePattern" label="解析模板" rules={[{ required: true, message: '请输入日志解析模板' }]}>
                      <Input placeholder="%d %-5level [%thread] %logger - %msg%n" />
                    </Form.Item>
                  );
                }
                if (parseMode === 'JSON') {
                  return (
                    <Form.Item name="parsePattern" label="字段映射">
                      <Input placeholder='可选，例: timestamp:@timestamp,level:level,message:message' />
                    </Form.Item>
                  );
                }
                return (
                  <Form.Item name="logbackConfigPath" label="logback.xml 路径">
                    <Input placeholder="可选，用于记录对应的 logback.xml 位置" />
                  </Form.Item>
                );
              }}
            </Form.Item>
          </div>
          <Form.Item noStyle shouldUpdate>
            {() => {
              const mode = form.getFieldValue('mode');
              if (mode !== 'COLLECT') {
                return null;
              }
              return (
                <div className="grid grid-cols-1 items-end gap-4 md:grid-cols-2">
                  <Form.Item name="interval" label="采集间隔(秒)" rules={[{ required: true }]} className="mb-0">
                    <InputNumber className="w-full" min={1} max={3600} />
                  </Form.Item>
                </div>
              );
            }}
          </Form.Item>
          <div className="space-y-2 border border-slate-200 bg-slate-50 px-3 py-3">
            <div className="flex items-center justify-between gap-3">
              <div className="text-sm font-medium text-slate-700">真实日志预览</div>
              <Button onClick={() => void testParse()}>读取一条真实日志</Button>
            </div>
            <pre className="min-h-[120px] whitespace-pre-wrap break-all bg-white px-3 py-2 text-sm text-slate-700">{parseTestResult || '这里会直接从真实日志路径读取一条样本日志'}</pre>
          </div>
        </Form>
      </Modal>
    </>
  );
}

async function resolveSampleLine(nodeId: number, targetPath: string): Promise<string> {
  try {
    const entries = await api.listNodeLogs(nodeId, targetPath);
    const file = entries.find((item) => !item.directory) ?? null;
    if (file) {
      const content = await api.readNodeLog(nodeId, file.path, 20);
      const lines = content.content.split('\n').map((item) => item.trim()).filter(Boolean);
      return lines[lines.length - 1] ?? '';
    }
  } catch {
    // Fall through to direct file read.
  }
  const content = await api.readNodeLog(nodeId, targetPath, 20);
  const lines = content.content.split('\n').map((item) => item.trim()).filter(Boolean);
  return lines[lines.length - 1] ?? '';
}

function buildParsePreview(sampleLine: string, parseMode: string, parsePattern: string): string {
  if (parseMode === 'JSON') {
    try {
      const parsed = JSON.parse(sampleLine);
      return `真实日志样本\n${sampleLine}\n\n解析结果\n${JSON.stringify(parsed, null, 2)}`;
    } catch {
      return `真实日志样本\n${sampleLine}\n\n解析结果\nJSON 解析失败，请检查日志内容或字段映射`;
    }
  }
  if (parseMode === 'LOGBACK_PATTERN') {
    return `真实日志样本\n${sampleLine}\n\n解析方式\nLogback Pattern\n${parsePattern || '(未填写解析模板)'}`;
  }
  return `真实日志样本\n${sampleLine}\n\n解析方式\nRAW`;
}
