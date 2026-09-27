# feature/savedfilters

Regular Android feature adapter for locally persisted saved-filter presets. The app-owned Navigation 3
host supplies this module with the repository-backed preset list and routes applied presets to a
query-driven results screen. It is intentionally not a dynamic feature: saved filters are part of
the base app's root navigation and do not require on-demand delivery.

The screen supports applying, renaming, and deleting presets. Catalog, search, and browse surfaces
save their exact `BeersQuery` through callbacks owned by the app navigation ViewModel. The results
screen reuses the shared browse paging content and ViewModel while retaining Android resources and
back-stack ownership in the Android adapter.
