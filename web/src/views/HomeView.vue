<script setup lang="ts">
// Family tree visualization will be added in follow-up slices.
// The tree component will use GET /api/v1/families/{familyId}/graph
// to fetch the family graph data for rendering.
</script>

<template>
  <div class="home">
    <h2>Welcome to Genealogy</h2>
    <p>Family tree management application.</p>
    <section class="placeholder">
      <p>
        <strong>Coming soon:</strong> Interactive family tree visualization.
      </p>
      <p class="note">
        The tree view will use <code>GET /api/v1/families/{familyId}/graph</code> 
        to render the family graph.
      </p>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding: 1rem 0;
}

.placeholder {
  margin-top: 2rem;
  padding: 1.5rem;
  background: #f9f9f9;
  border-radius: 8px;
  border: 1px dashed #ccc;
}

.note {
  color: #666;
  font-size: 0.9rem;
}

code {
  background: #e8e8e8;
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  font-size: 0.85rem;
}
</style>
