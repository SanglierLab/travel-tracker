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
