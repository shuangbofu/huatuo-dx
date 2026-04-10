import type { DiagnosticExecutionRecord } from '../lib/api';
import type { ReactNode } from 'react';
import { Button, Tabs } from 'antd';
import { useState } from 'react';
import {
  formatDateTime,
  formatDiagnosticExecutionStatus,
  formatDiagnosticType,
} from '../lib/api';
import { Pill } from './Pill';

interface Props {
  record: DiagnosticExecutionRecord;
}

export function DiagnosticResultPanel({ record }: Props) {
  const hasEvents = record.triggerEvents.length > 0;
  const [showRawOutput, setShowRawOutput] = useState(false);

  return (
    <div className="flex h-full min-h-0 flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
        <Pill tone={record.type === 'WATCH' ? 'blue' : record.type === 'TRACE' ? 'amber' : 'green'}>{formatDiagnosticType(record.type)}</Pill>
        <Pill tone={record.status === 'RUNNING' ? 'blue' : record.status === 'COMPLETED' ? 'green' : record.status === 'FAILED' ? 'red' : 'slate'}>
          {formatDiagnosticExecutionStatus(record.status)}
        </Pill>
        <span className="text-xs text-slate-500">PID {record.pid ?? '-'}</span>
      </div>

      <div className="grid gap-x-5 gap-y-1.5 border border-slate-200 bg-white px-3 py-2.5 md:grid-cols-2 xl:grid-cols-[120px_140px_236px_236px]">
        <InlineMeta label="触发次数" value={String(record.triggerCount)} strong />
        <InlineMeta label="最大耗时" value={record.maxCostMs != null ? `${record.maxCostMs.toFixed(2)} ms` : '-'} strong noWrap labelWidth={44} />
        <InlineMeta label="开始时间" value={formatDateTime(record.executedAt)} noWrap noEllipsis labelWidth={44} />
        <InlineMeta label="最近更新" value={formatDateTime(record.updatedAt)} noWrap noEllipsis labelWidth={44} />
      </div>

      {hasEvents ? (
        record.type === 'TRACE' ? (
          <Tabs
            className="dx-diagnostic-tabs min-h-0 flex-1"
            items={record.triggerEvents.map((event) => ({
              key: String(event.sequence),
              label: (
                <div className="flex items-center gap-2">
                  <span>{`第 ${event.sequence} 次`}</span>
                  {event.costMs != null ? <Pill tone={event.costMs >= 100 ? 'red' : event.costMs >= 30 ? 'amber' : 'blue'}>{event.costMs.toFixed(2)} ms</Pill> : null}
                </div>
              ),
              children: <TraceEventView event={event} />,
            }))}
          />
        ) : record.type === 'STACK' ? (
          <Tabs
            className="dx-diagnostic-tabs min-h-0 flex-1"
            items={record.triggerEvents.map((event) => ({
              key: String(event.sequence),
              label: (
                <div className="flex items-center gap-2">
                  <span>{`第 ${event.sequence} 次`}</span>
                  {event.costMs != null ? <Pill tone={event.costMs >= 100 ? 'red' : event.costMs >= 30 ? 'amber' : 'green'}>{event.costMs.toFixed(2)} ms</Pill> : null}
                </div>
              ),
              children: <StackEventView event={event} />,
            }))}
          />
        ) : (
          <Tabs
            className="dx-diagnostic-tabs min-h-0 flex-1"
            items={record.triggerEvents.map((event) => ({
              key: String(event.sequence),
              label: (
                <div className="flex items-center gap-2">
                  <span>{`第 ${event.sequence} 次`}</span>
                  {event.costMs != null ? <Pill tone="amber">{event.costMs.toFixed(2)} ms</Pill> : null}
                </div>
              ),
              children: <WatchEventView event={event} />,
            }))}
          />
        )
      ) : (
        <div className="space-y-3">
          <div className="border border-slate-200 bg-white px-3 py-3">
            <div className="mb-2 flex items-center justify-between gap-3">
              <div className="text-sm font-medium text-slate-900">
                {showRawOutput
                  ? '原始输出'
                  : record.status === 'RUNNING'
                    ? '监控已经启动，当前还没有捕获到触发结果'
                    : record.errorMessage
                      ? '本次监控执行异常'
                      : '本次监控没有整理出可展示的触发结果'}
              </div>
              {(record.output || record.errorMessage) ? (
                <Button size="small" onClick={() => setShowRawOutput((value) => !value)}>
                  {showRawOutput ? '查看状态说明' : '查看原始输出'}
                </Button>
              ) : null}
            </div>
            {showRawOutput ? (
              <div className="whitespace-pre-wrap break-words bg-slate-50 px-3 py-3 text-sm leading-6 text-slate-800">
                {record.output || record.errorMessage}
              </div>
            ) : (
              <div className="text-sm leading-6 text-slate-600">
                {record.status === 'RUNNING'
                  ? '你现在应该去触发实际业务请求。结果一旦进来，这里会自动刷新。'
                  : record.errorMessage
                    ? record.errorMessage
                    : '如果你怀疑已经触发过，但这里还是空的，那问题在后端监控链或结果解析链，不在这个页面。'}
              </div>
            )}
          </div>
          <div className="border border-slate-200 bg-white px-3 py-3">
            <div className="text-xs text-slate-500">监控命令</div>
            <div className="mt-2 break-all font-mono text-sm text-slate-800">{record.command}</div>
          </div>
        </div>
      )}
    </div>
  );
}

function WatchEventView({
  event,
}: {
  event: DiagnosticExecutionRecord['triggerEvents'][number];
}) {
  const [showRawOutput, setShowRawOutput] = useState(false);

  return (
    <div className="mt-3 space-y-3">
      <div className="grid gap-x-5 gap-y-1.5 border border-slate-200 bg-white px-3 py-2.5 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_120px_236px_132px]">
        <InlineMeta label="方法" value={event.methodName || '-'} mono strong labelWidth={48} />
        <InlineMeta label="位置" value={event.location || '-'} noWrap labelWidth={36} />
        <InlineMeta label="时间" value={event.timestamp || '-'} noWrap noEllipsis labelWidth={36} />
        <InlineMeta label="耗时" value={event.costMs != null ? `${event.costMs.toFixed(2)} ms` : '-'} strong noWrap noEllipsis labelWidth={36} />
      </div>
      <div className="border border-slate-200 bg-slate-50 px-3 py-3">
        <div className="mb-2 flex items-center justify-between gap-3">
          <div className="text-xs tracking-[0.08em] text-slate-500">{showRawOutput ? '原始终端输出' : '本次命中结果'}</div>
          <Button size="small" onClick={() => setShowRawOutput((value) => !value)}>
            {showRawOutput ? '查看命中结果' : '查看原始终端输出'}
          </Button>
        </div>
        {showRawOutput ? (
          <div className="whitespace-pre-wrap break-words bg-white px-3 py-3 text-sm leading-6 text-slate-800">
            {event.rawContent}
          </div>
        ) : (
          <div className="whitespace-pre-wrap break-words font-mono text-sm leading-6 text-slate-800">
            {event.watchBody || event.rawContent}
          </div>
        )}
      </div>
    </div>
  );
}

function TraceEventView({
  event,
}: {
  event: DiagnosticExecutionRecord['triggerEvents'][number];
}) {
  const [showRawOutput, setShowRawOutput] = useState(false);
  const summaryNode = event.traceNodes.find((node) => node.depth === 0);
  const detailNodes = summaryNode ? event.traceNodes.filter((node) => node !== summaryNode) : event.traceNodes;
  const eventMaxCostMs = Math.max(
    event.costMs ?? 0,
    ...event.traceNodes.map((node) => node.costMs ?? parseTraceNodeLabel(node.label)?.costMs ?? 0),
  );

  return (
    <div className="mt-3 space-y-3">
      <div className="grid gap-x-5 gap-y-1.5 border border-slate-200 bg-white px-3 py-2.5 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_236px_132px_132px]">
        <InlineMeta label="线程" value={event.threadName || '-'} />
        <InlineMeta label="时间" value={event.timestamp || '-'} noWrap noEllipsis labelWidth={36} />
        <InlineMeta label="总耗时" value={event.costMs != null ? `${event.costMs.toFixed(2)} ms` : '-'} strong noWrap noEllipsis labelWidth={52} />
        <InlineMeta label="链路节点" value={String(event.traceNodes.length)} strong labelWidth={52} />
      </div>
      {summaryNode ? (
        <div className="border border-slate-200 bg-white px-3 py-3">
          <div className="mb-2 text-xs tracking-[0.08em] text-slate-500">总览</div>
          <TraceNodeRow node={summaryNode} maxCostMs={eventMaxCostMs} />
        </div>
      ) : null}
      <div className="border border-slate-200 bg-slate-50 px-3 py-3">
        <div className="mb-2 flex items-center justify-between gap-3">
          <div className="text-xs tracking-[0.08em] text-slate-500">{showRawOutput ? '原始终端输出' : '链路耗时明细'}</div>
          <Button size="small" onClick={() => setShowRawOutput((value) => !value)}>
            {showRawOutput ? '查看链路耗时明细' : '查看原始终端输出'}
          </Button>
        </div>
        {showRawOutput ? (
          <div className="whitespace-pre-wrap break-words bg-white px-3 py-3 font-mono text-sm leading-6 text-slate-800">
            {event.rawContent}
          </div>
        ) : detailNodes.length ? (
          <div className="space-y-2">
            {detailNodes.map((node, index) => (
              <div key={`${index}-${node.label}`} className="min-w-0">
                <div className="ml-0" style={{ paddingLeft: `${node.depth * 18}px` }}>
                  <TraceNodeRow node={node} maxCostMs={eventMaxCostMs} />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm leading-6 text-slate-700">{event.summary || '当前链路结果还没有解析出明细节点。'}</div>
        )}
      </div>
    </div>
  );
}

function StackEventView({
  event,
}: {
  event: DiagnosticExecutionRecord['triggerEvents'][number];
}) {
  const [showRawOutput, setShowRawOutput] = useState(false);
  const stackLines = event.traceNodes.length
    ? event.traceNodes
    : event.rawContent
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('ts=') && !line.startsWith('thread_name=') && !line.startsWith('method=') && !line.startsWith('Affect('))
      .map((line) => ({ depth: 0, costMs: undefined, label: line }));

  return (
    <div className="mt-3 space-y-3">
      <div className="grid gap-x-5 gap-y-1.5 border border-slate-200 bg-white px-3 py-2.5 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_236px_132px]">
        <InlineMeta label="线程" value={event.threadName || '-'} />
        <InlineMeta label="时间" value={event.timestamp || '-'} noWrap noEllipsis labelWidth={36} />
        <InlineMeta label="耗时" value={event.costMs != null ? `${event.costMs.toFixed(2)} ms` : '-'} strong noWrap noEllipsis labelWidth={36} />
      </div>
      <div className="border border-slate-200 bg-slate-50 px-3 py-3">
        <div className="mb-2 flex items-center justify-between gap-3">
          <div className="text-xs tracking-[0.08em] text-slate-500">{showRawOutput ? '原始终端输出' : '调用堆栈'}</div>
          <Button size="small" onClick={() => setShowRawOutput((value) => !value)}>
            {showRawOutput ? '查看调用堆栈' : '查看原始终端输出'}
          </Button>
        </div>
        {showRawOutput ? (
          <div className="whitespace-pre-wrap break-words bg-white px-3 py-3 text-sm leading-6 text-slate-800">
            {event.rawContent}
          </div>
        ) : (
          <div className="overflow-hidden border border-slate-200 bg-white">
            {stackLines.map((node, index) => {
              const parsed = parseStackNodeLabel(node.label);
              if (!parsed) {
                return (
                  <div key={`${index}-${node.label}`} className="border-t border-slate-200 px-3 py-1.5 font-mono text-sm leading-5 text-slate-800 first:border-t-0">
                    {node.label}
                  </div>
                );
              }
              if (parsed.kind === 'header') {
                return (
                  <div key={`${index}-${node.label}`} className="border-t border-emerald-200 bg-emerald-50 px-3 py-1.5 first:border-t-0">
                    <div className="text-xs tracking-[0.08em] text-emerald-700">入口方法</div>
                    <div className="mt-1 break-all font-mono text-sm text-emerald-900">{parsed.text}</div>
                  </div>
                );
              }
              if (parsed.kind === 'cause') {
                return (
                  <div key={`${index}-${node.label}`} className="border-t border-amber-200 bg-amber-50 px-3 py-1.5 first:border-t-0">
                    <div className="break-all text-sm font-medium text-amber-900">{parsed.text}</div>
                  </div>
                );
              }
              if (parsed.kind === 'fold') {
                return (
                  <div key={`${index}-${node.label}`} className="border-t border-slate-200 px-3 py-1 text-xs text-slate-500 first:border-t-0">
                    {parsed.text}
                  </div>
                );
              }
              return (
                <div
                  key={`${index}-${node.label}`}
                  className="grid grid-cols-[28px_minmax(0,1fr)_auto] items-start gap-3 border-t border-slate-200 px-3 py-1.5 first:border-t-0"
                  style={{ marginLeft: `${node.depth * 14}px` }}
                >
                  <div className="pt-0.5 text-xs font-medium text-slate-400">{index + 1}</div>
                  <div className="min-w-0">
                    <div className="break-all font-mono text-sm text-slate-900">{parsed.method}</div>
                  </div>
                  <div className="whitespace-nowrap text-xs text-slate-500">{parsed.location || '-'}</div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function parseStackNodeLabel(label: string):
  | { kind: 'frame'; method: string; location?: string }
  | { kind: 'header'; text: string }
  | { kind: 'cause'; text: string }
  | { kind: 'fold'; text: string }
  | null {
  const trimmed = label.trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.startsWith('method=')) {
    return { kind: 'header', text: trimmed.replace(/^method=/, '').trim() || trimmed };
  }
  if (trimmed.startsWith('Caused by:') || trimmed.startsWith('Suppressed:')) {
    return { kind: 'cause', text: trimmed };
  }
  if (trimmed.startsWith('... ')) {
    return { kind: 'fold', text: trimmed };
  }
  const frameMatch = trimmed.match(/^at\s+(.+?)\(([^()]*)\)$/);
  if (frameMatch) {
    return {
      kind: 'frame',
      method: frameMatch[1].trim(),
      location: frameMatch[2].trim(),
    };
  }
  return {
    kind: 'frame',
    method: trimmed,
    location: undefined,
  };
}

function TraceNodeRow({
  node,
  maxCostMs,
}: {
  node: DiagnosticExecutionRecord['triggerEvents'][number]['traceNodes'][number];
  maxCostMs: number;
}) {
  const parsed = parseTraceNodeLabel(node.label);

  if (!parsed) {
    const fallbackCost = node.costMs;
    return (
      <div className="flex flex-wrap items-center gap-2">
        {fallbackCost != null ? <Pill tone={fallbackCost >= 100 ? 'red' : fallbackCost >= 30 ? 'amber' : 'blue'}>{fallbackCost.toFixed(2)} ms</Pill> : null}
        <span className="break-all font-mono text-sm text-slate-800">{node.label}</span>
      </div>
    );
  }

  const parsedNode = parsed;
  const cost = node.costMs ?? parsedNode.costMs;
  const costTone = cost != null ? (cost >= 100 ? 'red' : cost >= 30 ? 'amber' : cost >= 1 ? 'blue' : 'green') : 'slate';
  const widthPercent = cost != null && maxCostMs > 0
    ? Math.max(0.5, Math.min((cost / maxCostMs) * 100, 100))
    : 0.5;

  return (
    <div className="space-y-2">
      <div className="flex items-start gap-3">
        <div className="shrink-0 font-mono text-sm text-slate-700">
          {cost != null ? (
            <span className={costTone === 'red' ? 'text-rose-600' : costTone === 'amber' ? 'text-amber-600' : costTone === 'blue' ? 'text-sky-600' : 'text-emerald-600'}>
              {cost.toFixed(5)} ms
            </span>
          ) : '-'}
        </div>
        <div className="min-w-0 flex-1 break-all font-mono text-sm text-slate-900">
          {parsedNode.lineInfo ? <span className="mr-2 text-slate-500">{parsedNode.lineInfo}</span> : null}
          <span>{parsedNode.method}</span>
        </div>
      </div>
      <div className="grid grid-cols-[56px_minmax(0,1fr)] items-center gap-3">
        <div className="font-mono text-[11px] text-slate-500">{parsedNode.percent.toFixed(2)}%</div>
        <div className="h-1.5 overflow-hidden bg-slate-200">
          <div
            className={`${costTone === 'red' ? 'bg-rose-500' : costTone === 'amber' ? 'bg-amber-500' : costTone === 'blue' ? 'bg-sky-500' : 'bg-emerald-500'} h-full`}
            style={{ width: `${widthPercent}%` }}
          />
        </div>
      </div>
    </div>
  );
}

const TRACE_NUMBER_PATTERN = '([0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)';

function parseTraceNodeLabel(label: string): { percent: number; costMs?: number; method: string; lineInfo?: string } | null {
  const simpleMatch = label.match(new RegExp(`^\\[\\s*${TRACE_NUMBER_PATTERN}%\\s+${TRACE_NUMBER_PATTERN}ms\\s*\\]\\s*(.+)$`));
  if (simpleMatch) {
    const percent = Number(simpleMatch[1]);
    const costMs = Number(simpleMatch[2]);
    const remainder = simpleMatch[3].trim();
    const lineMatch = remainder.match(/^(.*?)(\s+#\d+)?$/);
    const method = (lineMatch?.[1] ?? remainder).trim();
    const lineInfo = lineMatch?.[2]?.trim();
    return {
      percent: Number.isFinite(percent) ? percent : 0,
      costMs: Number.isFinite(costMs) ? costMs : undefined,
      method,
      lineInfo,
    };
  }

  const statsMatch = label.match(new RegExp(`^\\[\\s*${TRACE_NUMBER_PATTERN}%\\s+min=${TRACE_NUMBER_PATTERN}ms,max=${TRACE_NUMBER_PATTERN}ms,total=${TRACE_NUMBER_PATTERN}ms,count=(\\d+)\\s*\\]\\s*(.+)$`));
  if (!statsMatch) {
    return null;
  }
  const percent = Number(statsMatch[1]);
  const totalCostMs = Number(statsMatch[4]);
  const remainder = statsMatch[6].trim();
  const lineMatch = remainder.match(/^(.*?)(\s+#\d+)?$/);
  const method = (lineMatch?.[1] ?? remainder).trim();
  const lineInfo = lineMatch?.[2]?.trim();
  return {
    percent: Number.isFinite(percent) ? percent : 0,
    costMs: Number.isFinite(totalCostMs) ? totalCostMs : undefined,
    method,
    lineInfo,
  };
}

function InlineMeta({
  label,
  value,
  mono = false,
  strong = false,
  labelWidth = 64,
  noWrap = false,
  noEllipsis = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
  strong?: boolean;
  labelWidth?: number;
  noWrap?: boolean;
  noEllipsis?: boolean;
}) {
  return (
    <div className="grid items-start gap-2" style={{ gridTemplateColumns: `${labelWidth}px minmax(0, 1fr)` }}>
      <div className="pt-0.5 text-xs tracking-[0.08em] text-slate-500">{label}</div>
      <div className={`min-w-0 text-sm text-slate-900 ${strong ? 'font-medium' : ''} ${mono ? 'break-all font-mono' : ''} ${noWrap ? (noEllipsis ? 'whitespace-nowrap' : 'overflow-hidden text-ellipsis whitespace-nowrap') : ''}`}>{value}</div>
    </div>
  );
}
