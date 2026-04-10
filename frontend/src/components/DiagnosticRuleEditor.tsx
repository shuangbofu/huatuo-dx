import { Button, Checkbox, Form, Input, InputNumber, Modal, Radio, Segmented, Space, Switch, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import type { DiagnosticRule } from '../lib/api';
import {
  buildDiagnosticScript,
  buildRulePayload,
  buildWatchOutputExpression,
  createInitialRuleFormValues,
  deriveOutputConfig,
  parseDiagnosticScript,
  WATCH_OUTPUT_PRESETS,
  type DiagnosticRuleFormValues,
} from '../lib/diagnostic-script';

interface DiagnosticRuleEditorProps {
  open: boolean;
  title: string;
  initialRule?: DiagnosticRule | null;
  onCancel: () => void;
  onSubmit: (payload: Partial<DiagnosticRule>) => Promise<void>;
}

type EditorMode = 'config' | 'script';

export function DiagnosticRuleEditor({
  open,
  title,
  initialRule,
  onCancel,
  onSubmit,
}: DiagnosticRuleEditorProps) {
  const [form] = Form.useForm<DiagnosticRuleFormValues>();
  const [editorMode, setEditorMode] = useState<EditorMode>('config');
  const [scriptValue, setScriptValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [messageApi, holder] = message.useMessage();

  useEffect(() => {
    if (!open) {
      return;
    }
    const initialValues = createInitialRuleFormValues(initialRule);
    form.setFieldsValue({
      ...initialValues,
    });
    setEditorMode('config');
    setScriptValue(buildDiagnosticScript(initialValues));
  }, [open, initialRule, form]);

  const type = Form.useWatch('type', form) ?? 'WATCH';
  const watchedValues = Form.useWatch([], form);

  const scriptPreview = useMemo(() => buildDiagnosticScript(watchedValues ?? {}), [watchedValues]);
  const outputExpressionPreview = useMemo(() => buildWatchOutputExpression(watchedValues ?? {}), [watchedValues]);

  async function handleSubmit() {
    try {
      let mergedValues: DiagnosticRuleFormValues;
      if (editorMode === 'script') {
        const baseValues = await form.validateFields(['name', 'type', 'executionTimeoutMs', 'enabled', 'notes']);
        const parsed = parseDiagnosticScript(scriptValue);
        const outputConfig = deriveOutputConfig(parsed.outputExpression);
        mergedValues = {
          ...baseValues,
          ...parsed,
          outputPresetParts: outputConfig.presetParts,
          outputCustomParts: outputConfig.customParts,
        };
        form.setFieldsValue(mergedValues);
      } else {
        mergedValues = await form.validateFields();
      }

      setSaving(true);
      await onSubmit(buildRulePayload(mergedValues));
    } catch (error) {
      if (error instanceof Error && editorMode === 'script') {
        messageApi.error(error.message);
      }
    } finally {
      setSaving(false);
    }
  }

  function switchMode(nextMode: EditorMode) {
    if (nextMode === editorMode) {
      return;
    }
    if (nextMode === 'script') {
      setScriptValue(scriptPreview);
      setEditorMode('script');
      return;
    }
    try {
      const parsed = parseDiagnosticScript(scriptValue);
      const outputConfig = deriveOutputConfig(parsed.outputExpression);
      form.setFieldsValue({
        ...parsed,
        outputPresetParts: outputConfig.presetParts,
        outputCustomParts: outputConfig.customParts,
      });
      setEditorMode('config');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '脚本无法解析，请先修正');
    }
  }

  return (
    <>
      {holder}
      <Modal
        className="dx-modal"
        open={open}
        onCancel={onCancel}
        width={840}
        title={title}
        footer={
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void handleSubmit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form layout="vertical" form={form}>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item name="name" label="规则名称" rules={[{ required: true }]}>
              <Input placeholder="例如：订单创建 trace" />
            </Form.Item>
            <Form.Item name="type" label="诊断类型" rules={[{ required: true }]}>
              <Radio.Group
                optionType="button"
                buttonStyle="solid"
                options={[
                  { label: '监看', value: 'WATCH' },
                  { label: '链路', value: 'TRACE' },
                  { label: '堆栈', value: 'STACK' },
                ]}
              />
            </Form.Item>
          </div>
          <div className="mb-3 flex items-center justify-between gap-3">
            <div className="dx-panel-title">编辑方式</div>
            <Segmented
              value={editorMode}
              options={[
                { label: '配置方式', value: 'config' },
                { label: '脚本方式', value: 'script' },
              ]}
              onChange={(value) => switchMode(value as EditorMode)}
            />
          </div>

          {editorMode === 'config' ? (
            <>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <Form.Item name="selectedProcessName" label="默认 Java 进程显示名">
                  <Input placeholder="例如：com.demo.Application" />
                </Form.Item>
                <div />
              </div>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <Form.Item name="targetClassPattern" label="类匹配" rules={[{ required: true }]}>
                  <Input placeholder="com.demo.order.OrderService" />
                </Form.Item>
                <Form.Item name="targetMethodPattern" label="方法匹配" rules={[{ required: true }]}>
                  <Input placeholder="create*" />
                </Form.Item>
              </div>

              {type === 'WATCH' ? (
                <div className="border border-lime-200 bg-lime-50/60 px-3 py-3">
                  <div className="mb-2 text-sm font-medium text-slate-700">watch 配置</div>
                  <Form.Item name="outputPresetParts" className="mb-2">
                    <Checkbox.Group options={WATCH_OUTPUT_PRESETS.map((item) => ({ label: item.label, value: item.value }))} />
                  </Form.Item>
                  <Form.Item name="outputCustomParts" className="mb-0">
                    <Input.TextArea rows={3} placeholder={'自定义输出项，一行一个\n例如：params[0].id\ntarget.orderNo'} />
                  </Form.Item>
                  <div className="mt-2 text-xs text-slate-500">
                    输出表达式预览: <code>{outputExpressionPreview}</code>
                  </div>
                  <div className="mt-1 text-xs text-slate-500">
                    完整脚本预览: <code>{scriptPreview}</code>
                  </div>
                  <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item name="conditionExpression" label="条件表达式" className="mb-0">
                      <Input placeholder="#cost > 100 || throwExp != null" />
                    </Form.Item>
                    <Form.Item name="stackDepth" label="对象展开层级" className="mb-0">
                      <InputNumber className="w-full" min={1} max={8} />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                    <div>命中后再判断这段表达式，常用于按耗时、异常、参数内容过滤。</div>
                    <div>`-x`，控制对象展开深度，越大看到的字段越多，但输出也会更重。</div>
                  </div>
                  <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item name="classloaderHash" label="ClassLoader Hash" className="mb-0">
                      <Input placeholder="可选，例如 6d06d69c" />
                    </Form.Item>
                    <Form.Item name="matchLimit" label="类匹配上限(-m)" className="mb-0">
                      <InputNumber className="w-full" min={1} max={200} />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                    <div>同名类很多时，用它把命令限定到某个 ClassLoader。</div>
                    <div>`-m`，限制类匹配数量，避免表达式太宽把太多类都挂上。</div>
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-4 md:grid-cols-5">
                    <Form.Item name="regexMatch" valuePropName="checked" className="mb-0">
                      <Checkbox>正则匹配(-E)</Checkbox>
                    </Form.Item>
                    <Form.Item name="watchBefore" valuePropName="checked" className="mb-0">
                      <Checkbox>入参(-b)</Checkbox>
                    </Form.Item>
                    <Form.Item name="watchExceptionExit" valuePropName="checked" className="mb-0">
                      <Checkbox>异常(-e)</Checkbox>
                    </Form.Item>
                    <Form.Item name="watchSuccessExit" valuePropName="checked" className="mb-0">
                      <Checkbox>成功(-s)</Checkbox>
                    </Form.Item>
                    <Form.Item name="watchFinally" valuePropName="checked" className="mb-0">
                      <Checkbox>结束(-f)</Checkbox>
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-2 gap-4 text-xs text-slate-500 md:grid-cols-5">
                    <div>把类名、方法名按正则处理。</div>
                    <div>方法进入前触发，适合看原始入参。</div>
                    <div>只在抛异常时触发。</div>
                    <div>只在正常返回时触发。</div>
                    <div>方法结束就触发，成功和异常都会进来。</div>
                  </div>
                  <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item name="maxMatches" label="匹配次数" className="mb-0">
                      <InputNumber className="w-full" min={1} max={50} />
                    </Form.Item>
                    <Form.Item name="executionTimeoutMs" label="超时(ms)" className="mb-0">
                      <InputNumber className="w-full" min={1000} max={60000} step={1000} />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                    <div>命中多少次后自动结束这次监控。</div>
                    <div>只限制这次任务最多挂多久，避免长期占着不释放。</div>
                  </div>
                </div>
              ) : type === 'TRACE' ? (
                <>
                  <div className="border border-amber-200 bg-amber-50/50 px-3 py-3">
                    <div className="mb-2 text-sm font-medium text-slate-700">trace 配置</div>
                    <Form.Item name="conditionExpression" label="条件表达式">
                      <Input placeholder="#cost > 100" />
                    </Form.Item>
                    <div className="text-xs text-slate-500">只追踪满足条件的调用，例如只看慢于 100ms 的一次链路。</div>
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                      <Form.Item name="classloaderHash" label="ClassLoader Hash" className="mb-0">
                        <Input placeholder="可选，例如 6d06d69c" />
                      </Form.Item>
                      <Form.Item name="matchLimit" label="类匹配上限(-m)" className="mb-0">
                        <InputNumber className="w-full" min={1} max={200} />
                      </Form.Item>
                    </div>
                    <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                      <div>同名类很多时，用它锁定具体 ClassLoader。</div>
                      <div>`-m`，限制表达式最多命中多少个类。</div>
                    </div>
                    <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                      <Form.Item name="regexMatch" valuePropName="checked" className="mb-0">
                        <Checkbox>正则匹配(-E)</Checkbox>
                      </Form.Item>
                      <Form.Item name="skipJdkMethodMode" label="是否跳过 JDK 方法" className="mb-0">
                        <Radio.Group
                          options={[
                            { label: '不设置', value: 'DEFAULT' },
                            { label: '是', value: 'TRUE' },
                            { label: '否', value: 'FALSE' },
                          ]}
                          optionType="button"
                          buttonStyle="solid"
                        />
                      </Form.Item>
                    </div>
                    <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                      <div>把类名、方法名按正则处理。</div>
                      <div>控制是否跳过 JDK 内部方法。`false` 时链路会更完整，但输出也更长。</div>
                    </div>
                    <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                      <Form.Item name="maxMatches" label="匹配次数">
                        <InputNumber className="w-full" min={1} max={50} />
                      </Form.Item>
                      <Form.Item name="executionTimeoutMs" label="超时(ms)">
                        <InputNumber className="w-full" min={1000} max={60000} step={1000} />
                      </Form.Item>
                    </div>
                    <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                      <div>命中多少次后自动结束这次 trace。</div>
                      <div>只限制这次任务挂多久，不是方法超时时间。</div>
                    </div>
                  </div>
                </>
              ) : (
                <div className="border border-emerald-200 bg-emerald-50/50 px-3 py-3">
                  <div className="mb-2 text-sm font-medium text-slate-700">stack 配置</div>
                  <Form.Item name="conditionExpression" label="条件表达式">
                    <Input placeholder="#cost > 100" />
                  </Form.Item>
                  <div className="text-xs text-slate-500">只在满足条件时抓取调用堆栈，适合看某个慢调用或异常调用是从哪里进来的。</div>
                  <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item name="classloaderHash" label="ClassLoader Hash" className="mb-0">
                      <Input placeholder="可选，例如 6d06d69c" />
                    </Form.Item>
                    <Form.Item name="matchLimit" label="类匹配上限(-m)" className="mb-0">
                      <InputNumber className="w-full" min={1} max={200} />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                    <div>同名类很多时，用它锁定具体 ClassLoader。</div>
                    <div>`-m`，限制表达式最多命中多少个类。</div>
                  </div>
                  <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item name="regexMatch" valuePropName="checked" className="mb-0">
                      <Checkbox>正则匹配(-E)</Checkbox>
                    </Form.Item>
                    <Form.Item name="skipJdkMethodMode" label="是否跳过 JDK 方法" className="mb-0">
                      <Radio.Group
                        options={[
                          { label: '不设置', value: 'DEFAULT' },
                          { label: '是', value: 'TRUE' },
                          { label: '否', value: 'FALSE' },
                        ]}
                        optionType="button"
                        buttonStyle="solid"
                      />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                    <div>把类名、方法名按正则处理。</div>
                    <div>控制是否跳过 JDK 内部方法。`false` 时堆栈会更完整，但输出也更长。</div>
                  </div>
                  <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item name="maxMatches" label="匹配次数">
                      <InputNumber className="w-full" min={1} max={50} />
                    </Form.Item>
                    <Form.Item name="executionTimeoutMs" label="超时(ms)">
                      <InputNumber className="w-full" min={1000} max={60000} step={1000} />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-1 gap-4 text-xs text-slate-500 md:grid-cols-2">
                    <div>命中多少次后自动结束这次 stack。</div>
                    <div>只限制这次任务挂多久，不是方法超时时间。</div>
                  </div>
                </div>
              )}
            </>
          ) : (
            <>
              <Input.TextArea
                value={scriptValue}
                onChange={(event) => setScriptValue(event.target.value)}
                rows={8}
                placeholder={"watch com.demo.order.OrderService create* '{params,returnObj,throwExp}' '#cost > 100' -x 2 -n 1\n或\ntrace com.demo.order.OrderService create* '#cost > 100' -n 1"}
              />
              <div className="mt-2 text-xs text-slate-500">
                支持完整诊断命令。切回“配置方式”时会自动解析成类、方法、条件、输出表达式等字段。
              </div>
              <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
                <Form.Item name="executionTimeoutMs" label="超时(ms)">
                  <InputNumber className="w-full" min={1000} max={60000} step={1000} />
                </Form.Item>
                <Form.Item name="notes" label="备注">
                  <Input />
                </Form.Item>
              </div>
            </>
          )}

          {editorMode === 'config' ? (
            <>
              <Form.Item name="notes" label="备注">
                <Input.TextArea rows={3} />
              </Form.Item>
              <Form.Item name="enabled" label="启用" valuePropName="checked">
                <Switch />
              </Form.Item>
            </>
          ) : (
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
