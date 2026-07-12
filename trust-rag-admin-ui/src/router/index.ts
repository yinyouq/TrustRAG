/**
 * 前端路由表，定义评估工作区的页面入口。
 */
import { createRouter, createWebHistory } from 'vue-router'
import EvaluationLayout from '@/layouts/EvaluationLayout.vue'

// 当前管理台聚焦评估工作区，所有未知入口统一回到 /eval。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/eval',
      component: EvaluationLayout,
      children: [
        { path: '', name: 'overview', component: () => import('@/views/OverviewView.vue') },
        { path: 'datasets', name: 'datasets', component: () => import('@/views/DatasetsView.vue') },
        { path: 'cases', name: 'cases', component: () => import('@/views/CasesView.vue') },
        { path: 'runs', name: 'runs', component: () => import('@/views/RunsView.vue') },
        { path: 'reports', name: 'reports', component: () => import('@/views/ReportsView.vue') },
        { path: 'compare', name: 'compare', component: () => import('@/views/CompareView.vue') },
        { path: 'governance', name: 'governance', component: () => import('@/views/GovernanceView.vue') },
        { path: 'knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeView.vue') },
        { path: 'knowledge-review', name: 'knowledge-review', component: () => import('@/views/KnowledgeReviewView.vue') },
      ],
    },
    { path: '/', redirect: '/eval' },
    { path: '/:pathMatch(.*)*', redirect: '/eval' },
  ],
})

export default router
