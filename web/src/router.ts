import { createRouter, createWebHistory } from 'vue-router'
import TreePage from './pages/TreePage.vue'
import CreateFamilyPage from './pages/CreateFamilyPage.vue'
import MembersPage from './pages/MembersPage.vue'

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
  {
    path: '/members',
    name: 'members',
    component: MembersPage,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
