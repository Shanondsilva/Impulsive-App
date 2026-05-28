# Impulsive Project Structure

Impulsive is organized around four main source packages under:

`src/main/java/com/impulsive/app/`

## Main Packages

### `frontend`

Contains all user interface code, including navigation, screens, reusable Compose components, app theme files, and previews. UI code should render state and dispatch user actions, but it should not own business rules, persistence, monitoring, or security behavior.

### `backend`

Contains app data, domain logic, repositories, session coordination, progress logic, engines, and background services. This package owns the core behavior of the app, including use cases, domain models, trigger logic, timers, and monitoring services.

### `security`

Contains permission handling, privacy safeguards, monitoring security, secure storage boundaries, and anti-bypass behavior. Security-sensitive code should stay here so it can be reviewed and evolved independently from UI and business workflows.

### `core`

Contains shared infrastructure that is safe for multiple packages to depend on, including constants, utilities, dependency injection, result types, and time abstractions. Core should stay small and generic.

## Feature Placement

### Demo Flow

New demo flow UI files should go in:

`src/main/java/com/impulsive/app/frontend/screens/demo/`

Demo session state, orchestration, and non-UI demo behavior should go in:

`src/main/java/com/impulsive/app/backend/session/`

### Future Games

Future game UI screens should live under `frontend/screens/` in a dedicated screen package for the game or game collection.

Game rules, scoring, session logic, timers, and engines should live in `backend/domain/engine/`, `backend/domain/usecase/`, or `backend/session/` depending on responsibility.

### Room Entities and Repositories

Future Room database files should go in:

- Entities: `backend/data/local/entity/`
- DAOs: `backend/data/local/dao/`
- Database setup: `backend/data/local/database/`
- Repositories: `backend/data/repository/`

### Theme Files

All theme, typography, color, spacing, and design-system style files should go in:

`src/main/java/com/impulsive/app/frontend/theme/`

## Strict Separation Rule

Do not mix UI, business logic, storage, and security code.

- UI belongs in `frontend`.
- Business logic belongs in `backend/domain`, `backend/session`, or `backend/progress`.
- Storage and repositories belong in `backend/data`.
- Security and anti-bypass behavior belong in `security`.
- Shared generic helpers belong in `core`.

When adding new code, choose the package by responsibility first. If code appears to belong in multiple areas, split it into smaller pieces with clear ownership.
