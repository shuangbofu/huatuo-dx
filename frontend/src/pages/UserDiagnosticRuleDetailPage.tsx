import { ArrowLeftOutlined, PlayCircleOutlined, StopOutlined } from '@ant-design/icons';
import { Button, Empty, Popconfirm, Space, Table, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { AgentNode, DiagnosticExecutionRecord, DiagnosticRule, ProcessView } from '../lib/api';
import {
  api,
  formatDateTime,
  formatDiagnosticExecutionStatus,
  formatDiagnosticType,
} from '../lib/api';
import { DiagnosticExecutionModal } from '../components/DiagnosticExecutionModal';
import { DiagnosticRuleEditor } from '../components/DiagnosticRuleEditor';
import { Pill } from '../components/Pill';

interface Props {
  nodes: AgentNode[];
}

export function UserDiagnosticRuleDetailPage({ nodes }: Props) {
  const navigate = useNavigate();
  const { ruleId } = useParams();
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

  useEffect(() => {
    void api.listRules().then((data) => setRules(data.filter((item) => item.enabled)));
  }, []);
  const numericRuleId = Number(ruleId);

  const selectedRule = useMemo(
    () => rules.find((item) => item.id === numericRuleId) ?? null,
    [rules, numericRuleId],
  );

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

  async function start(rule: DiagnosticRule, node: AgentNode, process: ProcessView, maxMatches: number) {
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

  async function stopRecord(record: DiagnosticExecutionRecord) {
    const updated = await api.stopDiagnosticRecord(record.id);
    setRecords((current) => current.map((item) => item.id === updated.id ? updated : item));
    messageApi.success('监控已停止');
  }

  async function submit(payload: Partial<DiagnosticRule>) {
    if (!selectedRule) {
      return;
    }
    await api.updateRule(selectedRule.id, payload);
    setEditorOpen(false);
    messageApi.success('规则已更新');
    const nextRules = await api.listRules();
    setRules(nextRules.filter((item) => item.enabled));
    await reloadRecords();
  }

  async function removeRule() {
    if (!selectedRule) {
      return;
    }
    await api.deleteRule(selectedRule.id);
    messageApi.success('规则已删除');
    navigate('/user/diagnostics');
  }

  if (!selectedRule) {
    return <Empty description="规则不存在" />;
  }

  return (
    <>
      {holder}
      <div className="flex h-full min-h-0 flex-col gap-2">
        <div className="flex items-center justify-between gap-3">
          <div className="dx-section-title text-xl">诊断规则</div>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/user/diagnostics')}>返回诊断规则</Button>
            <Button onClick={() => setEditorOpen(true)}>编辑</Button>
            <Popconfirm
              title="确认删除这条规则？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => void removeRule()}
            >
              <Button danger>删除</Button>
            </Popconfirm>
            <Button icon={<PlayCircleOutlined />} type="primary" loading={monitoring} onClick={() => setExecutingRule(selectedRule)}>
              启动监控
            </Button>
          </Space>
        </div>

        <div className="dx-section min-h-0 flex-1 overflow-auto p-3 shadow-panel">
          <div className="space-y-3">
            <div className="grid gap-3 md:grid-cols-2">
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">规则名称</div>
                <div className="mt-1 font-medium text-slate-900">{selectedRule.name}</div>
              </div>
              <div className="border border-slate-200 bg-white px-3 py-2">
                <div className="text-xs text-slate-500">诊断类型</div>
                <div className="mt-1"><Pill tone={selectedRule.type === 'WATCH' ? 'blue' : selectedRule.type === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(selectedRule.type)}</Pill></div>
              </div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">类匹配</div>
              <div className="mt-1 break-all font-mono text-sm text-slate-900">{selectedRule.targetClassPattern}</div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">方法匹配</div>
              <div className="mt-1 break-all font-mono text-sm text-slate-900">{selectedRule.targetMethodPattern}</div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">默认 Java 进程显示名</div>
              <div className="mt-1 break-all font-mono text-sm text-slate-900">{selectedRule.selectedProcessName || '-'}</div>
            </div>
            <div className="border border-slate-200 bg-white px-3 py-2">
              <div className="text-xs text-slate-500">备注</div>
              <div className="mt-1 text-sm text-slate-700">{selectedRule.notes || '-'}</div>
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
                    <Button onClick={() => navigate(`/user/records/${record.id}`)}>查看</Button>
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
        initialRule={selectedRule}
        onCancel={() => setEditorOpen(false)}
        onSubmit={submit}
      />
    </>
  );
}
