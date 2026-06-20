# TrustRAG Evaluation Admin UI

TrustRAG 评估管理台使用 Vue 3、TypeScript、Element Plus 和 ECharts，覆盖：

- 评估概览
- 测试集与测试样例管理
- CSV 样例导入
- 评估任务创建、进度和取消
- 检索/生成指标报告与 Judge 原始输出
- Before/After 对比
- 知识治理指标与趋势

## 环境

- Node.js 22.12+
- Corepack
- pnpm 11.8.0（由 `packageManager` 固定）

## 开发

```powershell
corepack pnpm install --frozen-lockfile
$env:VITE_USE_MOCKS='true'
corepack pnpm dev
```

Mock 只会在 `DEV` 且 `VITE_USE_MOCKS=true` 时加载。默认 API 地址为 `/trust-rag/admin/eval`，Vite 会将 `/trust-rag` 代理到 `http://localhost:8080`。

连接真实后端：

```powershell
$env:VITE_USE_MOCKS='false'
$env:VITE_API_BASE_URL='/trust-rag/admin/eval'
corepack pnpm dev
```

Bearer Token 可在页面右上角“环境与 Scope”中设置，保存在浏览器 localStorage。后端当前不负责签发 Token，生产环境应由宿主应用或网关实现认证与 RBAC。

## 验证

```powershell
corepack pnpm typecheck
corepack pnpm test:run
corepack pnpm build
```

构建产物默认输出到 `dist/`。受限环境可以通过 `VITE_OUT_DIR` 指定其他目录：

```powershell
$env:VITE_OUT_DIR="$env:TEMP\trust-rag-admin-ui-dist"
corepack pnpm build
```

## 部署

将 `dist/` 作为 SPA 静态资源交给 Nginx、网关或宿主 Spring Boot 应用托管，并将 `/trust-rag/admin/eval` 转发到启用了 `trust-rag.admin-api.enabled=true` 的后端。服务器需将未知前端路由回退到 `index.html`。
