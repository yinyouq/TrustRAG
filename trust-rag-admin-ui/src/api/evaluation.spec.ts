import axios from 'axios'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { createEvaluationApi } from './evaluation'
import { createHttpClient } from './http'
import { server } from '@/tests/testServer'

describe('evaluation API', () => {
  it('serializes dataset filters and comparison payloads', async () => {
    let compareBody: unknown
    server.use(
      http.get('*/trust-rag/admin/eval/datasets', ({ request }) => {
        expect(new URL(request.url).searchParams.get('includeDisabled')).toBe('true')
        return HttpResponse.json([])
      }),
      http.post('*/trust-rag/admin/eval/compare', async ({ request }) => {
        compareBody = await request.json()
        return HttpResponse.json({ beforeRunId: 1, afterRunId: 2 })
      }),
    )
    const api = createEvaluationApi(createHttpClient({
      baseURL: '/trust-rag/admin/eval',
      getToken: () => null,
    }))

    await api.listDatasets({ includeDisabled: true })
    await api.compareRuns(1, 2)

    expect(compareBody).toEqual({ beforeRunId: 1, afterRunId: 2 })
  })

  it('uploads CSV as multipart data', async () => {
    let contentType = ''
    server.use(
      http.post('*/trust-rag/admin/eval/cases/import-csv', ({ request }) => {
        contentType = request.headers.get('content-type') ?? ''
        return HttpResponse.json({ totalRows: 1, importedRows: 1, failedRows: 0, errors: [] })
      }),
    )
    const client = axios.create({ baseURL: '/trust-rag/admin/eval' })
    const api = createEvaluationApi(client)

    await api.importCases(4, new File(['question\nhello'], 'cases.csv', { type: 'text/csv' }))

    expect(contentType).toContain('multipart/form-data')
  })
})
