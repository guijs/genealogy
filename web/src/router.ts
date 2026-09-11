import { createRouter, createWebHistory } from 'vue-router'
import TreePage from './pages/TreePage.vue'
import CreateFamilyPage from './pages/CreateFamilyPage.vue'

const routes = [
  {
    path: '/',
    name: 'tree',
    component: TreePage,
  },
  {
    path: '/create-family',
    name: 'createFamily',
    component: CreateFamilyPage,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
