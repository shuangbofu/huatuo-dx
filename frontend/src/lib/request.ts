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
      throw new Error(messageText);
    }

    if (isResultEnvelope(payload)) {
      if (payload.code !== '0') {
        handleAuthExpired(payload.subCode);
        throw new Error(payload.message || `[${payload.code}/${payload.subCode}] 请求失败`);
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
