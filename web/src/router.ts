import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import TreePage from './pages/TreePage.vue'
import CreateFamilyPage from './pages/CreateFamilyPage.vue'
import MembersPage from './pages/MembersPage.vue'
import MediaUploadPage from './pages/MediaUploadPage.vue'
import StoriesPage from './pages/StoriesPage.vue'
import LoginPage from './pages/LoginPage.vue'
import RegisterPage from './pages/RegisterPage.vue'
import { useAuthStore } from './stores/authStore'
import { isUsingGraphApi } from './features/tree/api/graphClient'

const routes = [
  {
    path: '/',
    name: 'tree',
    component: TreePage,
    meta: { requiresAuth: true },
  },
  {
    path: '/create-family',
    name: 'createFamily',
    component: CreateFamilyPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/members',
    name: 'members',
    component: MembersPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/media-upload',
    name: 'mediaUpload',
    component: MediaUploadPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/stories',
    name: 'stories',
    component: StoriesPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterPage,
    meta: { guestOnly: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

function requiresAuthCheck(to: RouteLocationNormalized): boolean {
  if (!to.meta.requiresAuth) return false
  if (!isUsingGraphApi()) return false
  return true
}

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (requiresAuthCheck(to) && !authStore.isAuthenticated) {
    next({
      name: 'login',
      query: { redirect: to.fullPath },
    })
    return
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    next({ name: 'tree' })
    return
  }

  next()
})

export default router
