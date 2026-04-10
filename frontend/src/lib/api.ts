import { authStorage } from '../auth/authStorage';
import { ensureArray, request } from './request';

export type NodeStatus = 'ONLINE' | 'OFFLINE';
export type DiagnosticType = 'WATCH' | 'TRACE' | 'STACK';
export type DiagnosticExecutionStatus = 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED';
export type UserRole = 'ADMIN' | 'USER';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface UserProfile {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
  enabled: boolean;
  updatedAt?: string;
}

export interface LoginResponse {
  token: string;
  user: UserProfile;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface UserPayload {
  username: string;
  displayName: string;
  role: UserRole;
  enabled: boolean;
}

export function formatNodeStatus(status: NodeStatus): string {
  return status === 'ONLINE' ? '在线' : '离线';
}

export function formatDiagnosticType(type: DiagnosticType): string {
  if (type === 'WATCH') {
    return '监看';
  }
  if (type === 'TRACE') {
    return '链路';
  }
  return '堆栈';
}

export function formatExecutionStatus(success: boolean): string {
  return success ? '成功' : '失败';
}

export function formatDiagnosticExecutionStatus(status: DiagnosticExecutionStatus): string {
  switch (status) {
    case 'RUNNING':
      return '监控中';
    case 'COMPLETED':
      return '已完成';
    case 'STOPPED':
      return '已停止';
    case 'FAILED':
      return '失败';
    default:
      return status;
  }
}

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
}

export function formatJavaClassName(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const text = value.trim();
  if (!text.includes('.')) {
    return text;
  }
  const parts = text.split('.').filter(Boolean);
  if (parts.length <= 2) {
    return text;
  }
  const className = parts[parts.length - 1];
  const packageParts = parts.slice(0, -1);
  const abbreviatedPackages = packageParts.map((part, index) => {
    const keepFull = index >= packageParts.length - 2 || part.length <= 2;
    return keepFull ? part : part.charAt(0);
  });
  return [...abbreviatedPackages, className].join('.');
}

export interface AgentNode {
  id: number;
  nodeCode: string;
  nodeName: string;
  host: string;
  port: number;
  baseUrl: string;
  processPattern: string;
  visibleProcessNames: string[];
  logDirectories: string[];
  logSourceConfigs: LogSourceConfig[];
  companionBootstrapLogDirectories: string[];
  logCollectEnabled: boolean;
  logCollectIntervalSeconds: number;
  tags: string[];
  arthasBootJar: string;
  localCompanion: boolean;
  companionRestartRecommended: boolean;
  status: NodeStatus;
  runtimeVersion: string;
  matchedProcessSummary: string;
  lastHeartbeatAt: string;
  updatedAt: string;
}

export interface NodeUpdatePayload {
  nodeName: string;
  host: string;
  port: number;
  baseUrl: string;
  secret: string;
  processPattern: string;
  visibleProcessNames: string[];
  logDirectories: string[];
  logSourceConfigs: LogSourceConfig[];
  companionBootstrapLogDirectories: string[];
  logCollectEnabled: boolean;
  logCollectIntervalSeconds: number;
  tags?: string[];
  arthasBootJar?: string;
}

export interface LogSourceConfig {
  name: string;
  description?: string;
  path: string;
  mode?: 'QUERY' | 'COLLECT' | string;
  parseMode?: 'RAW' | 'LOGBACK_PATTERN' | 'JSON' | string;
  parsePattern?: string;
  logbackConfigPath?: string;
  collectIntervalSeconds?: number;
}

export interface LogCollectionStatus {
  directory: string;
  indexedFileCount: number;
  indexedLineCount: number;
  latestCollectedAtEpochMs?: number;
}

export interface ProcessView {
  pid: number;
  displayName: string;
  command: string;
  commandLine: string;
  user: string;
}

export interface ArthasStatus {
  installed: boolean;
  status: 'READY' | 'MISSING' | 'FAILED' | string;
  jarPath?: string;
  message?: string;
}

export interface LogFileView {
  path: string;
  name: string;
  size: number;
  directory: boolean;
  modifiedAt: string;
}

export interface LogContentView {
  path: string;
  content: string;
  lineCount: number;
  truncated: boolean;
}

export interface DiagnosticRule {
  id: number;
  agentNodeId?: number;
  agentNodeName?: string;
  name: string;
  ownerUsername?: string;
  ownerDisplayName?: string;
  type: DiagnosticType;
  targetClassPattern: string;
  targetMethodPattern: string;
  selectedProcessName?: string;
  targetProcessPattern?: string;
  outputExpression?: string;
  conditionExpression?: string;
  commandOptions?: string;
  stackDepth?: number;
  maxMatches?: number;
  executionTimeoutMs?: number;
  enabled: boolean;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DiagnosticExecutionResult {
  sessionId?: string;
  ruleId: number;
  ruleName: string;
  type?: DiagnosticType;
  status: DiagnosticExecutionStatus;
  success: boolean;
  command: string;
  output: string;
  errorMessage?: string;
  executedAt: string;
  updatedAt: string;
  durationMs: number;
  pid?: number;
}

export interface DiagnosticExecutionRequest {
  nodeId?: number;
  pid?: number;
  processName?: string;
  processPattern?: string;
  maxMatches?: number;
}

export interface DiagnosticExecutionRecord {
  id: number;
  sessionId?: string;
  ruleId: number;
  agentNodeId: number;
  agentNodeName?: string;
  ruleName: string;
  processDisplayName?: string;
  ownerUsername?: string;
  ownerDisplayName?: string;
  type: DiagnosticType;
  status: DiagnosticExecutionStatus;
  success: boolean;
  command: string;
  output?: string;
  errorMessage?: string;
  pid?: number;
  durationMs: number;
  executedAt: string;
  updatedAt: string;
  sharedSession: boolean;
  subscriberCount: number;
  triggerCount: number;
  maxCostMs?: number;
  triggerEvents: DiagnosticTriggerEvent[];
}

export interface DiagnosticTriggerEvent {
  sequence: number;
  title: string;
  timestamp?: string;
  location?: string;
  threadName?: string;
  costMs?: number;
  summary: string;
  rawContent: string;
  methodName?: string;
  watchBody?: string;
  traceNodes: DiagnosticTraceNode[];
}

export interface DiagnosticTraceNode {
  depth: number;
  costMs?: number;
  label: string;
}

export interface LogSource {
  id: string;
  name: string;
  description?: string;
  directory: string;
}

export interface LogConsoleNode {
  id: number;
  name: string;
  host: string;
  port: number;
  heartbeatStatus: string;
  sources: LogSource[];
}

export interface LogConsoleCatalog {
  nodes: LogConsoleNode[];
}

export interface LogQueryItem {
  filePath: string;
  lineNumber: number;
  content: string;
  collectedAtEpochMs: number;
}

export interface LogQueryResponse {
  total: number;
  page: number;
  pageSize: number;
  scannedFiles: number;
  items: LogQueryItem[];
}

export interface LogContextLine {
  lineNumber: number;
  content: string;
  hit: boolean;
}

export interface LogContextResponse {
  filePath: string;
  hitLineNumber: number;
  startLineNumber: number;
  endLineNumber: number;
  lines: LogContextLine[];
}

export interface LogTailResponse {
  items: LogQueryItem[];
  latestCollectedAtEpochMs?: number;
  latestFilePath?: string;
  latestLineNumber?: number;
}

export const api = {
  login: (payload: LoginPayload, options?: { silent?: boolean }) =>
    request<LoginResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }, options),
  me: (options?: { silent?: boolean }) =>
    request<UserProfile>('/api/auth/me', undefined, options),
  logout: (options?: { silent?: boolean }) =>
    request<void>('/api/auth/logout', { method: 'POST' }, options),
  listUsers: async () =>
    ensureArray<UserProfile>(await request<unknown>('/api/users')),
  listUsersPage: (params: { keyword?: string; role?: UserRole; enabled?: boolean; page: number; pageSize: number }) => {
    const search = new URLSearchParams({
      page: String(params.page),
      pageSize: String(params.pageSize),
    });
    if (params.keyword) search.set('keyword', params.keyword);
    if (params.role) search.set('role', params.role);
    if (params.enabled != null) search.set('enabled', String(params.enabled));
    return request<PageResult<UserProfile>>(`/api/users/page?${search.toString()}`);
  },
  createUser: (payload: UserPayload) =>
    request<UserProfile>('/api/users', { method: 'POST', body: JSON.stringify(payload) }),
  updateUser: (id: number, payload: UserPayload) =>
    request<UserProfile>(`/api/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  resetUserPassword: (id: number) =>
    request<void>(`/api/users/${id}/reset-password`, { method: 'POST' }),
  deleteUser: (id: number) =>
    request<void>(`/api/users/${id}`, { method: 'DELETE' }),
  listNodes: async () => ensureArray<AgentNode>(await request<unknown>('/api/agents')),
  listNodesPage: (params: { keyword?: string; status?: NodeStatus | 'ALL'; page: number; pageSize: number }) => {
    const search = new URLSearchParams({
      page: String(params.page),
      pageSize: String(params.pageSize),
    });
    if (params.keyword) search.set('keyword', params.keyword);
    if (params.status && params.status !== 'ALL') search.set('status', params.status);
    return request<PageResult<AgentNode>>(`/api/agents/page?${search.toString()}`);
  },
  updateNode: (nodeId: number, payload: NodeUpdatePayload) =>
    request<AgentNode>(`/api/agents/${nodeId}`, { method: 'PUT', body: JSON.stringify(payload) }),
  getProcesses: async (nodeId: number) =>
    ensureArray<ProcessView>(await request<unknown>(`/api/agents/${nodeId}/processes`)),
  getAllProcesses: async (nodeId: number) =>
    ensureArray<ProcessView>(await request<unknown>(`/api/agents/${nodeId}/all-processes`)),
  getArthasStatus: (nodeId: number) =>
    request<ArthasStatus>(`/api/agents/${nodeId}/arthas/status`),
  installArthas: (nodeId: number) =>
    request<ArthasStatus>(`/api/agents/${nodeId}/arthas/install`, { method: 'POST' }),
  listNodeLogs: async (nodeId: number, path?: string) =>
    ensureArray<LogFileView>(await request<unknown>(`/api/agents/${nodeId}/logs/index${path ? `?path=${encodeURIComponent(path)}` : ''}`)),
  readNodeLog: (nodeId: number, path: string, limit = 20) =>
    request<LogContentView>(`/api/agents/${nodeId}/logs/content?path=${encodeURIComponent(path)}&limit=${limit}`),
  getLogCollectionStatus: (nodeId: number, directory: string) =>
    request<LogCollectionStatus>(`/api/agents/${nodeId}/logs/collection-status?directory=${encodeURIComponent(directory)}`),
  syncNode: (nodeId: number) => request<void>(`/api/agents/${nodeId}/sync`, { method: 'POST' }),
  restartLocalCompanion: (nodeId: number) =>
    request<AgentNode>(`/api/agents/${nodeId}/restart`, { method: 'POST' }),

  listRules: async (nodeId?: number) =>
    ensureArray<DiagnosticRule>(await request<unknown>(`/api/diagnostic-rules${nodeId ? `?nodeId=${nodeId}` : ''}`)),
  listRulesPage: (params: { keyword?: string; type?: DiagnosticType | 'ALL'; enabled?: boolean; page: number; pageSize: number }) => {
    const search = new URLSearchParams({
      page: String(params.page),
      pageSize: String(params.pageSize),
    });
    if (params.keyword) search.set('keyword', params.keyword);
    if (params.type && params.type !== 'ALL') search.set('type', params.type);
    if (params.enabled != null) search.set('enabled', String(params.enabled));
    return request<PageResult<DiagnosticRule>>(`/api/diagnostic-rules/page?${search.toString()}`);
  },
  createRule: (payload: Partial<DiagnosticRule>) =>
    request<DiagnosticRule>('/api/diagnostic-rules', { method: 'POST', body: JSON.stringify(payload) }),
  updateRule: (id: number, payload: Partial<DiagnosticRule>) =>
    request<DiagnosticRule>(`/api/diagnostic-rules/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  toggleRule: (id: number, enabled: boolean) =>
    request<DiagnosticRule>(`/api/diagnostic-rules/${id}/enabled?enabled=${enabled}`, { method: 'PUT' }),
  startRule: (id: number, payload?: DiagnosticExecutionRequest) =>
    request<DiagnosticExecutionRecord>(`/api/diagnostic-rules/${id}/start`, { method: 'POST', body: JSON.stringify(payload ?? {}) }),
  deleteRule: (id: number) => request<void>(`/api/diagnostic-rules/${id}`, { method: 'DELETE' }),
  listDiagnosticRecords: async (nodeId?: number) =>
    ensureArray<DiagnosticExecutionRecord>(await request<unknown>(`/api/diagnostic-records${nodeId ? `?nodeId=${nodeId}` : ''}`)),
  listDiagnosticRecordsPage: (params: {
    nodeId?: number;
    ruleId?: number;
    keyword?: string;
    type?: DiagnosticType | 'ALL';
    status?: DiagnosticExecutionStatus | 'ALL';
    page: number;
    pageSize: number;
  }) => {
    const search = new URLSearchParams({
      page: String(params.page),
      pageSize: String(params.pageSize),
    });
    if (params.nodeId != null) search.set('nodeId', String(params.nodeId));
    if (params.ruleId != null) search.set('ruleId', String(params.ruleId));
    if (params.keyword) search.set('keyword', params.keyword);
    if (params.type && params.type !== 'ALL') search.set('type', params.type);
    if (params.status && params.status !== 'ALL') search.set('status', params.status);
    return request<PageResult<DiagnosticExecutionRecord>>(`/api/diagnostic-records/page?${search.toString()}`);
  },
  getDiagnosticRecord: (id: number) =>
    request<DiagnosticExecutionRecord>(`/api/diagnostic-records/${id}`),
  stopDiagnosticRecord: (id: number) =>
    request<DiagnosticExecutionRecord>(`/api/diagnostic-records/${id}/stop`, { method: 'POST' }),

  getLogCatalog: () => request<LogConsoleCatalog>('/api/log-console/catalog'),
  queryLogs: (payload: { nodeId: number; sourceId: string; keyword: string; page?: number; pageSize?: number; tailMode?: boolean }) =>
    request<LogQueryResponse>('/api/log-console/query', { method: 'POST', body: JSON.stringify(payload) }),
  queryLogContext: (payload: { nodeId: number; sourceId: string; filePath: string; lineNumber: number; beforeLines?: number; afterLines?: number }) =>
    request<LogContextResponse>('/api/log-console/context', { method: 'POST', body: JSON.stringify(payload) }),
  openLogTailStream: (
    payload: {
      nodeId: number;
      sourceId: string;
      keyword: string;
      afterCollectedAtEpochMs?: number;
      afterFilePath?: string;
      afterLineNumber?: number;
    },
    handlers: {
      onAppend: (payload: LogTailResponse) => void;
      onError?: (message: string) => void;
    },
  ) => {
    const params = new URLSearchParams({
      nodeId: String(payload.nodeId),
      sourceId: payload.sourceId,
      keyword: payload.keyword,
    });
    if (payload.afterCollectedAtEpochMs != null) {
      params.set('afterCollectedAtEpochMs', String(payload.afterCollectedAtEpochMs));
    }
    if (payload.afterFilePath) {
      params.set('afterFilePath', payload.afterFilePath);
    }
    if (payload.afterLineNumber != null) {
      params.set('afterLineNumber', String(payload.afterLineNumber));
    }
    const token = authStorage.getToken();
    if (token) {
      params.set('authToken', token);
    }
    const eventSource = new EventSource(`/api/log-console/tail/stream?${params.toString()}`);
    eventSource.addEventListener('append', (event) => {
      handlers.onAppend(JSON.parse((event as MessageEvent<string>).data) as LogTailResponse);
    });
    eventSource.addEventListener('error', (event) => {
      const data = (event as MessageEvent<string>).data;
      handlers.onError?.(typeof data === 'string' && data ? data : '日志流连接已断开');
      eventSource.close();
    });
    eventSource.onerror = () => {
      handlers.onError?.('日志流连接已断开');
      eventSource.close();
    };
    return () => eventSource.close();
  },
};
