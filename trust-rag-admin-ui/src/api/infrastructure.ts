/**
 * 检索基础设施监控 API。
 *
 * 此处只访问后端聚合后的 Milvus/OpenSearch 容器指标，不会读取宿主 Java 进程指标。
 */
import type { AxiosInstance } from 'axios'
import type { InfrastructureMemorySnapshot } from './types'

export function createInfrastructureApi(client: AxiosInstance) {
  return {
    async getMemorySnapshot(windowMinutes?: number) {
      return (await client.get<InfrastructureMemorySnapshot>(
        '/trust-rag/admin/infrastructure/memory',
        { params: { windowMinutes } },
      )).data
    },
  }
}

export type InfrastructureApi = ReturnType<typeof createInfrastructureApi>
