import { createRouter, createWebHistory } from 'vue-router'
import EvaluationLayout from '@/layouts/EvaluationLayout.vue'

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
      ],
    },
    { path: '/', redirect: '/eval' },
    { path: '/:pathMatch(.*)*', redirect: '/eval' },
  ],
})

export default router
