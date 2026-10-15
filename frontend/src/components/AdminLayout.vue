<template>
  <div class="admin">
    <!-- Mobile : barre fixe en bas, grosses cibles tactiles. Grand écran : barre en haut. -->
    <nav class="admin-nav" aria-label="Administration">
      <RouterLink to="/admin" class="admin-nav__item" active-class="" exact-active-class="admin-nav__item--active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z" /></svg>
        <span>Menu</span>
      </RouterLink>
      <RouterLink to="/admin/galeries" class="admin-nav__item" active-class="admin-nav__item--active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M9 9.5h.01" /></svg>
        <span>Galeries</span>
      </RouterLink>
      <RouterLink to="/admin/etapes" class="admin-nav__item" active-class="admin-nav__item--active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6-5.2-6-10a6 6 0 1112 0c0 4.8-6 10-6 10zM12 8.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z" /></svg>
        <span>Étapes</span>
      </RouterLink>
      <RouterLink to="/admin/vols" class="admin-nav__item" active-class="admin-nav__item--active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 13l18-8-6 14-3-6-6-2zM12 13l9-8" /></svg>
        <span>Vols</span>
      </RouterLink>
      <RouterLink to="/admin/croisieres" class="admin-nav__item" active-class="admin-nav__item--active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v11M12 4l6 9h-6M4 16h16l-2 4H6z" /></svg>
        <span>Croisières</span>
      </RouterLink>
      <RouterLink to="/" class="admin-nav__item">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3a9 9 0 100 18 9 9 0 000-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" /></svg>
        <span>Site</span>
      </RouterLink>
    </nav>

    <main class="page admin__content">
      <slot />
    </main>
  </div>
</template>
