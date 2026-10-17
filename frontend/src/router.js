import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import { loadSession, session } from './session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    // Même écran : la galerie ciblée est sélectionnée, sa page ouverte, la carte centrée dessus.
    { path: '/galerie/:id(\\d+)', name: 'gallery', component: HomeView },
    // Même écran : l'étape ciblée est sélectionnée et mise en valeur sur la carte.
    { path: '/etape/:id(\\d+)', name: 'stage', component: HomeView },
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
    {
      path: '/admin/croisieres',
      name: 'admin-cruises',
      component: () => import('./views/admin/AdminCruisesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/livetrack',
      name: 'admin-livetrack',
      component: () => import('./views/admin/AdminLiveTrackView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/telephone',
      name: 'admin-devices',
      component: () => import('./views/admin/AdminDevicesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/config',
      name: 'admin-config',
      component: () => import('./views/admin/AdminConfigView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/etapes',
      name: 'admin-stages',
      component: () => import('./views/admin/AdminStagesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/etapes/nouvelle',
      name: 'admin-stage-new',
      component: () => import('./views/admin/AdminStageFormView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/etapes/:id(\\d+)',
      name: 'admin-stage',
      component: () => import('./views/admin/AdminStageFormView.vue'),
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
