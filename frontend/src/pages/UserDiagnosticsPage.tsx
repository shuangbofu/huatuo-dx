import { Button, Input, Popconfirm, Select, Space, Table, Typography, message } from 'antd';
import { PlayCircleOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { AgentNode, DiagnosticRule } from '../lib/api';
import { api, formatDiagnosticType } from '../lib/api';
import { DiagnosticExecutionModal } from '../components/DiagnosticExecutionModal';
import { DiagnosticRuleEditor } from '../components/DiagnosticRuleEditor';
import { Pill } from '../components/Pill';

interface Props {
  nodes: AgentNode[];
}

export function UserDiagnosticsPage({ nodes }: Props) {
  const navigate = useNavigate();
  const [rules, setRules] = useState<DiagnosticRule[]>([]);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'WATCH' | 'TRACE' | 'STACK'>('ALL');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [executingRule, setExecutingRule] = useState<DiagnosticRule | null>(null);
  const [monitoring, setMonitoring] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<DiagnosticRule | null>(null);
  const [messageApi, holder] = message.useMessage();

  useEffect(() => {
    void reload();
  }, [keyword, typeFilter, page, pageSize]);

  async function reload() {
    setLoading(true);
    try {
      const result = await api.listRulesPage({
        keyword: keyword.trim() || undefined,
        type: typeFilter,
        enabled: true,
        page,
        pageSize,
      });
      setRules(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  }

  async function start(rule: DiagnosticRule, node: AgentNode, process: any, maxMatches: number) {
    setMonitoring(true);
    try {
      const record = await api.startRule(rule.id, {
        nodeId: node.id,
        pid: process.pid,
        processName: process.displayName,
        processPattern: process.commandLine || process.displayName,
        maxMatches,
      });
      setExecutingRule(null);
      messageApi.success('监控已启动');
      navigate(`/user/records/${record.id}`);
    } finally {
      setMonitoring(false);
    }
  }

  async function submit(payload: Partial<DiagnosticRule>) {
    if (editingRule) {
      await api.updateRule(editingRule.id, payload);
      messageApi.success('规则已更新');
      setEditingRule(null);
    } else {
      await api.createRule(payload);
      messageApi.success('规则已创建');
      setEditorOpen(false);
    }
    await reload();
  }

  async function removeRule(rule: DiagnosticRule) {
    await api.deleteRule(rule.id);
    messageApi.success('规则已删除');
    await reload();
  }

  return (
    <>
      {holder}
      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Space wrap>
            <Select
              value={typeFilter}
              onChange={(value) => {
                setTypeFilter(value);
                setPage(1);
              }}
              options={[
                { label: '全部类型', value: 'ALL' },
                { label: '监看', value: 'WATCH' },
                { label: '链路', value: 'TRACE' },
                { label: '堆栈', value: 'STACK' },
              ]}
              className="w-36"
            />
            <Input value={keyword} onChange={(event) => {
              setKeyword(event.target.value);
              setPage(1);
            }} placeholder="规则名 / 类 / 方法" className="w-72" />
            <Button onClick={() => {
              setKeyword('');
              setTypeFilter('ALL');
              setPage(1);
            }}
            >
              重置
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => void reload()}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditorOpen(true)}>
              新建规则
            </Button>
          </Space>
        </div>

        <div className="dx-section p-4 shadow-panel">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="dx-section-title text-xl">诊断规则</div>
            <Typography.Text type="secondary">{total} 条</Typography.Text>
          </div>
          <Table
            className="dx-table"
            rowKey="id"
            loading={loading}
            dataSource={rules}
            scroll={{ x: 920 }}
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
              { title: '规则名', dataIndex: 'name', width: 180 },
              {
                title: '类型',
                dataIndex: 'type',
                width: 100,
                render: (value: DiagnosticRule['type']) => <Pill tone={value === 'WATCH' ? 'blue' : value === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(value)}</Pill>,
              },
              { title: '备注', dataIndex: 'notes', width: 240, ellipsis: true, render: (value?: string) => value || '-' },
              { title: '默认进程', dataIndex: 'selectedProcessName', width: 220, render: (value?: string) => value || '-' },
              { title: '类', dataIndex: 'targetClassPattern', width: 240, ellipsis: true },
              { title: '方法', dataIndex: 'targetMethodPattern', width: 160, ellipsis: true },
              {
                title: '操作',
                width: 300,
                render: (_, rule: DiagnosticRule) => (
                  <Space size="small" wrap>
                    <Button onClick={() => navigate(`/user/diagnostics/${rule.id}`)}>
                      详情
                    </Button>
                    <Button onClick={() => setEditingRule(rule)}>
                      编辑
                    </Button>
                    <Button icon={<PlayCircleOutlined />} type="primary" onClick={() => setExecutingRule(rule)}>
                      启动
                    </Button>
                    <Popconfirm
                      title="确认删除这条规则？"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => void removeRule(rule)}
                    >
                      <Button danger>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        </div>
      </div>

      <DiagnosticRuleEditor
        open={editorOpen}
        title="新建诊断规则"
        onCancel={() => setEditorOpen(false)}
        onSubmit={submit}
      />
      <DiagnosticRuleEditor
        open={!!editingRule}
        title="编辑诊断规则"
        initialRule={editingRule}
        onCancel={() => setEditingRule(null)}
        onSubmit={submit}
      />
      <DiagnosticExecutionModal
        open={!!executingRule}
        ruleName={executingRule?.name}
        defaultProcessName={executingRule?.selectedProcessName}
        defaultMaxMatches={executingRule?.maxMatches}
        nodes={nodes}
        loading={monitoring}
        onCancel={() => setExecutingRule(null)}
        onSubmit={(node, process, maxMatches) => start(executingRule!, node, process, maxMatches)}
      />
    </>
  );
}
