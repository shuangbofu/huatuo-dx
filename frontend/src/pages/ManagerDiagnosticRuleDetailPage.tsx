import { ArrowLeftOutlined, PlayCircleOutlined, StopOutlined } from '@ant-design/icons';
import { Button, Empty, Space, Table, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { AgentNode, DiagnosticExecutionRecord, DiagnosticRule, ProcessView } from '../lib/api';
import { api, formatDateTime, formatDiagnosticExecutionStatus, formatDiagnosticType } from '../lib/api';
import { DiagnosticExecutionModal } from '../components/DiagnosticExecutionModal';
import { DiagnosticRuleEditor } from '../components/DiagnosticRuleEditor';
import { Pill } from '../components/Pill';

interface Props {
  nodes: AgentNode[];
}

export function ManagerDiagnosticRuleDetailPage({ nodes }: Props) {
  const navigate = useNavigate();
  const { ruleId } = useParams();
  const [rules, setRules] = useState<DiagnosticRule[]>([]);
  const [records, setRecords] = useState<DiagnosticExecutionRecord[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [recordsPage, setRecordsPage] = useState(1);
  const [recordsPageSize, setRecordsPageSize] = useState(10);
  const [recordsTotal, setRecordsTotal] = useState(0);
  const [editorOpen, setEditorOpen] = useState(false);
  const [executingRule, setExecutingRule] = useState<DiagnosticRule | null>(null);
  const [messageApi, holder] = message.useMessage();

  useEffect(() => {
    void api.listRules().then(setRules);
  }, []);
  const numericRuleId = Number(ruleId);
  const rule = useMemo(() => rules.find((item) => item.id === numericRuleId) ?? null, [rules, numericRuleId]);

  useEffect(() => {
    if (!numericRuleId) {
      return;
    }
    void reloadRecords();
  }, [numericRuleId, recordsPage, recordsPageSize]);

  useEffect(() => {
    if (!records.some((item) => item.status === 'RUNNING')) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void reloadRecords(true);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [records, numericRuleId, recordsPage, recordsPageSize]);

  async function reloadRecords(silent = false) {
    if (!numericRuleId) {
      return;
    }
    if (!silent) {
      setRecordsLoading(true);
    }
    try {
      const result = await api.listDiagnosticRecordsPage({ ruleId: numericRuleId, page: recordsPage, pageSize: recordsPageSize });
      setRecords(result.items);
      setRecordsTotal(result.total);
    } finally {
      if (!silent) {
        setRecordsLoading(false);
      }
    }
  }

  async function reload() {
    setRules(await api.listRules());
    await reloadRecords();
  }

  async function submit(payload: Partial<DiagnosticRule>) {
    if (!rule) {
      return;
    }
    await api.updateRule(rule.id, payload);
    messageApi.success('规则已更新');
    setEditorOpen(false);
    await reload();
  }

  async function removeRule() {
    if (!rule) {
      return;
    }
    await api.deleteRule(rule.id);
    messageApi.success('规则已删除');
    navigate('/manager/rules');
  }

  async function startMonitor(targetRule: DiagnosticRule, node: AgentNode, process: ProcessView, maxMatches: number) {
    const record = await api.startRule(targetRule.id, {
      nodeId: node.id,
      pid: process.pid,
      processName: process.displayName,
      processPattern: process.commandLine || process.displayName,
      maxMatches,
    });
    setExecutingRule(null);
    messageApi.success('监控已启动');
    navigate(`/manager/records/${record.id}`);
  }

  async function stopRecord(record: DiagnosticExecutionRecord) {
    const updated = await api.stopDiagnosticRecord(record.id);
    setRecords((current) => current.map((item) => item.id === updated.id ? updated : item));
    messageApi.success('监控已停止');
  }

  if (!rule) {
    return <Empty description="规则不存在" />;
  }

  return (
    <>
      {holder}
      <div className="flex h-full min-h-0 flex-col gap-2">
        <div className="flex items-center justify-between gap-3">
          <div className="dx-section-title text-xl">诊断规则</div>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/manager/rules')}>返回诊断规则</Button>
            <Button icon={<PlayCircleOutlined />} type="primary" onClick={() => setExecutingRule(rule)}>启动监控</Button>
            <Button onClick={() => setEditorOpen(true)}>编辑</Button>
            <Button danger onClick={() => void removeRule()}>删除</Button>
          </Space>
        </div>

        <div className="dx-section min-h-0 flex-1 overflow-auto p-3 shadow-panel">
          <div className="space-y-3">
            <div className="grid gap-3 md:grid-cols-2">
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">规则名称</div>
                <div className="mt-1 font-medium text-slate-900">{rule.name}</div>
              </div>
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">诊断类型</div>
                <div className="mt-1"><Pill tone={rule.type === 'WATCH' ? 'blue' : rule.type === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(rule.type)}</Pill></div>
              </div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">类匹配</div>
              <div className="mt-1 break-all font-mono text-sm text-slate-900">{rule.targetClassPattern}</div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">方法匹配</div>
              <div className="mt-1 break-all font-mono text-sm text-slate-900">{rule.targetMethodPattern}</div>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">条件表达式</div>
                <div className="mt-1 break-all font-mono text-sm text-slate-900">{rule.conditionExpression || '-'}</div>
              </div>
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">输出表达式</div>
                <div className="mt-1 break-all font-mono text-sm text-slate-900">{rule.outputExpression || '-'}</div>
              </div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">默认 Java 进程显示名</div>
              <div className="mt-1 break-all font-mono text-sm text-slate-900">{rule.selectedProcessName || '-'}</div>
            </div>
            <div className="grid gap-3 md:grid-cols-3">
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">对象展开层级</div>
                <div className="mt-1 text-slate-900">{rule.stackDepth ?? '-'}</div>
              </div>
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">匹配次数</div>
                <div className="mt-1 text-slate-900">{rule.maxMatches ?? '-'}</div>
              </div>
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">超时(ms)</div>
                <div className="mt-1 text-slate-900">{rule.executionTimeoutMs ?? '-'}</div>
              </div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">说明</div>
              <div className="mt-1 text-slate-700">{rule.notes || '无'}</div>
            </div>
          </div>
        </div>

        <div className="space-y-2">
          <div className="dx-section-title">诊断记录</div>
          <Table
            className="dx-table"
            rowKey="id"
            loading={recordsLoading}
            dataSource={records}
            locale={{ emptyText: '这条规则还没有诊断记录' }}
            pagination={{
              current: recordsPage,
              pageSize: recordsPageSize,
              total: recordsTotal,
              showSizeChanger: true,
              position: ['bottomRight'],
              onChange: (current, size) => {
                setRecordsPage(current);
                setRecordsPageSize(size);
              },
            }}
            scroll={{ x: 980 }}
            columns={[
              { title: '开始时间', dataIndex: 'executedAt', width: 190, render: (value: string) => formatDateTime(value) },
              { title: '类型', dataIndex: 'type', width: 100, render: (value: DiagnosticExecutionRecord['type']) => <Pill tone={value === 'WATCH' ? 'blue' : value === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(value)}</Pill> },
              { title: '状态', dataIndex: 'status', width: 110, render: (value: DiagnosticExecutionRecord['status']) => <Pill tone={value === 'RUNNING' ? 'blue' : value === 'COMPLETED' ? 'green' : value === 'FAILED' ? 'red' : 'slate'}>{formatDiagnosticExecutionStatus(value)}</Pill> },
              { title: '最近更新', dataIndex: 'updatedAt', width: 190, render: (value: string) => formatDateTime(value) },
              { title: 'PID', dataIndex: 'pid', width: 100, render: (value?: number) => value ?? '-' },
              { title: '触发次数', dataIndex: 'triggerCount', width: 110 },
              { title: '最大耗时(ms)', dataIndex: 'maxCostMs', width: 140, render: (value?: number) => value == null ? '-' : value.toFixed(2) },
              {
                title: '操作',
                width: 180,
                render: (_, record: DiagnosticExecutionRecord) => (
                  <Space size="small" wrap>
                    <Button onClick={() => navigate(`/manager/records/${record.id}`)}>查看</Button>
                    {record.status === 'RUNNING' ? (
                      <Button icon={<StopOutlined />} danger onClick={() => void stopRecord(record)}>
                        停止
                      </Button>
                    ) : null}
                  </Space>
                ),
              },
            ]}
          />
        </div>
      </div>

      <DiagnosticRuleEditor
        open={editorOpen}
        title="编辑诊断规则"
        initialRule={rule}
        onCancel={() => setEditorOpen(false)}
        onSubmit={submit}
      />
      <DiagnosticExecutionModal
        open={!!executingRule}
        ruleName={executingRule?.name}
        defaultProcessName={executingRule?.selectedProcessName}
        defaultMaxMatches={executingRule?.maxMatches}
        nodes={nodes}
        onCancel={() => setExecutingRule(null)}
        onSubmit={(node, process, maxMatches) => startMonitor(executingRule!, node, process, maxMatches)}
      />
    </>
  );
}
