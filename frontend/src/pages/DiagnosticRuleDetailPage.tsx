import { ArrowLeftOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { Button, Empty, Popconfirm, Space, Table, Tabs, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { DiagnosticExecutionModal } from '../components/DiagnosticExecutionModal';
import { DiagnosticRuleEditor } from '../components/DiagnosticRuleEditor';
import { Pill } from '../components/Pill';
import { StopMonitorButton } from '../components/StopMonitorButton';
import type { AgentNode, DiagnosticExecutionRecord, DiagnosticRule, ProcessView } from '../lib/api';
import { api, formatDateTime, formatDiagnosticExecutionStatus, formatDiagnosticType, formatJavaClassName } from '../lib/api';

type Portal = 'manager' | 'user';

interface Props {
  portal: Portal;
  nodes: AgentNode[];
}

export function DiagnosticRuleDetailPage({ portal, nodes }: Props) {
  const navigate = useNavigate();
  const { ruleId } = useParams();
  const isManager = portal === 'manager';
  const basePath = isManager ? '/manager/rules' : '/user/diagnostics';
  const [rules, setRules] = useState<DiagnosticRule[]>([]);
  const [records, setRecords] = useState<DiagnosticExecutionRecord[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [recordsPage, setRecordsPage] = useState(1);
  const [recordsPageSize, setRecordsPageSize] = useState(10);
  const [recordsTotal, setRecordsTotal] = useState(0);
  const [executingRule, setExecutingRule] = useState<DiagnosticRule | null>(null);
  const [monitoring, setMonitoring] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [messageApi, holder] = message.useMessage();
  const numericRuleId = Number(ruleId);

  useEffect(() => {
    void api.listRules().then(setRules);
  }, []);

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
    setEditorOpen(false);
    messageApi.success('规则已更新');
    await reload();
  }

  async function removeRule() {
    if (!rule) {
      return;
    }
    await api.deleteRule(rule.id);
    messageApi.success('规则已删除');
    navigate(basePath);
  }

  async function start(ruleValue: DiagnosticRule, node: AgentNode, process: ProcessView, maxMatches: number) {
    setMonitoring(true);
    try {
      const record = await api.startRule(ruleValue.id, {
        nodeId: node.id,
        pid: process.pid,
        processName: process.displayName,
        processPattern: process.commandLine || process.displayName,
        maxMatches,
      });
      setExecutingRule(null);
      messageApi.success('监控已启动');
      navigate(`/${portal}/records/${record.id}`);
    } finally {
      setMonitoring(false);
    }
  }

  async function stopRecord(record: DiagnosticExecutionRecord) {
    const updated = await api.stopDiagnosticRecord(record.id);
    setRecords((current) => current.map((item) => (item.id === updated.id ? updated : item)));
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
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(basePath)}>
              返回诊断规则
            </Button>
            <Button onClick={() => setEditorOpen(true)}>编辑</Button>
            <Popconfirm title="确认删除这条规则？" okText="删除" cancelText="取消" onConfirm={() => void removeRule()}>
              <Button danger>删除</Button>
            </Popconfirm>
            <Button icon={<PlayCircleOutlined />} type="primary" loading={monitoring} onClick={() => setExecutingRule(rule)}>
              启动监控
            </Button>
          </Space>
        </div>

        <div className="dx-section min-h-0 flex-1 overflow-hidden p-3 shadow-panel">
          <Tabs
            className="dx-rule-detail-tabs h-full min-h-0"
            items={[
              {
                key: 'detail',
                label: '规则详情',
                children: (
                  <div className="h-full overflow-auto pr-1">
                    <div className="space-y-2">
                      <div className="grid gap-2 md:grid-cols-[minmax(0,1fr)_180px]">
                        <div className="border border-slate-200 bg-white px-3 py-1.5">
                          <div className="text-xs text-slate-500">规则名称</div>
                          <div className="mt-0.5 font-medium leading-5 text-slate-900">{rule.name}</div>
                        </div>
                        <div className="border border-slate-200 bg-white px-3 py-1.5">
                          <div className="text-xs text-slate-500">诊断类型</div>
                          <div className="mt-0.5">
                            <Pill tone={rule.type === 'WATCH' ? 'blue' : rule.type === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(rule.type)}</Pill>
                          </div>
                        </div>
                      </div>
                      {isManager ? (
                        <div className="grid gap-2 md:grid-cols-[220px_minmax(0,1fr)]">
                          <div className="border border-slate-200 bg-white px-3 py-1.5">
                            <div className="text-xs text-slate-500">用户</div>
                            <div className="mt-0.5 leading-5 text-slate-900">{rule.ownerDisplayName || rule.ownerUsername || '-'}</div>
                          </div>
                          <div className="border border-slate-200 bg-white px-3 py-1.5">
                            <div className="text-xs text-slate-500">默认 Java 进程显示名</div>
                            <div className="mt-0.5 break-all font-mono text-sm leading-5 text-slate-900">{rule.selectedProcessName || '-'}</div>
                          </div>
                        </div>
                      ) : (
                        <div className="border border-slate-200 bg-white px-3 py-1.5">
                          <div className="text-xs text-slate-500">默认 Java 进程显示名</div>
                          <div className="mt-0.5 break-all font-mono text-sm leading-5 text-slate-900">{rule.selectedProcessName || '-'}</div>
                        </div>
                      )}
                      <div className="border border-slate-200 bg-white px-3 py-1.5">
                        <div className="text-xs text-slate-500">类匹配</div>
                        <div className="mt-0.5 break-all font-mono text-sm leading-5 text-slate-900" title={rule.targetClassPattern}>{formatJavaClassName(rule.targetClassPattern)}</div>
                      </div>
                      <div className="border border-slate-200 bg-white px-3 py-1.5">
                        <div className="text-xs text-slate-500">方法匹配</div>
                        <div className="mt-0.5 break-all font-mono text-sm leading-5 text-slate-900">{rule.targetMethodPattern}</div>
                      </div>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 border border-slate-200 bg-white px-3 py-1.5">
                        <CompactMeta label="对象展开层级" value={String(rule.stackDepth ?? '-')} />
                        <CompactMeta label="匹配次数" value={String(rule.maxMatches ?? '-')} />
                        <CompactMeta label="超时(ms)" value={String(rule.executionTimeoutMs ?? '-')} />
                      </div>
                      <div className="grid gap-2 md:grid-cols-2">
                        <div className="border border-slate-200 bg-white px-3 py-1.5">
                          <div className="text-xs text-slate-500">条件表达式</div>
                          <div className="mt-0.5 break-all font-mono text-sm leading-5 text-slate-900">{rule.conditionExpression || '-'}</div>
                        </div>
                        <div className="border border-slate-200 bg-white px-3 py-1.5">
                          <div className="text-xs text-slate-500">输出表达式</div>
                          <div className="mt-0.5 break-all font-mono text-sm leading-5 text-slate-900">{rule.outputExpression || '-'}</div>
                        </div>
                      </div>
                      <div className="border border-slate-200 bg-white px-3 py-1.5">
                        <div className="text-xs text-slate-500">备注</div>
                        <div className="mt-0.5 text-sm leading-5 text-slate-700">{rule.notes || '-'}</div>
                      </div>
                    </div>
                  </div>
                ),
              },
              {
                key: 'records',
                label: '诊断记录',
                children: (
                  <div className="h-full overflow-auto">
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
                      scroll={{ x: isManager ? 1120 : 980 }}
                      columns={[
                        { title: 'ID', dataIndex: 'id', width: 80 },
                        ...(isManager
                          ? [{ title: '用户', width: 140, render: (_: unknown, record: DiagnosticExecutionRecord) => record.ownerDisplayName || record.ownerUsername || '-' }]
                          : []),
                        {
                          title: '类型',
                          dataIndex: 'type',
                          width: 100,
                          render: (value: DiagnosticExecutionRecord['type']) => (
                            <Pill tone={value === 'WATCH' ? 'blue' : value === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(value)}</Pill>
                          ),
                        },
                        {
                          title: '状态',
                          dataIndex: 'status',
                          width: 110,
                          render: (value: DiagnosticExecutionRecord['status']) => (
                            <Pill tone={value === 'RUNNING' ? 'blue' : value === 'COMPLETED' ? 'green' : value === 'FAILED' ? 'red' : 'slate'}>
                              {formatDiagnosticExecutionStatus(value)}
                            </Pill>
                          ),
                        },
                        { title: 'PID', dataIndex: 'pid', width: 100, render: (value?: number) => value ?? '-' },
                        { title: '触发次数', dataIndex: 'triggerCount', width: 110 },
                        { title: '最大耗时(ms)', dataIndex: 'maxCostMs', width: 140, render: (value?: number) => (value == null ? '-' : value.toFixed(2)) },
                        { title: '开始时间', dataIndex: 'executedAt', width: 190, render: (value: string) => formatDateTime(value) },
                        {
                          title: '操作',
                          width: 180,
                          render: (_: unknown, record: DiagnosticExecutionRecord) => (
                            <Space size="small" wrap>
                              <Button onClick={() => navigate(`/${portal}/records/${record.id}`)}>查看</Button>
                              {record.status === 'RUNNING' ? (
                                <StopMonitorButton onClick={() => void stopRecord(record)} />
                              ) : null}
                            </Space>
                          ),
                        },
                      ]}
                    />
                  </div>
                ),
              },
            ]}
          />
        </div>
      </div>

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
      <DiagnosticRuleEditor
        open={editorOpen}
        title="编辑诊断规则"
        initialRule={rule}
        onCancel={() => setEditorOpen(false)}
        onSubmit={submit}
      />
    </>
  );
}

function CompactMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="inline-flex items-center gap-2">
      <span className="text-xs tracking-[0.08em] text-slate-500">{label}</span>
      <span className="text-sm font-medium text-slate-900">{value}</span>
    </div>
  );
}
