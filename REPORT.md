# Exercise 06 — Reflection Report
#### Members: Adrienne Grapa, Faiq Baig

## (a) Tradeoffs
CampusCoffee offers two persistence paths: relational adapters (`PosDataServiceImpl`, `ReviewDataServiceImpl`) and event-sourced decorators (`EventSourcedPosDataService`) appending full-state events via `EventStore`, projected by `ReadModelProjector`. Relational is simpler with less storage; event sourcing adds an audit log and rebuild capability (`EventsToDataRunner`). CampusCoffee uses optimistic locking (`@Version`) rather than pessimistic locking, where a transaction acquires an exclusive row lock blocking writers until commit, detecting stale writes via `ConcurrentUpdateException` (409). Event sourcing justifies itself when audit trails or replay matter; CRUD suffices.

## (b) Use Cases
The log reveals *who approved a review*. The event body stores only `approvalCount` and `approved`, but querying `INSERT` events of type `ReviewApproval` exposes each approver's identity and timestamp information the relational aggregate cannot provide.

The log also reconstructs a POS's edit history. Each `UPDATE` stores complete state; filtering by `changeType = UPDATE` traces how a listing evolved the relational table holds only the current snapshot.

## (c) Snapshots
As the `events` table grows, `EventsToDataRunner.rebuildFromLog()` replays every row through `ReadModelProjector`, becoming costly. Periodic snapshots storing projected state at a known `seq` let startup replay only newer events. Since `seq` is monotonic, a snapshot at `N` means replay starts at `N + 1`.
