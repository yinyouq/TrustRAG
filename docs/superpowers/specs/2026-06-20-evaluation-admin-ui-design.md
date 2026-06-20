# TrustRAG 评估管理台设计

## 1. 目标

为现有 TrustRAG 评估模块补齐可实际使用的管理前端，并修复前端闭环依赖的后端接口缺口和严重逻辑错误。管理台采用 Vue 3 技术栈，覆盖测试集、测试样例、评估任务、评估报告、Before/After 对比和知识治理看板。

本次不将登录认证扩展成新的身份系统。前端支持配置 API 基地址并携带 Bearer Token，但 Spring Security/JWT 的身份签发、用户管理和全局 RBAC 应作为 Admin API 的独立安全项目完成。

## 2. 文档实现核对

### 2.1 已实现

- `trust-rag-evaluation` 独立模块及 PostgreSQL/H2/MySQL 数据表。
- `evaluationMode`、EVAL Trace 标记以及评估调用不更新知识使用次数、不触发缺口和候选抽取。
- 测试集和测试样例创建、查询、expected knowledge 维护。
- 异步评估任务、取消和进度记录。
- Recall@5/10、Precision@5/10、MRR、NDCG@5/10。
- Faithfulness、Answer Correctness、Answer Relevance 和 Hallucination Score 基础 Judge。
- 汇总报告、Before/After 对比、治理快照和趋势 API。
- 定时治理快照调度配置。

### 2.2 部分实现或未实现

- 测试集和样例缺少编辑、删除；样例缺少 CSV 导入。
- Judge 详情已经入库，但没有管理 API；没有人工复核流程。
- Judge 非法 JSON 被当作空评分返回，不会按设计重试。
- `case-timeout-seconds` 已配置但执行器没有使用；任务顶层异常可能让状态表达不准确。
- Judge 未保存 `unsupported_claims`、`missing_points`、`wrong_points` 等结构化诊断。
- `eval_result` 未保存实际 retrieved/used knowledge ID 列表。
- 治理污染率当前按低可信知识使用率近似，不等同于文档定义的高可信知识回滚/降级率；缺口解决率也使用 Trace/反馈近似。
- Spring Batch 未接入。文档允许第一版使用线程池，因此不阻塞当前管理台。
- Spring Security/JWT 未实现。
- Vue 3/ECharts 前端完全未实现。
- 评估模块自动化测试目前主要覆盖检索指标，Runner、Judge、报告、Controller 和 JDBC 评估仓库覆盖不足。

## 3. 技术方案

新增独立目录 `trust-rag-admin-ui`，不加入 Maven reactor，避免 Java SDK 构建被 Node 环境强制绑定。

- Vue 3 + TypeScript + Vite
- Element Plus
- ECharts + vue-echarts
- Pinia
- Vue Router
- Axios
- Vitest + Vue Test Utils + MSW

开发环境通过 Vite 代理访问 `/trust-rag/admin/eval`。生产构建输出静态资源，可交给 Nginx、网关或宿主 Spring Boot 应用托管。

## 4. 视觉与布局

采用用户确认的 B 方案“任务导向型工作台”。

- 暖白背景、深绿色主色和低饱和提示色。
- 顶部主导航，首页强调综合质量、失败样例和最近评估活动。
- 数据集、样例、任务等高频管理页面使用紧凑表格，不牺牲批量操作效率。
- 图表用于趋势和 Before/After 差异，不用图表替代精确数值表格。
- 支持桌面与平板宽度；窄屏表格允许横向滚动。

## 5. 页面与路由

### 5.1 `/eval`

评估概览。展示最近成功报告形成的综合质量摘要、失败样例数、运行中任务、最近任务以及进入创建评估的主操作。

### 5.2 `/eval/datasets`

测试集列表和创建/编辑抽屉。支持租户、项目和启用状态过滤；查看样例、启动评估、禁用或删除测试集。

### 5.3 `/eval/cases`

按测试集管理样例。支持问题、标准答案、标签、难度、Scope、expected knowledge 编辑；支持 CSV 导入及逐行错误反馈。

### 5.4 `/eval/runs`

创建评估任务，展示任务状态、成功/失败数、进度、耗时和 Before/After 分组；运行中任务自动轮询并可取消。

### 5.5 `/eval/reports`

按任务查看检索指标、生成指标、延迟和逐题结果。结果详情抽屉展示问题、标准答案、实际答案、错误信息以及 Judge 原始输出。

### 5.6 `/eval/compare`

选择同一测试集的 before/after 任务。展示指标原值、变化量、柱状图和后端生成的结论；不允许跨测试集比较。

### 5.7 `/eval/governance`

展示候选通过率、知识复用率、污染率、隐私泄漏率和缺口解决率；支持租户、项目、时间范围过滤并手动生成快照。

## 6. 后端补齐

### 6.1 数据集与样例

- 增加数据集更新和删除接口。
- 增加样例更新和删除接口。
- 删除采用约束明确的策略：存在评估运行记录的数据集不可物理删除，返回冲突错误；无历史记录时级联删除样例和 expected knowledge。
- 增加 multipart CSV 导入，返回成功数和带行号的失败原因；单行失败不回滚其他合法行。

### 6.2 报告诊断

- 增加 `GET /results/{resultId}/judge-details`。
- 保留 Judge prompt/raw output 的配置开关；字段未保存时前端显示“未配置保存”，不伪造内容。

### 6.3 逻辑修复

- Judge JSON 解析无效时抛出受控异常，触发配置的重试次数；最终失败保存 FAILED 诊断而不是伪装成有效评分。
- EvalRunner 捕获任务级异常并可靠结束任务状态；报告生成失败必须记录到 run error，不能把任务留在 RUNNING 或误标成功。
- 暂不引入 Spring Batch，也不改变现有逐 case 执行顺序。

## 7. 前端数据流

页面通过 typed API client 调用后端。Pinia 只保存跨页共享的筛选 Scope、数据集索引和用户 Token；列表、表单和图表状态保留在页面组件中，避免全局状态膨胀。

任务轮询只在 `/eval/runs` 页面存在 PENDING/RUNNING 任务时启动，终态或离开页面后清理定时器。所有百分比在展示层统一格式化，`null` 表示未评估，不能显示为 0%。

开发 Mock API 仅在 `VITE_USE_MOCKS=true` 时加载。生产构建默认关闭，确保模拟数据不会进入真实环境。

## 8. 错误处理

- Axios 拦截器将 RFC 7807、Spring Validation 和普通错误响应归一化。
- 表单保留后端字段级错误；列表错误提供重试，不清空用户筛选条件。
- 401/403 显示认证或权限提示，不跳转到不存在的登录页。
- CSV 导入同时展示成功数和失败行。
- 图表对空数据、单点数据和部分指标为空提供明确空状态。

## 9. 测试与验收

### 9.1 后端

- Service/Repository 测试覆盖更新、删除约束和 CSV 行级失败。
- Judge 测试验证非法 JSON 会重试且最终失败被保存。
- Runner 测试验证任务级异常能够进入终态。
- Controller 测试覆盖新增 API 的校验和状态码。

### 9.2 前端

- API client 测试覆盖序列化、错误归一化和 Token。
- 组件测试覆盖指标空值、状态标签、表单校验、轮询清理和 Before/After 同数据集限制。
- `npm run typecheck`、`npm run test`、`npm run build` 全部通过。
- 使用 Mock API 启动管理台，在浏览器验证七条路由、创建流程、任务轮询、报告详情、对比和治理趋势。
- Maven `verify` 通过，保证新增后端能力不破坏现有模块。

## 10. 非本次范围

- 身份签发、登录页、用户管理和完整 RBAC。
- Spring Batch 大规模分片执行。
- Judge 人工复核工作流和结构化 unsupported claims 数据库迁移。
- 真实 gap_event、privacy_event 生产链路以及治理指标语义重构。
- 知识快照和严格可重复的 Before/After 隔离环境。
