import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import { loadSession, session } from './session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    // Même écran : la galerie ciblée est sélectionnée, sa page ouverte, la carte centrée dessus.
    { path: '/galerie/:id(\\d+)', name: 'gallery', component: HomeView },
    { path: '/connexion', name: 'login', component: () => import('./views/LoginView.vue') },
    // Tout ce qui est admin est chargé à la demande : un visiteur ne télécharge jamais ce code.
    {
      path: '/admin',
      name: 'admin',
      component: () => import('./views/admin/AdminHomeView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth) {
    await loadSession()
    if (!session.authenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
})

export default router
