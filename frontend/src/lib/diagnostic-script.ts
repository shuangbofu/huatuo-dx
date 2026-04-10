import type { DiagnosticRule, DiagnosticType } from './api';

export type SkipJdkMethodMode = 'DEFAULT' | 'TRUE' | 'FALSE';

export interface DiagnosticRuleFormValues {
  name: string;
  type: DiagnosticType;
  selectedProcessName?: string;
  targetClassPattern: string;
  targetMethodPattern: string;
  outputExpression?: string;
  outputPresetParts?: string[];
  outputCustomParts?: string;
  conditionExpression?: string;
  regexMatch?: boolean;
  classloaderHash?: string;
  matchLimit?: number;
  watchBefore?: boolean;
  watchExceptionExit?: boolean;
  watchSuccessExit?: boolean;
  watchFinally?: boolean;
  skipJdkMethodMode?: SkipJdkMethodMode;
  extraCommandOptions?: string;
  stackDepth?: number;
  maxMatches?: number;
  executionTimeoutMs?: number;
  enabled?: boolean;
  notes?: string;
}

export interface ParsedDiagnosticScript {
  type: DiagnosticType;
  targetClassPattern: string;
  targetMethodPattern: string;
  outputExpression?: string;
  conditionExpression?: string;
  regexMatch?: boolean;
  classloaderHash?: string;
  matchLimit?: number;
  watchBefore?: boolean;
  watchExceptionExit?: boolean;
  watchSuccessExit?: boolean;
  watchFinally?: boolean;
  skipJdkMethodMode?: SkipJdkMethodMode;
  extraCommandOptions?: string;
  stackDepth?: number;
  maxMatches?: number;
}

export const WATCH_OUTPUT_PRESETS = [
  { label: '入参 params', value: 'params' },
  { label: '目标对象 target', value: 'target' },
  { label: '返回值 returnObj', value: 'returnObj' },
  { label: '异常 throwExp', value: 'throwExp' },
  { label: '耗时 #cost', value: '#cost' },
] as const;

const WATCH_OUTPUT_PRESET_SET: ReadonlySet<string> = new Set(WATCH_OUTPUT_PRESETS.map((item) => item.value));

export function buildRulePayload(
  values: DiagnosticRuleFormValues,
): Partial<DiagnosticRule> {
  return {
    name: values.name,
    type: values.type,
    selectedProcessName: normalizeOptionalText(values.selectedProcessName),
    targetClassPattern: values.targetClassPattern.trim(),
    targetMethodPattern: values.targetMethodPattern.trim(),
    outputExpression: values.type === 'WATCH' ? composeWatchOutputExpression(values.outputPresetParts, values.outputCustomParts) : undefined,
    conditionExpression: normalizeOptionalText(values.conditionExpression),
    commandOptions: composeCommandOptions(values),
    stackDepth: values.stackDepth ?? 2,
    maxMatches: values.maxMatches ?? 5,
    executionTimeoutMs: values.executionTimeoutMs ?? 30000,
    enabled: values.enabled ?? true,
    notes: normalizeOptionalText(values.notes),
  };
}

export function buildWatchOutputExpression(values: Partial<DiagnosticRuleFormValues>): string {
  return composeWatchOutputExpression(values.outputPresetParts, values.outputCustomParts)
    || normalizeOptionalText(values.outputExpression)
    || '{params,returnObj,throwExp}';
}

export function buildDiagnosticScript(values: Partial<DiagnosticRuleFormValues>): string {
  const type = values.type ?? 'WATCH';
  const clazz = (values.targetClassPattern ?? '').trim();
  const method = (values.targetMethodPattern ?? '').trim();
  const maxMatches = values.maxMatches ?? 5;
  const condition = normalizeOptionalText(values.conditionExpression);
  const commandOptions = composeCommandOptions(values);
  if (type === 'TRACE') {
    const segments = ['trace', clazz, method];
    if (condition) {
      segments.push(quote(condition));
    }
    segments.push('-n', String(maxMatches));
    if (commandOptions) {
      segments.push(commandOptions);
    }
    return segments.filter(Boolean).join(' ').trim();
  }
  if (type === 'STACK') {
    const segments = ['stack', clazz, method];
    if (condition) {
      segments.push(quote(condition));
    }
    segments.push('-n', String(maxMatches));
    if (commandOptions) {
      segments.push(commandOptions);
    }
    return segments.filter(Boolean).join(' ').trim();
  }
  const depth = values.stackDepth ?? 2;
  const outputExpression = buildWatchOutputExpression(values);
  const segments = ['watch', clazz, method, quote(outputExpression)];
  if (condition) {
    segments.push(quote(condition));
  }
  segments.push('-x', String(depth), '-n', String(maxMatches));
  if (commandOptions) {
    segments.push(commandOptions);
  }
  return segments.filter(Boolean).join(' ').trim();
}

export function parseDiagnosticScript(script: string): ParsedDiagnosticScript {
  const tokens = tokenizeScript(script);
  if (!tokens.length) {
    throw new Error('脚本不能为空');
  }
  const command = tokens[0]?.toLowerCase();
  if (command !== 'watch' && command !== 'trace' && command !== 'stack') {
    throw new Error('只支持 watch、trace 或 stack 脚本');
  }

  const parsed: ParsedDiagnosticScript = {
    type: command === 'watch' ? 'WATCH' : command === 'trace' ? 'TRACE' : 'STACK',
    targetClassPattern: '',
    targetMethodPattern: '',
  };

  const positionalTokens: string[] = [];
  const extraTokens: string[] = [];
  let index = 1;
  while (index < tokens.length) {
    const token = tokens[index];
    if (token === '-x') {
      parsed.stackDepth = Number(tokens[index + 1] ?? 2);
      index += 2;
      continue;
    }
    if (token === '-n') {
      parsed.maxMatches = Number(tokens[index + 1] ?? 5);
      index += 2;
      continue;
    }
    if (token === '-E') {
      parsed.regexMatch = true;
      index += 1;
      continue;
    }
    if (token === '-b') {
      parsed.watchBefore = true;
      index += 1;
      continue;
    }
    if (token === '-e') {
      parsed.watchExceptionExit = true;
      index += 1;
      continue;
    }
    if (token === '-s') {
      parsed.watchSuccessExit = true;
      index += 1;
      continue;
    }
    if (token === '-f') {
      parsed.watchFinally = true;
      index += 1;
      continue;
    }
    if (token === '-c' || token === '--classloaderHash') {
      parsed.classloaderHash = tokens[index + 1] ?? '';
      index += 2;
      continue;
    }
    if (token === '-m' || token === '--maxMatch') {
      parsed.matchLimit = Number(tokens[index + 1] ?? 50);
      index += 2;
      continue;
    }
    if (token === '--skipJDKMethod') {
      const value = (tokens[index + 1] ?? '').toLowerCase();
      if (value === 'true' || value === 'false') {
        parsed.skipJdkMethodMode = value === 'true' ? 'TRUE' : 'FALSE';
        index += 2;
      } else {
        parsed.skipJdkMethodMode = 'TRUE';
        index += 1;
      }
      continue;
    }
    if (token.startsWith('-')) {
      extraTokens.push(token);
      if (index + 1 < tokens.length && !tokens[index + 1].startsWith('-')) {
        extraTokens.push(tokens[index + 1]);
        index += 2;
        continue;
      }
      index += 1;
      continue;
    }
    positionalTokens.push(token);
    index += 1;
  }

  applyPositionalTokens(parsed, positionalTokens);
  parsed.extraCommandOptions = extraTokens.join(' ').trim() || undefined;

  if (parsed.type === 'WATCH' && !parsed.outputExpression) {
    parsed.outputExpression = '{params,returnObj,throwExp}';
  }
  if (!parsed.maxMatches) {
    parsed.maxMatches = 5;
  }
  if (parsed.type === 'WATCH' && !parsed.stackDepth) {
    parsed.stackDepth = 2;
  }
  return parsed;
}

export function deriveOutputConfig(expression?: string): { presetParts: string[]; customParts: string } {
  const trimmed = normalizeOptionalText(expression);
  if (!trimmed) {
    return { presetParts: ['params', 'returnObj', 'throwExp'], customParts: '' };
  }
  const normalized = trimmed.startsWith('{') && trimmed.endsWith('}')
    ? trimmed.slice(1, -1)
    : trimmed;
  const parts = splitTopLevel(normalized)
    .map((item) => item.trim())
    .filter(Boolean);
  const presetParts = parts.filter((item) => WATCH_OUTPUT_PRESET_SET.has(item));
  const customParts = parts.filter((item) => !WATCH_OUTPUT_PRESET_SET.has(item)).join('\n');
  return { presetParts, customParts };
}

export function createInitialRuleFormValues(rule?: DiagnosticRule | null): DiagnosticRuleFormValues {
  const defaults: DiagnosticRuleFormValues = {
    name: '',
    type: 'WATCH',
    selectedProcessName: '',
    targetClassPattern: '',
    targetMethodPattern: '',
    outputExpression: '{params,returnObj,throwExp}',
    outputPresetParts: ['params', 'returnObj', 'throwExp'],
    outputCustomParts: '',
    conditionExpression: '',
    regexMatch: false,
    classloaderHash: '',
    matchLimit: undefined,
    watchBefore: false,
    watchExceptionExit: false,
    watchSuccessExit: false,
    watchFinally: false,
    skipJdkMethodMode: 'DEFAULT',
    extraCommandOptions: '',
    stackDepth: 2,
    maxMatches: 5,
    executionTimeoutMs: 30000,
    enabled: true,
    notes: '',
  };
  if (!rule) {
    return defaults;
  }
  const outputConfig = deriveOutputConfig(rule.outputExpression);
  const commandOptions = parseCommandOptions(rule.commandOptions);
  return {
    ...defaults,
    ...rule,
    outputPresetParts: outputConfig.presetParts,
    outputCustomParts: outputConfig.customParts,
    regexMatch: commandOptions.regexMatch,
    classloaderHash: commandOptions.classloaderHash,
    matchLimit: commandOptions.matchLimit,
    watchBefore: commandOptions.watchBefore,
    watchExceptionExit: commandOptions.watchExceptionExit,
    watchSuccessExit: commandOptions.watchSuccessExit,
    watchFinally: commandOptions.watchFinally,
    skipJdkMethodMode: commandOptions.skipJdkMethodMode,
    extraCommandOptions: commandOptions.extraCommandOptions,
  };
}

function composeWatchOutputExpression(presetParts?: string[], customParts?: string): string | undefined {
  const parts = [
    ...(presetParts ?? []).map((item) => item.trim()).filter(Boolean),
    ...normalizeOptionalText(customParts)?.split('\n').map((item) => item.trim()).filter(Boolean) ?? [],
  ];
  if (!parts.length) {
    return undefined;
  }
  return `{${parts.join(',')}}`;
}

function composeCommandOptions(values: Partial<DiagnosticRuleFormValues>): string | undefined {
  const parts: string[] = [];
  if (values.regexMatch) {
    parts.push('-E');
  }
  if (values.classloaderHash?.trim()) {
    parts.push('-c', values.classloaderHash.trim());
  }
  if (values.matchLimit != null) {
    parts.push('-m', String(values.matchLimit));
  }
  if ((values.type ?? 'WATCH') === 'WATCH') {
    if (values.watchBefore) {
      parts.push('-b');
    }
    if (values.watchExceptionExit) {
      parts.push('-e');
    }
    if (values.watchSuccessExit) {
      parts.push('-s');
    }
    if (values.watchFinally) {
      parts.push('-f');
    }
  }
  if ((values.type ?? 'WATCH') !== 'WATCH' && values.skipJdkMethodMode && values.skipJdkMethodMode !== 'DEFAULT') {
    parts.push('--skipJDKMethod', values.skipJdkMethodMode === 'TRUE' ? 'true' : 'false');
  }
  const extra = normalizeOptionalText(values.extraCommandOptions);
  if (extra) {
    parts.push(extra);
  }
  return parts.length ? parts.join(' ') : undefined;
}

function parseCommandOptions(commandOptions?: string): {
  regexMatch: boolean;
  classloaderHash: string;
  matchLimit?: number;
  watchBefore: boolean;
  watchExceptionExit: boolean;
  watchSuccessExit: boolean;
  watchFinally: boolean;
  skipJdkMethodMode: SkipJdkMethodMode;
  extraCommandOptions: string;
} {
  const parsed = {
    regexMatch: false,
    classloaderHash: '',
    matchLimit: undefined as number | undefined,
    watchBefore: false,
    watchExceptionExit: false,
    watchSuccessExit: false,
    watchFinally: false,
    skipJdkMethodMode: 'DEFAULT' as SkipJdkMethodMode,
    extraCommandOptions: '',
  };
  const tokens = tokenizeScript(commandOptions ?? '');
  const extraTokens: string[] = [];
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (token === '-E') {
      parsed.regexMatch = true;
      continue;
    }
    if (token === '-b') {
      parsed.watchBefore = true;
      continue;
    }
    if (token === '-e') {
      parsed.watchExceptionExit = true;
      continue;
    }
    if (token === '-s') {
      parsed.watchSuccessExit = true;
      continue;
    }
    if (token === '-f') {
      parsed.watchFinally = true;
      continue;
    }
    if (token === '-c' || token === '--classloaderHash') {
      parsed.classloaderHash = tokens[index + 1] ?? '';
      index += 1;
      continue;
    }
    if (token === '-m' || token === '--maxMatch') {
      parsed.matchLimit = Number(tokens[index + 1] ?? 50);
      index += 1;
      continue;
    }
    if (token === '--skipJDKMethod') {
      const value = (tokens[index + 1] ?? '').toLowerCase();
      if (value === 'true' || value === 'false') {
        parsed.skipJdkMethodMode = value === 'true' ? 'TRUE' : 'FALSE';
        index += 1;
      } else {
        parsed.skipJdkMethodMode = 'TRUE';
      }
      continue;
    }
    extraTokens.push(token);
    if (index + 1 < tokens.length && !tokens[index + 1].startsWith('-')) {
      extraTokens.push(tokens[index + 1]);
      index += 1;
    }
  }
  parsed.extraCommandOptions = extraTokens.join(' ').trim();
  return parsed;
}

function normalizeOptionalText(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function applyPositionalTokens(parsed: ParsedDiagnosticScript, positionalTokens: string[]) {
  if (parsed.type === 'TRACE' || parsed.type === 'STACK') {
    parsed.targetClassPattern = positionalTokens[0] ?? '';
    parsed.targetMethodPattern = positionalTokens[1] ?? '';
    parsed.conditionExpression = positionalTokens[2];
    return;
  }

  if (!positionalTokens.length) {
    return;
  }

  if (looksLikeOutputExpression(positionalTokens[0])) {
    parsed.outputExpression = positionalTokens[0];
    parsed.conditionExpression = positionalTokens[1];
    return;
  }

  parsed.targetClassPattern = positionalTokens[0] ?? '';

  if (positionalTokens.length === 1) {
    return;
  }

  if (looksLikeOutputExpression(positionalTokens[1])) {
    parsed.outputExpression = positionalTokens[1];
    parsed.conditionExpression = positionalTokens[2];
    return;
  }

  parsed.targetMethodPattern = positionalTokens[1] ?? '';

  if (positionalTokens.length === 2) {
    return;
  }

  if (looksLikeOutputExpression(positionalTokens[2])) {
    parsed.outputExpression = positionalTokens[2];
    parsed.conditionExpression = positionalTokens[3];
    return;
  }

  parsed.conditionExpression = positionalTokens[2];
}

function quote(value: string): string {
  return `'${value.replace(/'/g, "\\'")}'`;
}

function looksLikeOutputExpression(value?: string): boolean {
  if (!value) {
    return false;
  }
  const trimmed = value.trim();
  return trimmed.startsWith('{') && trimmed.endsWith('}');
}

function tokenizeScript(script: string): string[] {
  const tokens: string[] = [];
  let current = '';
  let quoteChar = '';
  let escaping = false;
  for (const character of script.trim()) {
    if (escaping) {
      current += character;
      escaping = false;
      continue;
    }
    if (character === '\\') {
      escaping = true;
      continue;
    }
    if (quoteChar) {
      if (character === quoteChar) {
        quoteChar = '';
      } else {
        current += character;
      }
      continue;
    }
    if (character === '\'' || character === '"') {
      quoteChar = character;
      continue;
    }
    if (/\s/.test(character)) {
      if (current) {
        tokens.push(current);
        current = '';
      }
      continue;
    }
    current += character;
  }
  if (current) {
    tokens.push(current);
  }
  return tokens;
}

function splitTopLevel(value: string): string[] {
  const parts: string[] = [];
  let current = '';
  let depth = 0;
  for (const character of value) {
    if (character === ',' && depth === 0) {
      parts.push(current);
      current = '';
      continue;
    }
    if (character === '{' || character === '[' || character === '(') {
      depth += 1;
    } else if (character === '}' || character === ']' || character === ')') {
      depth = Math.max(0, depth - 1);
    }
    current += character;
  }
  if (current) {
    parts.push(current);
  }
  return parts;
}
