import { Button, Empty, Input, Select, Space, Table } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { AgentNode, DiagnosticExecutionRecord } from '../lib/api';
import {
  api,
  formatDateTime,
  formatDiagnosticExecutionStatus,
  formatDiagnosticType,
} from '../lib/api';
import { Pill } from '../components/Pill';
import { StopMonitorButton } from '../components/StopMonitorButton';

interface Props {
  portal: 'user' | 'manager';
  nodes: AgentNode[];
}

export function DiagnosticRecordsPage({ portal, nodes }: Props) {
  const navigate = useNavigate();
  const [selectedNodeId, setSelectedNodeId] = useState<number | undefined>(nodes[0]?.id);
  const [records, setRecords] = useState<DiagnosticExecutionRecord[]>([]);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'WATCH' | 'TRACE' | 'STACK'>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED'>('ALL');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (nodes.length && !selectedNodeId) {
      setSelectedNodeId(nodes[0].id);
    }
  }, [nodes, selectedNodeId]);

  useEffect(() => {
    if (!selectedNodeId) {
      return;
    }
    void reload(selectedNodeId);
  }, [selectedNodeId, keyword, typeFilter, statusFilter, page, pageSize]);

  useEffect(() => {
    if (!records.some((item) => item.status === 'RUNNING') || !selectedNodeId) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void reload(selectedNodeId);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [records, selectedNodeId]);

  async function reload(nodeId: number) {
    setLoading(true);
    try {
      const result = await api.listDiagnosticRecordsPage({
        nodeId,
        keyword: keyword.trim() || undefined,
        type: typeFilter,
        status: statusFilter,
        page,
        pageSize,
      });
      setRecords(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  }

  async function stopRecord(record: DiagnosticExecutionRecord) {
    const updated = await api.stopDiagnosticRecord(record.id);
    setRecords((current) => current.map((item) => item.id === updated.id ? updated : item));
  }

  async function openRecord(record: DiagnosticExecutionRecord) {
    navigate(`/${portal}/records/${record.id}`);
  }

  if (!nodes.length) {
    return <Empty description="没有可用节点" />;
  }

  return (
    <>
      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Space wrap>
            <Select
              className="w-56"
              value={selectedNodeId}
              options={nodes.map((node) => ({ label: node.nodeName, value: node.id }))}
              onChange={(value) => {
                setSelectedNodeId(value);
                setPage(1);
              }}
            />
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
            <Select
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value);
                setPage(1);
              }}
              options={[
                { label: '全部状态', value: 'ALL' },
                { label: '监控中', value: 'RUNNING' },
                { label: '已完成', value: 'COMPLETED' },
                { label: '已停止', value: 'STOPPED' },
                { label: '失败', value: 'FAILED' },
              ]}
              className="w-36"
            />
            <Input value={keyword} onChange={(event) => {
              setKeyword(event.target.value);
              setPage(1);
            }} placeholder="规则名 / 命令" className="w-72" />
            <Button onClick={() => {
              setKeyword('');
              setTypeFilter('ALL');
              setStatusFilter('ALL');
              setPage(1);
            }}
            >
              重置
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => selectedNodeId && void reload(selectedNodeId)}>
              刷新
            </Button>
          </Space>
        </div>
        <div className="dx-section p-4 shadow-panel">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="dx-section-title text-xl">诊断记录</div>
          </div>
          <Table
            className="dx-table"
            rowKey="id"
            loading={loading}
            dataSource={records}
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
            scroll={{ x: 1380 }}
            columns={[
              { title: 'ID', dataIndex: 'id', width: 80 },
              { title: '规则', dataIndex: 'ruleName', width: 180 },
              ...(portal === 'manager'
                ? [{
                    title: '用户',
                    width: 140,
                    render: (_: unknown, record: DiagnosticExecutionRecord) => record.ownerDisplayName || record.ownerUsername || '-',
                  }]
                : []),
              {
                title: '节点',
                width: 150,
                render: (_: unknown, record: DiagnosticExecutionRecord) => record.agentNodeName || nodes.find((node) => node.id === record.agentNodeId)?.nodeName || '-',
              },
              {
                title: '进程',
                width: 220,
                render: (_: unknown, record: DiagnosticExecutionRecord) => record.processDisplayName || '-',
              },
              {
                title: '共享',
                width: 110,
                render: (_: unknown, record: DiagnosticExecutionRecord) => record.sharedSession
                  ? <Pill tone="amber">{`${record.subscriberCount} 人`}</Pill>
                  : '-',
              },
              { title: '类型', dataIndex: 'type', width: 100, render: (value: DiagnosticExecutionRecord['type']) => <Pill tone={value === 'WATCH' ? 'blue' : value === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(value)}</Pill> },
              { title: '状态', dataIndex: 'status', width: 110, render: (value: DiagnosticExecutionRecord['status']) => <Pill tone={value === 'RUNNING' ? 'blue' : value === 'COMPLETED' ? 'green' : value === 'FAILED' ? 'red' : 'slate'}>{formatDiagnosticExecutionStatus(value)}</Pill> },
              { title: 'PID', dataIndex: 'pid', width: 100, render: (value?: number) => value ?? '-' },
              { title: '触发次数', dataIndex: 'triggerCount', width: 110 },
              { title: '最大耗时(ms)', dataIndex: 'maxCostMs', width: 140, render: (value?: number) => value == null ? '-' : value.toFixed(2) },
              { title: '开始时间', dataIndex: 'executedAt', width: 190, render: (value: string) => formatDateTime(value) },
              { title: '命令', dataIndex: 'command', ellipsis: true },
              {
                title: '操作',
                width: 180,
                render: (_, record: DiagnosticExecutionRecord) => (
                  <Space size="small" wrap>
                    <Button onClick={() => void openRecord(record)}>
                      查看
                    </Button>
                    {record.status === 'RUNNING' ? (
                      <StopMonitorButton onClick={() => void stopRecord(record)} />
                    ) : null}
                  </Space>
                ),
              },
            ]}
          />
        </div>
      </div>
    </>
  );
}
