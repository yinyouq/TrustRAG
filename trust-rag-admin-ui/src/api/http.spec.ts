import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ApiError, createHttpClient } from './http'
import { server } from '@/tests/testServer'

describe('HTTP client', () => {
  it('adds bearer token and omits null query parameters', async () => {
    let requestUrl = ''
    let authorization = ''
    server.use(
      http.get('*/trust-rag/admin/eval/datasets', ({ request }) => {
        requestUrl = request.url
        authorization = request.headers.get('authorization') ?? ''
        return HttpResponse.json([])
      }),
    )
    const client = createHttpClient({
      baseURL: '/trust-rag/admin/eval',
      getToken: () => 'token-123',
    })

    await client.get('/datasets', {
      params: { tenantId: null, projectId: 'project-a', includeDisabled: false },
    })

    expect(authorization).toBe('Bearer token-123')
    expect(requestUrl).toContain('projectId=project-a')
    expect(requestUrl).toContain('includeDisabled=false')
    expect(requestUrl).not.toContain('tenantId')
  })

  it('normalizes RFC 7807 responses', async () => {
    server.use(
      http.get('*/trust-rag/admin/eval/datasets', () =>
        HttpResponse.json(
          {
            title: 'TrustRAG evaluation conflict',
            detail: 'dataset has historical runs',
            status: 409,
          },
          { status: 409 },
        ),
      ),
    )
    const client = createHttpClient({
      baseURL: '/trust-rag/admin/eval',
      getToken: () => null,
    })

    const error = await client.get('/datasets').catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 409,
      title: 'TrustRAG evaluation conflict',
      detail: 'dataset has historical runs',
    })
  })
})
