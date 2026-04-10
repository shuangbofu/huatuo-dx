import { Modal, Space, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import type { DiagnosticExecutionRecord } from '../lib/api';
import { api } from '../lib/api';
import { DiagnosticResultPanel } from './DiagnosticResultPanel';
import { StopMonitorButton } from './StopMonitorButton';

interface Props {
  open: boolean;
  recordId?: number;
  initialRecord?: DiagnosticExecutionRecord | null;
  onClose: () => void;
}

export function DiagnosticRecordViewerModal({ open, recordId, initialRecord, onClose }: Props) {
  const [record, setRecord] = useState<DiagnosticExecutionRecord | null>(initialRecord ?? null);
  const [messageApi, holder] = message.useMessage();

  useEffect(() => {
    setRecord(initialRecord ?? null);
  }, [initialRecord]);

  useEffect(() => {
    if (!open || !recordId) {
      return;
    }
    void api.getDiagnosticRecord(recordId).then(setRecord);
  }, [open, recordId]);

  useEffect(() => {
    if (!open || !recordId || !record || record.status !== 'RUNNING') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void api.getDiagnosticRecord(recordId).then(setRecord);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [open, recordId, record]);

  async function stopRecord() {
    if (!record) {
      return;
    }
    const updated = await api.stopDiagnosticRecord(record.id);
    setRecord(updated);
    messageApi.success('监控已停止');
  }

  return (
    <>
      {holder}
      <Modal
        className="dx-modal"
        open={open}
        onCancel={onClose}
        footer={null}
        width={960}
        title={record?.ruleName || '监控结果'}
      >
        {record ? (
          <Space direction="vertical" size={12} className="w-full">
            <div className="flex items-center justify-between gap-3">
              <Typography.Text>
                监控命令: <Typography.Text code>{record.command}</Typography.Text>
              </Typography.Text>
              {record.status === 'RUNNING' ? (
                <StopMonitorButton onClick={() => void stopRecord()} />
              ) : null}
            </div>
            <DiagnosticResultPanel record={record} />
          </Space>
        ) : null}
      </Modal>
    </>
  );
}
