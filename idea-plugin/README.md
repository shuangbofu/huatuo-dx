# Huatuo DX IDEA Plugin

在方法上右键，可直接创建以下诊断规则：

- 监看
- 链路
- 堆栈

设置入口：

- `Settings` -> `Tools` -> `Huatuo Diagnose`

需要配置：

- 服务端地址
- 登录令牌
- 访问密钥
- 签名密钥
- 默认 Java 进程显示名

插件会调用 manager 的插件接入接口：

- `POST /api/plugin/diagnostic-rules/import`

并使用以下头做签名验签：

- `Authorization: Bearer <token>`
- `X-Huatuo-Access-Key`
- `X-Huatuo-Timestamp`
- `X-Huatuo-Nonce`
- `X-Huatuo-Signature`
