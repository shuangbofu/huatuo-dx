import { Button, InputNumber, Modal, Select, Space, Spin, message } from 'antd';
import { useEffect, useState } from 'react';
import type { AgentNode, ProcessView } from '../lib/api';
import { api } from '../lib/api';

interface DiagnosticExecutionModalProps {
  open: boolean;
  ruleName?: string;
  defaultProcessName?: string;
  defaultMaxMatches?: number;
  nodes: AgentNode[];
  loading?: boolean;
  onCancel: () => void;
  onSubmit: (node: AgentNode, process: ProcessView, maxMatches: number) => Promise<void>;
}

export function DiagnosticExecutionModal({
  open,
  ruleName,
  defaultProcessName,
  defaultMaxMatches,
  nodes,
  loading = false,
  onCancel,
  onSubmit,
}: DiagnosticExecutionModalProps) {
  const [selectedNodeId, setSelectedNodeId] = useState<number>();
  const [processes, setProcesses] = useState<ProcessView[]>([]);
  const [selectedPid, setSelectedPid] = useState<string>();
  const [maxMatches, setMaxMatches] = useState<number>(defaultMaxMatches ?? 5);
  const [submitting, setSubmitting] = useState(false);
  const [processLoading, setProcessLoading] = useState(false);
  const [messageApi, holder] = message.useMessage();

  useEffect(() => {
    if (!open) {
      return;
    }
    setSelectedNodeId(nodes[0]?.id);
    setMaxMatches(defaultMaxMatches ?? 5);
  }, [open, nodes, defaultMaxMatches]);

  useEffect(() => {
    if (!open || !selectedNodeId) {
      setProcesses([]);
      setSelectedPid(undefined);
      return;
    }
    setProcessLoading(true);
    void api.getProcesses(selectedNodeId)
      .then((data) => {
        setProcesses(data);
        const preferred = defaultProcessName
          ? data.find((item) => item.displayName === defaultProcessName)
          : undefined;
        setSelectedPid(preferred ? String(preferred.pid) : data[0] ? String(data[0].pid) : undefined);
      })
      .finally(() => setProcessLoading(false));
  }, [open, selectedNodeId, defaultProcessName]);

  async function handleSubmit() {
    const selectedNode = nodes.find((item) => item.id === selectedNodeId);
    if (!selectedNode) {
      messageApi.warning('请选择一个目标节点');
      return;
    }
    const selectedProcess = processes.find((item) => String(item.pid) === selectedPid);
    if (!selectedProcess) {
      messageApi.warning('请选择一个目标 Java 进程');
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit(selectedNode, selectedProcess, maxMatches > 0 ? maxMatches : 1);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      {holder}
      <Modal
        className="dx-modal"
        open={open}
        onCancel={onCancel}
        width={720}
        title={ruleName ? `启动监控: ${ruleName}` : '启动监控'}
        footer={
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" loading={submitting || loading} onClick={() => void handleSubmit()}>
              启动监控
            </Button>
          </Space>
        }
      >
        <div className="space-y-3">
          <div className="border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
            请选择本次执行的目标节点和 Java 进程。
          </div>
          <div className="space-y-1">
            <div className="text-xs font-medium tracking-[0.08em] text-slate-500">目标节点</div>
          <Select
            className="w-full"
            value={selectedNodeId}
            options={nodes.map((node) => ({
              value: node.id,
              label: (
                <div className="flex items-center gap-2">
                  <span className="inline-flex border border-slate-300 bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                    节点
                  </span>
                  <span className="font-medium text-slate-900">{node.nodeName}</span>
                  <span className="text-slate-500">{node.host}:{node.port}</span>
                </div>
              ),
            }))}
            placeholder="请选择本次执行目标节点"
            onChange={setSelectedNodeId}
          />
          </div>
          {processLoading ? <div className="flex justify-center py-6"><Spin /></div> : null}
          <div className="space-y-1">
            <div className="text-xs font-medium tracking-[0.08em] text-slate-500">Java 进程</div>
          <Select
            showSearch
            className="w-full"
            value={selectedPid}
            optionFilterProp="label"
            options={processes.map((process) => ({
              value: String(process.pid),
              searchLabel: `${process.pid} ${process.displayName}`,
              label: (
                <div className="flex items-center gap-2">
                  <span className="inline-flex border border-slate-300 bg-slate-100 px-2 py-0.5 font-mono text-xs text-slate-700">
                    PID {process.pid}
                  </span>
                  <span className="truncate text-slate-900">{process.displayName}</span>
                </div>
              ),
            }))}
            placeholder="请选择本次执行目标进程"
            loading={processLoading || loading}
            onChange={setSelectedPid}
            filterOption={(input, option) =>
              String(option?.searchLabel ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />
          </div>
          <div className="space-y-1">
            <div className="text-xs font-medium tracking-[0.08em] text-slate-500">本次匹配次数</div>
            <InputNumber
              className="w-full"
              min={1}
              max={50}
              value={maxMatches}
              onChange={(value) => setMaxMatches(Number(value) || 1)}
            />
          </div>
        </div>
      </Modal>
    </>
  );
}
