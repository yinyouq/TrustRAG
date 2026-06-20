# TrustRAG Evaluation Admin UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐评估管理所需后端契约和关键可靠性问题，并交付采用任务导向布局的 Vue 3 评估管理台。

**Architecture:** 后端继续保持 `trust-rag-evaluation` 领域服务、`trust-rag-storage-jdbc` 持久化和 `trust-rag-admin-api` HTTP 适配三层边界。前端作为独立 `trust-rag-admin-ui` SPA，通过 typed Axios client 使用 Admin API，Pinia 只保存跨页面 Scope 和认证 Token，ECharts 负责报告、对比和治理趋势。

**Tech Stack:** Java 17、Spring Boot 3.5.15、Spring MVC、Apache Commons CSV、JUnit 5、AssertJ、Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts、Vitest、Vue Test Utils、MSW、pnpm/Corepack。

---

## File Structure

### Backend

- `trust-rag-evaluation/.../EvaluationRepository.java`: 增加更新/删除约束所需仓库契约。
- `trust-rag-evaluation/.../EvalDatasetService.java`: 数据集更新、删除业务规则。
- `trust-rag-evaluation/.../EvalCaseService.java`: 样例查询、更新、删除业务规则。
- `trust-rag-evaluation/.../EvalCaseCsvImportService.java`: RFC 4180 CSV 解析、逐行校验和导入结果。
- `trust-rag-evaluation/.../EvaluationResourceConflictException.java`: 历史评估阻止删除时的领域异常。
- `trust-rag-evaluation/.../DefaultGenerationJudgeService.java`: 正确重试非法 JSON 并保存最终诊断。
- `trust-rag-evaluation/.../DefaultEvalRunner.java`: 任务级异常终态处理。
- `trust-rag-storage-jdbc/.../JdbcEvaluationRepository.java`: 删除约束查询和事务删除。
- `trust-rag-admin-api/.../TrustRagEvaluationController.java`: CRUD、CSV、Judge 明细 HTTP 接口。
- `trust-rag-admin-api/.../TrustRagExceptionHandler.java`: 409 Conflict 映射。

### Frontend

- `trust-rag-admin-ui/src/api`: 类型、Axios client、评估 API。
- `trust-rag-admin-ui/src/components`: 指标卡、状态标签、图表和通用空状态。
- `trust-rag-admin-ui/src/layouts`: 任务导向型应用外壳。
- `trust-rag-admin-ui/src/views`: 概览及六个设计文档页面。
- `trust-rag-admin-ui/src/stores`: Scope/Token 和数据集索引。
- `trust-rag-admin-ui/src/mocks`: 仅开发环境启用的 MSW 场景。
- `trust-rag-admin-ui/src/tests`: Vitest 初始化和共享 fixture。

---

### Task 1: Establish Backend Contract Tests

**Files:**
- Create: `trust-rag-evaluation/src/test/java/io/github/trustrag/evaluation/InMemoryEvaluationRepository.java`
- Create: `trust-rag-evaluation/src/test/java/io/github/trustrag/evaluation/EvalDatasetServiceTest.java`
- Create: `trust-rag-evaluation/src/test/java/io/github/trustrag/evaluation/EvalCaseServiceTest.java`
- Modify: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/EvaluationRepository.java`

- [ ] **Step 1: Add a reusable in-memory repository test double**

Implement every `EvaluationRepository` method with maps/lists and expose run/result existence so deletion constraints can be tested without JDBC.

- [ ] **Step 2: Write failing dataset update/delete tests**

```java
@Test
void refusesToDeleteDatasetWithHistoricalRuns() {
    repository.saveDataset(dataset(1L));
    repository.createRun(run(10L, 1L));

    assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(EvaluationResourceConflictException.class);
}
```

- [ ] **Step 3: Write failing case update/delete tests**

```java
@Test
void preservesCreationTimeWhenUpdatingCase() {
    EvalCase saved = repository.saveCase(evalCase(1L, Instant.EPOCH));
    EvalCase updated = service.update(saved.id(), updateRequest(saved));
    assertThat(updated.createdAt()).isEqualTo(Instant.EPOCH);
    assertThat(updated.updatedAt()).isAfter(Instant.EPOCH);
}
```

- [ ] **Step 4: Extend repository contract minimally**

Add `deleteDataset`, `datasetHasRuns`, `deleteCase`, and `caseHasResults`. Do not add generic CRUD abstractions.

- [ ] **Step 5: Run tests and confirm RED**

Run: `mvn -s maven-settings.xml -pl trust-rag-evaluation -am -Dtest=EvalDatasetServiceTest,EvalCaseServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compile/test failure because service methods and conflict exception do not exist.

---

### Task 2: Implement Dataset and Case Lifecycle

**Files:**
- Create: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/EvaluationResourceConflictException.java`
- Modify: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/EvalDatasetService.java`
- Modify: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/EvalCaseService.java`
- Modify: `trust-rag-storage-jdbc/src/main/java/io/github/trustrag/storage/jdbc/JdbcEvaluationRepository.java`
- Test: `trust-rag-storage-jdbc/src/test/java/io/github/trustrag/storage/jdbc/JdbcRepositoriesTest.java`

- [ ] **Step 1: Implement explicit update/delete service methods**

Dataset update loads the existing record, preserves `createdBy/createdAt`, applies mutable fields and writes a new `updatedAt`. Dataset deletion checks `datasetHasRuns`; case deletion checks `caseHasResults`.

- [ ] **Step 2: Implement transactional JDBC deletion**

```sql
DELETE FROM eval_case_expected_knowledge
WHERE eval_case_id IN (SELECT id FROM eval_case WHERE dataset_id=:datasetId);
DELETE FROM eval_case WHERE dataset_id=:datasetId;
DELETE FROM eval_dataset WHERE id=:datasetId;
```

Case deletion removes expected knowledge first. Both operations verify the affected owning row count and throw `IllegalArgumentException` for unknown IDs.

- [ ] **Step 3: Add JDBC integration coverage**

Test successful deletion and blocked deletion when `eval_run` or `eval_result` exists using the existing H2 schema fixture.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `mvn -s maven-settings.xml -pl trust-rag-storage-jdbc -am -Dtest=EvalDatasetServiceTest,EvalCaseServiceTest,JdbcRepositoriesTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all focused tests pass.

---

### Task 3: Add CSV Import and Admin API Completeness

**Files:**
- Modify: `pom.xml`
- Modify: `trust-rag-evaluation/pom.xml`
- Create: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/EvalCaseCsvImportService.java`
- Create: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/EvalCaseCsvImportResult.java`
- Create: `trust-rag-evaluation/src/test/java/io/github/trustrag/evaluation/EvalCaseCsvImportServiceTest.java`
- Modify: `trust-rag-admin-api/src/main/java/io/github/trustrag/admin/TrustRagEvaluationController.java`
- Modify: `trust-rag-admin-api/src/main/java/io/github/trustrag/admin/TrustRagExceptionHandler.java`
- Create: `trust-rag-admin-api/src/test/java/io/github/trustrag/admin/TrustRagEvaluationControllerTest.java`

- [ ] **Step 1: Write failing CSV tests**

Cover quoted commas/newlines, UTF-8 BOM, `expected_knowledge_ids` separated by `|`, tags separated by `|`, invalid difficulty, invalid knowledge ID, and mixed valid/invalid rows. Assert one bad row does not discard valid rows.

- [ ] **Step 2: Add Apache Commons CSV dependency**

Manage `org.apache.commons:commons-csv` in the root POM and depend on it only from `trust-rag-evaluation`.

- [ ] **Step 3: Implement the importer**

Required headers: `question`. Optional headers: `expected_answer`, `expected_knowledge_ids`, `tags`, `difficulty`, `tenant_id`, `project_id`, `user_id`, `conversation_id`, `enabled`. Return:

```java
public record EvalCaseCsvImportResult(
        int totalRows, int importedRows, int failedRows, List<RowError> errors) {
    public record RowError(long rowNumber, String message) {}
}
```

- [ ] **Step 4: Add controller CRUD/import/diagnostic endpoints**

Implement PUT/DELETE datasets, GET/PUT/DELETE cases, query-param case listing, multipart CSV import, and `GET /results/{resultId}/judge-details`. Existing routes remain unchanged.

- [ ] **Step 5: Map conflicts to HTTP 409**

Return `ProblemDetail` title `TrustRAG evaluation conflict` with the domain error message.

- [ ] **Step 6: Run focused tests**

Run: `mvn -s maven-settings.xml -pl trust-rag-admin-api -am -Dtest=EvalCaseCsvImportServiceTest,TrustRagEvaluationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all focused tests pass.

---

### Task 4: Fix Judge Retry and Runner Terminal State

**Files:**
- Create: `trust-rag-evaluation/src/test/java/io/github/trustrag/evaluation/DefaultGenerationJudgeServiceTest.java`
- Create: `trust-rag-evaluation/src/test/java/io/github/trustrag/evaluation/DefaultEvalRunnerTest.java`
- Modify: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/DefaultGenerationJudgeService.java`
- Modify: `trust-rag-evaluation/src/main/java/io/github/trustrag/evaluation/DefaultEvalRunner.java`

- [ ] **Step 1: Write retry failure tests**

Use a counting `LlmClient`: first response is non-JSON, second is valid. Assert two calls and a non-null score. Add a second test where all attempts are invalid and assert null score, `passed=false`, final raw output and parse reason are retained.

- [ ] **Step 2: Make parse failure retryable**

`parse` throws a private `JudgeResponseException` for empty, malformed or score-less JSON. `score` retains the last raw output and returns a failed detail only after all configured attempts are exhausted.

- [ ] **Step 3: Write runner terminal-state tests**

Cover repository/list failure and report generation failure. Assert `completeRun(...FAILED...)` receives the error message and the run never remains RUNNING.

- [ ] **Step 4: Wrap the complete task lifecycle**

Mark RUNNING once, execute cases/report in one guarded lifecycle, and complete SUCCEEDED/FAILED/CANCELED exactly once. Preserve case-level failure accounting.

- [ ] **Step 5: Run evaluation module tests**

Run: `mvn -s maven-settings.xml -pl trust-rag-evaluation -am test`

Expected: all evaluation and dependency tests pass.

---

### Task 5: Scaffold the Vue 3 Application and Typed API

**Files:**
- Create: `trust-rag-admin-ui/package.json`
- Create: `trust-rag-admin-ui/pnpm-lock.yaml`
- Create: `trust-rag-admin-ui/index.html`
- Create: `trust-rag-admin-ui/tsconfig.json`
- Create: `trust-rag-admin-ui/vite.config.ts`
- Create: `trust-rag-admin-ui/src/main.ts`
- Create: `trust-rag-admin-ui/src/App.vue`
- Create: `trust-rag-admin-ui/src/api/http.ts`
- Create: `trust-rag-admin-ui/src/api/evaluation.ts`
- Create: `trust-rag-admin-ui/src/api/types.ts`
- Create: `trust-rag-admin-ui/src/stores/app.ts`
- Create: `trust-rag-admin-ui/src/router/index.ts`
- Create: `trust-rag-admin-ui/src/styles/index.css`
- Create: `trust-rag-admin-ui/src/tests/setup.ts`
- Create: `trust-rag-admin-ui/src/api/http.spec.ts`

- [ ] **Step 1: Resolve current stable dependency versions**

Run `corepack pnpm view <package> version` for Vue, Vite, Element Plus, ECharts, Pinia, Vue Router, Axios, Vitest, Vue Test Utils and MSW. Pin exact versions in `package.json` and set `packageManager` to pnpm.

- [ ] **Step 2: Write failing API client tests**

Verify base URL, query omission for null values, Bearer Token, RFC 7807 normalization and timeout/network errors.

- [ ] **Step 3: Implement the typed API client**

Expose methods for every controller route. Model Java enum values as string unions and preserve nullable metrics as `number | null`.

- [ ] **Step 4: Add app store and route skeleton**

Persist only `tenantId`, `projectId`, `token` and `apiBaseUrl`. Define lazy-loaded routes for `/eval`, datasets, cases, runs, reports, compare and governance.

- [ ] **Step 5: Install and verify**

Run: `corepack pnpm install --frozen-lockfile=false`, then `corepack pnpm test --run src/api/http.spec.ts`.

Expected: API tests pass and `pnpm-lock.yaml` is generated.

---

### Task 6: Build the Task-Oriented Shell and Overview

**Files:**
- Create: `trust-rag-admin-ui/src/layouts/EvaluationLayout.vue`
- Create: `trust-rag-admin-ui/src/components/MetricCard.vue`
- Create: `trust-rag-admin-ui/src/components/RunStatusTag.vue`
- Create: `trust-rag-admin-ui/src/components/EmptyState.vue`
- Create: `trust-rag-admin-ui/src/views/OverviewView.vue`
- Create: `trust-rag-admin-ui/src/utils/format.ts`
- Create: `trust-rag-admin-ui/src/utils/format.spec.ts`
- Create: `trust-rag-admin-ui/src/views/OverviewView.spec.ts`

- [ ] **Step 1: Write formatter and empty-metric tests**

Assert `null` renders `--`, percentages use one decimal place, durations remain readable, and zero is rendered as zero rather than missing.

- [ ] **Step 2: Implement the B visual direction**

Use a warm neutral canvas, deep green actions, rounded quality cards, restrained shadows and a top navigation. Include responsive breakpoints and keyboard-visible focus styles.

- [ ] **Step 3: Implement overview aggregation**

Load recent runs and the most recent successful report. Show no-data onboarding when no run exists; do not synthesize a score from missing generation metrics.

- [ ] **Step 4: Run component tests**

Run: `corepack pnpm test --run src/utils/format.spec.ts src/views/OverviewView.spec.ts`

Expected: tests pass.

---

### Task 7: Implement Dataset and Case Management

**Files:**
- Create: `trust-rag-admin-ui/src/views/DatasetsView.vue`
- Create: `trust-rag-admin-ui/src/views/CasesView.vue`
- Create: `trust-rag-admin-ui/src/components/DatasetFormDrawer.vue`
- Create: `trust-rag-admin-ui/src/components/CaseFormDrawer.vue`
- Create: `trust-rag-admin-ui/src/components/CsvImportDialog.vue`
- Create: `trust-rag-admin-ui/src/views/DatasetsView.spec.ts`
- Create: `trust-rag-admin-ui/src/views/CasesView.spec.ts`

- [ ] **Step 1: Write interaction tests**

Cover required fields, create/edit payloads, disabled filters, delete conflict display, expected knowledge editing, CSV summary and failed-row rendering.

- [ ] **Step 2: Implement dataset management**

Provide Scope filters, create/edit drawer, navigation to cases, run shortcut and guarded delete confirmation.

- [ ] **Step 3: Implement case management**

Provide dataset selection, tag/difficulty client filtering, compact table, create/edit drawer, expected knowledge rows and CSV import.

- [ ] **Step 4: Run view tests**

Run: `corepack pnpm test --run src/views/DatasetsView.spec.ts src/views/CasesView.spec.ts`

Expected: tests pass.

---

### Task 8: Implement Runs, Reports, Comparison and Governance

**Files:**
- Create: `trust-rag-admin-ui/src/views/RunsView.vue`
- Create: `trust-rag-admin-ui/src/views/ReportsView.vue`
- Create: `trust-rag-admin-ui/src/views/CompareView.vue`
- Create: `trust-rag-admin-ui/src/views/GovernanceView.vue`
- Create: `trust-rag-admin-ui/src/components/MetricChart.vue`
- Create: `trust-rag-admin-ui/src/components/ResultDetailDrawer.vue`
- Create: `trust-rag-admin-ui/src/composables/useRunPolling.ts`
- Create: `trust-rag-admin-ui/src/composables/useRunPolling.spec.ts`
- Create: `trust-rag-admin-ui/src/views/CompareView.spec.ts`

- [ ] **Step 1: Write polling and comparison guard tests**

Use fake timers to prove polling starts only for active runs and is cleaned on unmount. Prove cross-dataset comparison is rejected before an API call.

- [ ] **Step 2: Implement run creation and monitoring**

Display progress as `(success + failed) / total`, elapsed time and cancel actions. Refresh only while active work exists.

- [ ] **Step 3: Implement report diagnostics**

Render retrieval/generation metric groups, latency, result table and Judge detail drawer. Explicitly distinguish failed, skipped and unavailable Judge values.

- [ ] **Step 4: Implement Before/After analysis**

Filter eligible BEFORE/AFTER runs by selected dataset, fetch both reports plus compare result, and display raw values, deltas, grouped bars and translated conclusion.

- [ ] **Step 5: Implement governance dashboard**

Render five metric cards and a multi-series date trend. Support Scope/date filters and snapshot capture.

- [ ] **Step 6: Run focused tests**

Run: `corepack pnpm test --run src/composables/useRunPolling.spec.ts src/views/CompareView.spec.ts`

Expected: tests pass.

---

### Task 9: Add Development Mock Scenarios and Documentation

**Files:**
- Create: `trust-rag-admin-ui/src/mocks/browser.ts`
- Create: `trust-rag-admin-ui/src/mocks/handlers.ts`
- Create: `trust-rag-admin-ui/src/mocks/fixtures.ts`
- Create: `trust-rag-admin-ui/.env.example`
- Create: `trust-rag-admin-ui/README.md`
- Modify: `README.md`
- Modify: `trust-rag-docs/src/main/resources/docs/api.md`

- [ ] **Step 1: Add complete mock scenarios**

Include two datasets, multiple cases, PENDING/RUNNING/SUCCEEDED/FAILED runs, a report with partial Judge metrics, Judge raw output, Before/After data and seven governance dates.

- [ ] **Step 2: Gate mock startup**

```ts
if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCKS === 'true') {
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}
```

- [ ] **Step 3: Document local and production use**

Document Corepack/pnpm commands, backend proxy, optional token, mock mode, static build output and all new API routes.

- [ ] **Step 4: Run complete frontend verification**

Run: `corepack pnpm typecheck`, `corepack pnpm test --run`, `corepack pnpm build`.

Expected: all commands exit 0.

---

### Task 10: Full Verification and Browser Acceptance

**Files:**
- Modify only files required by failures discovered in this task.

- [ ] **Step 1: Run Java verification**

Run: `mvn -s maven-settings.xml verify`

Expected: reactor build success with all tests passing.

- [ ] **Step 2: Start UI with explicit mocks**

Run: `VITE_USE_MOCKS=true corepack pnpm dev --host 127.0.0.1` from `trust-rag-admin-ui`.

Expected: Vite serves the app on localhost and MSW starts only in development.

- [ ] **Step 3: Browser acceptance**

Verify all seven routes, dataset create/edit, case expected knowledge editing, CSV feedback, run creation/progress/cancel, report result/Judge details, same-dataset comparison and governance trend/snapshot. Check one desktop and one narrow viewport.

- [ ] **Step 4: Inspect final diff**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; pre-existing unrelated changes remain untouched.

- [ ] **Step 5: Record implementation audit**

Update the final report with completed document requirements and explicitly list remaining non-scope items: JWT/RBAC, Spring Batch, Judge manual review, structured claim diagnostics, exact governance event semantics and knowledge snapshots.
