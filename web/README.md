# Genealogy Web Frontend

Vue 3 + TypeScript + Vite frontend for the Genealogy application.

## Development

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Notes

- The tree view will use `GET /api/v1/families/{familyId}/graph` to render the family graph.
- Family-scoped routes are not yet implemented in the backend (coming in follow-up slices).
