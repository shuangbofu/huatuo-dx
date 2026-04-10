import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Empty, Space, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { AgentNode, DiagnosticExecutionRecord } from '../lib/api';
import { api } from '../lib/api';
import { DiagnosticResultPanel } from '../components/DiagnosticResultPanel';
import { Pill } from '../components/Pill';
import { StopMonitorButton } from '../components/StopMonitorButton';

interface Props {
  portal: 'user' | 'manager';
  nodes: AgentNode[];
}

export function DiagnosticRecordDetailPage({ portal, nodes }: Props) {
  const navigate = useNavigate();
  const { recordId } = useParams();
  const [record, setRecord] = useState<DiagnosticExecutionRecord | null>(null);
  const [messageApi, holder] = message.useMessage();

  const backPath = portal === 'manager' ? '/manager/records' : '/user/records';
  const nodeName = useMemo(
    () => nodes.find((item) => item.id === record?.agentNodeId)?.nodeName,
    [nodes, record?.agentNodeId],
  );

  useEffect(() => {
    if (!recordId) {
      return;
    }
    void api.getDiagnosticRecord(Number(recordId)).then(setRecord);
  }, [recordId]);

  useEffect(() => {
    if (!record || record.status !== 'RUNNING') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void api.getDiagnosticRecord(record.id).then(setRecord);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [record]);

  async function stopRecord() {
    if (!record) {
      return;
    }
    const updated = await api.stopDiagnosticRecord(record.id);
    setRecord(updated);
    messageApi.success('监控已停止');
  }

  if (!recordId) {
    return <Empty description="记录不存在" />;
  }

  return (
    <>
      {holder}
      <div className="flex h-full min-h-0 flex-col gap-2">
        <div className="flex items-center justify-between gap-3">
          <div className="dx-section-title text-xl">结果详情</div>
          <Space>
            {record?.status === 'RUNNING' ? (
              <StopMonitorButton onClick={() => void stopRecord()} />
            ) : null}
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(backPath)}>返回诊断记录</Button>
          </Space>
        </div>

        {!record ? (
          <div className="dx-section p-3 shadow-panel">
            <Typography.Text type="secondary">正在加载结果...</Typography.Text>
          </div>
        ) : (
          <div className="dx-section flex min-h-0 flex-1 flex-col overflow-hidden p-3 shadow-panel">
            {record.sharedSession ? (
              <div className="mb-3 flex items-center gap-2 border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                <Pill tone="amber">{`共享监控 · ${record.subscriberCount} 人`}</Pill>
                <span>当前记录正在复用同一个底层监控会话。</span>
              </div>
            ) : null}
            <div className="grid gap-2 border-b border-slate-200 pb-3 md:grid-cols-[220px_220px_260px_minmax(0,1fr)]">
              <div className="grid grid-cols-[48px_minmax(0,1fr)] items-start gap-2">
                <div className="pt-0.5 text-xs tracking-[0.08em] text-slate-500">规则</div>
                <div className="text-sm font-medium text-slate-900">{record.ruleName}</div>
              </div>
              <div className="grid grid-cols-[48px_minmax(0,1fr)] items-start gap-2">
                <div className="pt-0.5 text-xs tracking-[0.08em] text-slate-500">节点</div>
                <div className="text-sm font-medium text-slate-900">{record.agentNodeName || nodeName || record.agentNodeId}</div>
              </div>
              <div className="grid grid-cols-[48px_minmax(0,1fr)] items-start gap-2">
                <div className="pt-0.5 text-xs tracking-[0.08em] text-slate-500">进程</div>
                <div className="text-sm font-medium text-slate-900 break-all">{record.processDisplayName || (record.pid ? `PID ${record.pid}` : '-')}</div>
              </div>
              <div className="grid min-w-0 grid-cols-[72px_minmax(0,1fr)] items-start gap-2">
                <div className="pt-0.5 text-xs tracking-[0.08em] text-slate-500">监控命令</div>
                <Typography.Text code className="!block !min-w-0 !break-all">
                  {record.command}
                </Typography.Text>
              </div>
            </div>
            <div className="mt-3 min-h-0 flex-1 overflow-hidden">
              <DiagnosticResultPanel record={record} />
            </div>
          </div>
        )}
      </div>
    </>
  );
}
