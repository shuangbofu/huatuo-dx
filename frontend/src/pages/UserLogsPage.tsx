import { Button, Input, Pagination, Space, Spin, Typography, message } from 'antd';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { LogConsoleCatalog, LogContextResponse, LogQueryItem, LogQueryResponse, LogTailResponse } from '../lib/api';
import { api } from '../lib/api';
import { Pill } from '../components/Pill';

interface UserLogsPageProps {
  fullHeight?: boolean;
}

const TAIL_MODE_AUTO_STOP_MS = 5 * 60 * 1000;

type LogLevelTone = 'error' | 'warn' | 'info' | 'debug' | 'default';

function detectLogLevel(content: string): LogLevelTone {
  const upper = content.toUpperCase();
  if (/\bERROR\b/.test(upper)) {
    return 'error';
  }
  if (/\bWARN(?:ING)?\b/.test(upper)) {
    return 'warn';
  }
  if (/\bINFO\b/.test(upper)) {
    return 'info';
  }
  if (/\bDEBUG\b/.test(upper)) {
    return 'debug';
  }
  return 'default';
}

function levelTextClass(level: LogLevelTone, expanded: boolean, hit?: boolean) {
  if (hit) {
    return 'text-lime-50';
  }
  switch (level) {
    case 'error':
      return 'text-rose-300';
    case 'warn':
      return 'text-amber-300';
    case 'info':
      return 'text-emerald-300';
    case 'debug':
      return 'text-violet-300';
    default:
      return expanded ? 'text-slate-100' : 'text-slate-300';
  }
}

function levelBadgeClass(level: LogLevelTone) {
  switch (level) {
    case 'error':
      return 'text-rose-200 bg-rose-950/60';
    case 'warn':
      return 'text-amber-200 bg-amber-950/50';
    case 'info':
      return 'text-emerald-300 bg-emerald-950/35';
    case 'debug':
      return 'text-violet-200 bg-violet-950/45';
    default:
      return '';
  }
}

function renderLogContent(content: string, expanded: boolean, hit?: boolean) {
  const level = detectLogLevel(content);
  const baseClass = levelTextClass(level, expanded, hit);
  const parts = content.split(/(ERROR|WARN(?:ING)?|INFO|DEBUG)/gi);
  return (
    <pre className={`overflow-auto whitespace-pre-wrap break-all font-mono text-[12px] leading-[1.35rem] ${baseClass}`}>
      {parts.map((part, index) => {
        const upper = part.toUpperCase();
        const isLevel = upper === 'ERROR' || upper === 'WARN' || upper === 'WARNING' || upper === 'INFO' || upper === 'DEBUG';
        if (!isLevel) {
          return <span key={`${part}-${index}`}>{part}</span>;
        }
        const tone = detectLogLevel(part);
        return (
          <span
            key={`${part}-${index}`}
            className={`rounded px-1 py-[1px] font-semibold ${levelBadgeClass(tone)}`}
          >
            {part}
          </span>
        );
      })}
    </pre>
  );
}

export function UserLogsPage({ fullHeight = false }: UserLogsPageProps) {
  const [catalog, setCatalog] = useState<LogConsoleCatalog>();
  const [selectedNodeId, setSelectedNodeId] = useState<number>();
  const [selectedSourceId, setSelectedSourceId] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [queryResult, setQueryResult] = useState<LogQueryResponse>();
  const [loading, setLoading] = useState(true);
  const [querying, setQuerying] = useState(false);
  const [tailMode, setTailMode] = useState(false);
  const [pageSize, setPageSize] = useState(50);
  const [contextLines, setContextLines] = useState(10);
  const [expandedKey, setExpandedKey] = useState<string>();
  const [contextMap, setContextMap] = useState<Record<string, LogContextResponse>>({});
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [tailStreamSeed, setTailStreamSeed] = useState(0);
  const [messageApi, holder] = message.useMessage();
  const tailStreamCloseRef = useRef<(() => void) | null>(null);
  const resultScrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    void api.getLogCatalog()
      .then((data) => {
        setCatalog(data);
        const firstNode = data.nodes[0];
        setSelectedNodeId(firstNode?.id);
        setSelectedSourceId(firstNode?.sources[0]?.id);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!tailMode) {
      tailStreamCloseRef.current?.();
      tailStreamCloseRef.current = null;
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setTailMode(false);
      messageApi.info('跟随模式已自动关闭，如需继续请手动重新开启');
    }, TAIL_MODE_AUTO_STOP_MS);
    return () => window.clearTimeout(timer);
  }, [tailMode, messageApi]);

  useEffect(() => {
    if (tailMode && queryResult) {
      setTailStreamSeed((current) => current + 1);
    }
  }, [tailMode]);

  const selectedNode = useMemo(() => catalog?.nodes.find((node) => node.id === selectedNodeId), [catalog, selectedNodeId]);
  const orderedItems = useMemo(
    () => (queryResult?.items ? [...queryResult.items].reverse() : []),
    [queryResult],
  );

  useEffect(() => () => {
    tailStreamCloseRef.current?.();
    tailStreamCloseRef.current = null;
  }, []);

  useEffect(() => {
    const container = resultScrollRef.current;
    if (!container) {
      return;
    }
    requestAnimationFrame(() => {
      container.scrollTop = container.scrollHeight;
    });
  }, [queryResult?.page, orderedItems.length]);

  useEffect(() => {
    if (!tailMode || !queryResult || !selectedNodeId || !selectedSourceId || tailStreamSeed === 0) {
      return;
    }
    tailStreamCloseRef.current?.();
    const latestItem = queryResult.items[0];
    tailStreamCloseRef.current = api.openLogTailStream({
      nodeId: selectedNodeId,
      sourceId: selectedSourceId,
      keyword: keyword.trim(),
      afterCollectedAtEpochMs: latestItem?.collectedAtEpochMs,
      afterFilePath: latestItem?.filePath,
      afterLineNumber: latestItem?.lineNumber,
    }, {
      onAppend: (payload) => appendTailItems(payload),
      onError: (text) => {
        setTailMode(false);
        messageApi.warning(text);
      },
    });
    return () => {
      tailStreamCloseRef.current?.();
      tailStreamCloseRef.current = null;
    };
  }, [tailMode, tailStreamSeed]);

  async function runQuery(page = 1, nextPageSize = pageSize) {
    if (!selectedNodeId) {
      messageApi.warning('请选择节点');
      return;
    }
    if (!selectedSourceId) {
      messageApi.warning('请选择日志目录');
      return;
    }
    if (!tailMode && !keyword.trim()) {
      messageApi.warning('请输入关键词');
      return;
    }
    setQuerying(true);
    tailStreamCloseRef.current?.();
    tailStreamCloseRef.current = null;
    try {
      const response = await api.queryLogs({
        nodeId: selectedNodeId,
        sourceId: selectedSourceId,
        keyword: keyword.trim(),
        page,
        pageSize: nextPageSize,
        tailMode,
      });
      setQueryResult(response);
      setExpandedKey(undefined);
      setContextMap({});
      if (tailMode) {
        setTailStreamSeed((current) => current + 1);
      }
    } finally {
      setQuerying(false);
    }
  }

  function appendTailItems(payload: LogTailResponse) {
    if (!payload.items.length) {
      return;
    }
    setQueryResult((current) => {
      if (!current) {
        return current;
      }
      return {
        ...current,
        total: current.total + payload.items.length,
        items: [...payload.items.slice().reverse(), ...current.items],
      };
    });
  }

  async function openContext(item: LogQueryItem) {
    if (!selectedNodeId || !selectedSourceId) {
      return;
    }
    const key = `${item.filePath}-${item.lineNumber}`;
    if (expandedKey === key) {
      setExpandedKey(undefined);
      return;
    }
    if (contextMap[key]) {
      setExpandedKey(key);
      return;
    }
    const context = await api.queryLogContext({
      nodeId: selectedNodeId,
      sourceId: selectedSourceId,
      filePath: item.filePath,
      lineNumber: item.lineNumber,
      beforeLines: contextLines,
      afterLines: contextLines,
    });
    setContextMap((current) => ({ ...current, [key]: context }));
    setExpandedKey(key);
  }

  function resetFilters() {
    const firstNode = catalog?.nodes[0];
    tailStreamCloseRef.current?.();
    tailStreamCloseRef.current = null;
    setSelectedNodeId(firstNode?.id);
    setSelectedSourceId(firstNode?.sources[0]?.id);
    setKeyword('');
    setTailMode(false);
    setContextLines(10);
    setPageSize(50);
    setQueryResult(undefined);
    setExpandedKey(undefined);
    setContextMap({});
  }

  if (loading) {
    return <div className="flex min-h-[40vh] items-center justify-center"><Spin size="large" /></div>;
  }

  if (!catalog?.nodes.length) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <div className="font-mono text-sm text-slate-500">还没有可用的日志节点</div>
      </div>
    );
  }

  return (
    <>
      {holder}
      <div className={`relative grid gap-3 ${sidebarCollapsed ? 'grid-cols-1' : 'xl:grid-cols-[236px_minmax(0,1fr)]'} ${fullHeight ? 'h-full min-h-0' : ''}`}>
        <button
          type="button"
          aria-label={sidebarCollapsed ? '展开节点栏' : '收起节点栏'}
          className={`absolute top-1/2 z-10 flex h-9 w-5 -translate-y-1/2 items-center justify-center border border-slate-300 bg-white text-slate-600 shadow-sm transition hover:bg-slate-50 ${sidebarCollapsed ? 'left-0' : 'left-[226px]'}`}
          onClick={() => setSidebarCollapsed((current) => !current)}
        >
          {sidebarCollapsed ? <RightOutlined /> : <LeftOutlined />}
        </button>
        {!sidebarCollapsed ? (
          <div className={`dx-section p-0 shadow-panel ${fullHeight ? 'flex min-h-0 flex-col' : ''}`}>
            <div className="mb-2 flex items-center justify-between gap-2">
              <div className="px-3 pt-3 dx-section-title">节点与日志源</div>
            </div>
            <div className={`dx-log-sidebar ${fullHeight ? 'min-h-0 flex-1 overflow-auto' : ''}`}>
              {catalog.nodes.map((node) => (
                <div key={node.id} className="dx-log-node">
                  <button
                    type="button"
                    className={`dx-log-node-button ${selectedNodeId === node.id ? 'is-active' : ''}`}
                    onClick={() => {
                      setSelectedNodeId(node.id);
                      setSelectedSourceId(node.sources[0]?.id);
                      tailStreamCloseRef.current?.();
                      tailStreamCloseRef.current = null;
                      setQueryResult(undefined);
                      setExpandedKey(undefined);
                      setContextMap({});
                    }}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <Typography.Text strong className="!text-slate-800">{node.name}</Typography.Text>
                      <Pill tone={node.heartbeatStatus === '在线' ? 'green' : 'red'}>{node.heartbeatStatus}</Pill>
                    </div>
                    <div className="mt-1 font-mono text-[11px] text-slate-500">{node.host}:{node.port}</div>
                  </button>
                  <div className="dx-log-source-list">
                    {node.sources.map((source) => (
                      <button
                        key={source.id}
                        type="button"
                        className={`dx-log-source-button ${selectedSourceId === source.id ? 'is-active' : ''}`}
                        onClick={() => {
                          setSelectedNodeId(node.id);
                          setSelectedSourceId(source.id);
                          tailStreamCloseRef.current?.();
                          tailStreamCloseRef.current = null;
                          setQueryResult(undefined);
                          setExpandedKey(undefined);
                          setContextMap({});
                        }}
                      >
                        <div className="truncate text-[13px] font-medium">{source.name}</div>
                        <div className="truncate text-[11px] text-slate-500">
                          {source.description || '未填写描述'}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : null}
        <div className={`${fullHeight ? 'flex min-h-0 flex-col' : ''}`}>
          <div className={`dx-console dx-log-workbench shadow-panel ${fullHeight ? 'flex min-h-0 flex-1 flex-col' : ''}`}>
            <div className="dx-log-workbench-toolbar">
              <div className="flex items-center gap-2 overflow-x-auto whitespace-nowrap">
                <Input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  onPressEnter={() => void runQuery(1)}
                  className="dx-log-search-input min-w-[260px] flex-1"
                  placeholder='支持空格分词(且)、"完整短语"、-排除词；跟随模式可留空'
                />
                <Space className="dx-log-toolbar shrink-0 whitespace-nowrap">
                  <Button type={tailMode ? 'primary' : 'default'} onClick={() => setTailMode((current) => !current)}>
                    跟随模式
                  </Button>
                  <Button onClick={() => setContextLines((current) => (current >= 50 ? 10 : current + 10))}>
                    上下文 {contextLines} 行
                  </Button>
                  <Button className="whitespace-nowrap" onClick={resetFilters}>重置</Button>
                  <Button type="primary" onClick={() => void runQuery(1)} loading={querying}>
                    查询
                  </Button>
                </Space>
                {queryResult ? (
                  <div className="dx-log-pagination shrink-0 whitespace-nowrap">
                    <Pagination
                      size="small"
                      current={queryResult.page}
                      pageSize={queryResult.pageSize}
                      total={queryResult.total}
                      onChange={(nextPage, nextPageSize) => {
                        setPageSize(nextPageSize);
                        void runQuery(nextPage, nextPageSize);
                      }}
                      showSizeChanger
                      pageSizeOptions={[20, 50, 100, 200]}
                    />
                  </div>
                ) : null}
              </div>
            </div>
            <div ref={resultScrollRef} className={`${fullHeight ? 'min-h-0 flex-1 overflow-auto' : ''}`}>
              {!queryResult?.items.length ? (
                <div className="flex min-h-[220px] items-center justify-center px-6 py-8">
                  <div className="font-mono text-sm text-slate-400">输入关键词后查询日志</div>
                </div>
              ) : (
                orderedItems.map((item) => (
                  <div key={`${item.filePath}-${item.lineNumber}`} className="last:border-b-0">
                    <button
                      type="button"
                      className={`relative w-full px-2.5 py-1 text-left transition ${
                        expandedKey === `${item.filePath}-${item.lineNumber}`
                          ? 'bg-lime-950/18 shadow-[inset_3px_0_0_0_rgba(163,230,53,0.95)]'
                          : 'hover:bg-white/5'
                      }`}
                      onClick={() => void openContext(item)}
                    >
                      <div
                        className={`absolute right-1.5 top-0.5 z-[2] font-mono text-[9px] leading-none ${
                          expandedKey === `${item.filePath}-${item.lineNumber}` ? 'text-lime-300' : 'text-sky-300'
                        }`}
                      >
                        {item.lineNumber}
                      </div>
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0 flex-1">
                          {renderLogContent(item.content, expandedKey === `${item.filePath}-${item.lineNumber}`)}
                        </div>
                      </div>
                    </button>
                    {expandedKey === `${item.filePath}-${item.lineNumber}` && contextMap[`${item.filePath}-${item.lineNumber}`] ? (
                      <div className="bg-[#101923] px-2.5 py-1.5">
                        <div className="space-y-1">
                          {contextMap[`${item.filePath}-${item.lineNumber}`].lines.map((line) => (
                            <div
                              key={`${line.lineNumber}-${line.content}`}
                              className={`relative py-0.5 px-1.5 ${line.hit
                                ? 'bg-lime-950/35 shadow-[inset_3px_0_0_0_rgba(163,230,53,0.95)]'
                                : 'bg-[#0b1219]'}`}
                            >
                              <div className={`absolute right-1.5 top-0.5 z-[2] font-mono text-[9px] leading-none ${line.hit ? 'text-lime-300' : 'text-slate-500'}`}>
                                {line.lineNumber}
                              </div>
                              {renderLogContent(line.content, false, line.hit)}
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
