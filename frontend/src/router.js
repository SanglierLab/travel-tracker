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
    {
      path: '/admin/galeries',
      name: 'admin-galleries',
      component: () => import('./views/admin/AdminGalleriesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/galeries/nouvelle',
      name: 'admin-gallery-new',
      component: () => import('./views/admin/AdminGalleryFormView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/galeries/:id(\\d+)',
      name: 'admin-gallery',
      component: () => import('./views/admin/AdminGalleryFormView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/vols',
      name: 'admin-flights',
      component: () => import('./views/admin/AdminFlightsView.vue'),
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
