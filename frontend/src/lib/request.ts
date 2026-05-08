import { message } from 'antd';
import { authStorage } from '../auth/authStorage';

interface ResultEnvelope<T> {
  code: string;
  subCode: string;
  message: string;
  data: T;
}

interface RequestOptions {
  silent?: boolean;
}

class RequestError extends Error {
  readonly rawMessage?: string;
  readonly code?: string;
  readonly subCode?: string;

  constructor(message: string, options?: { rawMessage?: string; code?: string; subCode?: string }) {
    super(message);
    this.name = 'RequestError';
    this.rawMessage = options?.rawMessage;
    this.code = options?.code;
    this.subCode = options?.subCode;
  }
}

function isResultEnvelope(value: unknown): value is ResultEnvelope<unknown> {
  return Boolean(
    value
      && typeof value === 'object'
      && 'code' in (value as Record<string, unknown>)
      && 'subCode' in (value as Record<string, unknown>)
      && 'message' in (value as Record<string, unknown>),
  );
}

function stripHtml(input: string): string {
  return input
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeErrorMessage(raw: string, fallback: string): string {
  const cleaned = stripHtml(raw);
  if (!cleaned) {
    return fallback;
  }
  if (cleaned.includes('No static resource') && cleaned.includes('internal/arthas/status')) {
    return '当前节点 Agent 没有提供诊断引擎状态接口，请重启伴生 Agent 或重新部署节点 Agent';
  }
  if (cleaned.includes('No static resource') && cleaned.includes('internal/arthas/install')) {
    return '当前节点 Agent 没有提供诊断引擎安装接口，请重启伴生 Agent 或重新部署节点 Agent';
  }
  if (cleaned.includes('Whitelabel Error Page')) {
    return '服务返回了异常页面，请检查接口配置或服务日志';
  }
  return cleaned.length > 120 ? `${cleaned.slice(0, 120)}...` : cleaned;
}

function normalizeBusinessMessage(raw: string, subCode?: string, fallback = '请求失败'): string {
  const cleaned = stripHtml(raw || '');
  if (!cleaned) {
    return fallback;
  }
  if (subCode === 'ARTHAS_EXECUTE_FAILED') {
    const concise = cleaned.split('；Arthas 原始输出:')[0]?.trim() || cleaned;
    return concise.includes('启动 Arthas 监控失败')
      ? concise
      : `启动监控失败：${concise}`;
  }
  return normalizeErrorMessage(cleaned, fallback);
}

function handleAuthExpired(subCode?: string | null) {
  if (subCode === 'AUTH_REQUIRED' || subCode === 'AUTH_TOKEN_INVALID') {
    authStorage.clearToken();
    const hashPath = window.location.hash.replace(/^#/, '') || '/';
    if (!hashPath.startsWith('/login')) {
      const callback = hashPath.startsWith('/') ? hashPath : `/${hashPath}`;
      window.location.replace(`/#/login?callback=${encodeURIComponent(callback)}`);
    }
    return true;
  }
  return false;
}

export async function request<T>(url: string, init?: RequestInit, options?: RequestOptions): Promise<T> {
  try {
    const token = authStorage.getToken();
    const response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init?.headers ?? {}),
      },
      ...init,
    });

    if (response.status === 204) {
      return undefined as T;
    }

    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('application/json')
      ? await response.json() as unknown
      : await response.text();

    if (!response.ok) {
      const fallback = `${response.status} ${response.statusText}`;
      const messageText = typeof payload === 'string'
        ? normalizeErrorMessage(payload, fallback)
        : fallback;
      throw new RequestError(messageText, { rawMessage: typeof payload === 'string' ? payload : undefined });
    }

    if (isResultEnvelope(payload)) {
      if (payload.code !== '0') {
        handleAuthExpired(payload.subCode);
        const userMessage = normalizeBusinessMessage(
          payload.message || `[${payload.code}/${payload.subCode}] 请求失败`,
          payload.subCode,
          `[${payload.code}/${payload.subCode}] 请求失败`,
        );
        if (payload.message && payload.message !== userMessage) {
          console.error('Backend business error detail:', {
            code: payload.code,
            subCode: payload.subCode,
            message: payload.message,
          });
        }
        throw new RequestError(userMessage, {
          rawMessage: payload.message,
          code: payload.code,
          subCode: payload.subCode,
        });
      }
      return payload.data as T;
    }

    return payload as T;
  } catch (error) {
    const messageText = error instanceof Error ? error.message : '请求失败';
    if (!options?.silent) {
      message.error(messageText);
    }
    throw error;
  }
}

export function ensureArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value as T[] : [];
}
