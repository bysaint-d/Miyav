# Architecture review

Review date: 8 November 2026.

This is a read-only review of the Heyvan Java monorepo after Graphify extraction. Source code was not modified. The Expo application at `<expo-app-path>` is outside this graph.

## 1. Method and evidence grades

| Grade | Meaning |
| --- | --- |
| Verified (graph) | Counted or traversed in `graphify-out/graph.json` or returned by Graphify CLI |
| Verified (source) | Read from Java or Gradle files on disk |
| Graph-limited | The graph does not contain the relationship; the claim is about that absence, not about runtime |
| Assumption | Inferred from design docs or incomplete extraction; not proven by the graph |

Commands used: `graphify god-nodes`, `graphify query`, `graphify path`, `graphify affected`, `graphify explain`, `graphify diagnose multigraph`. Also read `graphify-out/graph.json`, `graphify-out/.graphify_analysis.json`, `graphify-out/.graphify_build.json`, and `graphify-out/manifest.json`.

There is no `graphify-out/GRAPH_REPORT.md` and no `graphify-out/wiki/`. Community names in the analysis file are numeric only.

## 2. Graph inventory

Verified (graph):

| Metric | Value |
| --- | --- |
| Nodes | 745 |
| Links | 2019 |
| Hyperedges | 0 |
| Communities | 41 (ids 0–40) |
| Unique `source_file` values | 105 |
| Callable classes | 106 |
| Callable symbols | 464 |
| Graphify version | 0.9.103 |
| `directed` | `false` |
| `multigraph` | `false` |
| Extraction mode | `code_only: true` (`.graphify_build.json`) |
| Unverified nodes | 0 |
| Exact duplicate edges | 0 |
| Same-endpoint collapsed edges | 0 |
| Self-loops | 2 |

The extraction message said 107 code files. The graph contains 105 unique `source_file` values. The two-file gap is not explained by the JSON; it is not treated as extra architecture.

Node `file_type`: 687 `code`, 58 `concept` (JDK/Android/JavaFX/library symbols with no `source_file`).

Link relations:

| Relation | Count | Confidence |
| --- | --- | --- |
| `imports` | 575 | all EXTRACTED |
| `references` | 455 | all EXTRACTED |
| `calls` | 446 | 198 EXTRACTED, 248 INFERRED |
| `method` | 358 | all EXTRACTED |
| `contains` | 121 | all EXTRACTED |
| `case_of` | 31 | all EXTRACTED |
| `inherits` | 17 | all EXTRACTED |
| `implements` | 14 | all EXTRACTED |
| `defines` | 2 | all EXTRACTED |

Treat `INFERRED` `calls` as weaker than AST `imports` / `implements` / `inherits`.

Indexed file extensions (node-weighted): `.java` 589, `.yml` 15, `.gradle` 9, plus 4 `gradlew` script nodes. SQL, XML, FXML, properties, TOML, CSS, and Markdown are not in the graph.

## 3. Module graph

### 3.1 Gradle project DAG (verified source)

`settings.gradle` includes `:shared:common`, `:shared:domain`, `:shared:security`, `:shared:application`, `:shared:data`, `:desktop-app`, `:android-app`.

Project dependencies from each `build.gradle`:

```text
shared:common          (no project deps)
shared:domain          api -> common
shared:security        implementation -> domain, common
shared:application     api -> domain; implementation -> security, common
shared:data            implementation -> domain, common
desktop-app            implementation -> application, data, security, domain, common
android-app            implementation -> application, data, security, domain, common
```

There is no Gradle edge from `application` to `data`, from `domain` to `application`/`data`/`security`, or from `common` to any other project. The intended layering in `docs/MIYAV_ARCHITECTURE.md` matches these files.

Apps depend on both `application` and `data` because they are composition roots: they construct `MiyavBackend` and pass repository ports into services.

### 3.2 Graphify Java import edges (verified graph)

Cross-module `imports` among first-party modules:

| From | To | Import edges |
| --- | --- | --- |
| android-app | application, common, data, domain, security | 3 / 4 / 2 / 13 / 3 |
| desktop-app | application, common, data, domain, security | 2 / 13 / 4 / 13 / 3 |
| application | common, domain, security | 30 / 57 / 5 |
| data | common, domain | 27 / 48 |
| domain | common | 17 |
| security | common, domain | 4 / 5 |

Verified (graph): no `shared/application` → `shared/data` import. No `shared/domain` → `shared/application` or `shared/data` import. No `shared/common` → other `shared/*` import.

Tarjan SCC on that import graph: no cycles. The same modules with mixed `imports`/`calls`/`references`/`implements`/`inherits`/`contains` also produced no SCC larger than one node.

Gradle `implementation` edges themselves are **not** in the Java import graph. All nine `*.gradle` nodes have degree 0 (communities 32–40). Graphify therefore cannot confirm the Gradle DAG; the DAG above is from reading the Gradle files.

### 3.3 Nodes by module (verified graph)

| Module | Nodes | Indexed files |
| --- | --- | --- |
| shared:domain | 232 | 36 |
| shared:data | 127 | 19 |
| external/unknown (JDK, Android, JavaFX, Gson, OkHttp, JUnit, Mockito) | 128 | 0 |
| shared:application | 73 | 12 |
| android-app | 62 | 12 |
| shared:common | 46 | 12 |
| desktop-app | 38 | 5 |
| shared:security | 18 | 5 |
| ci (`.github/workflows/foundation.yml`) | 15 | 1 |
| gradle-wrapper / root-build | 6 | 3 |

Domain is the largest first-party cluster. That matches a model-heavy, UI-thin foundation.

## 4. Architectural hubs

`graphify god-nodes --top 25` and `.graphify_analysis.json` `gods` agree on the top ten.

| Rank | Symbol | Degree | Module | Why it is central |
| --- | --- | --- | --- | --- |
| 1 | `AuthSession` | 105 | domain | Session token object passed into almost every repository method |
| 2 | `Pet` | 48 | domain | Abstract listing type used by factory, services, UI lists |
| 3 | `User` | 41 | domain | Profile entity after login |
| 4 | `AdoptionApplication` | 37 | domain | Application workflow type |
| 5 | `ValidationException` | 33 | common | Shared error type |
| 6 | `PersistenceException` | 30 | common | Shared error type |
| 7 | `LocaleCode` | 30 | common | AZ/EN/RU tag |
| 8 | `AuthService` | 28 | application | Login/register/logout use case |
| 9 | `UserRole` | 25 | domain | owner/sitter/admin |
| 10 | `HttpExchange` | 23 | data | HTTP response record used by every Supabase adapter |

Next hubs: `PetStatus` 22, `SupabaseClient` 20, `PetQuery` 20, `AccessDeniedException` 19, `RoleGuard` 18, `MiyavBackend` 17, `AuthRepository` 17, `UserRepository` 17, `DesktopGraph` 16, `SupabaseConfig` 16.

`AuthSession` is a data-carrier hub, not a god *class* with mixed responsibilities. High degree follows from `AuthSession` appearing on repository ports. That is a verified structural fact, not by itself a SOLID violation.

`graphify affected "AuthSession" --depth 2` reaches Android `HomeFragment`, desktop `HomeController`, every application service that takes a session, every Supabase repository, and `MemorySessionStore`. Changing `AuthSession` is the widest blast radius in this graph.

## 5. Communities

`.graphify_analysis.json` stores membership and cohesion. Cohesion is low on the large communities (0.05–0.12) and 1.0 on the nine isolated Gradle file communities. Low cohesion here is clustering of AST symbols (methods, tests, JDK types), not proof that Java packages are tangled.

Readable grouping of the 41 communities from member lists (labels are this review’s, not Graphify’s):

| Id | Size | Cohesion | Contents (from member ids) |
| --- | --- | --- | --- |
| 0 | 107 | 0.052 | Data adapters, `SupabaseClient`, `HttpExchange`, `AuthSession` accessors, `PersistenceException`, `TelegramVerification` |
| 1 | 87 | 0.054 | Tests, JUnit/Mockito, many domain types, `RoleGuard.requireAdmin` |
| 2 | 71 | 0.081 | Composition: `MiyavApplication`, `DesktopGraph.load`, `AuthService`, `PetService`, `MemorySessionStore` |
| 3 | 50 | 0.090 | Desktop JavaFX controllers and `MiyavDesktopApp` |
| 4 | 43 | 0.078 | `SupabaseConfig`, Google URI, contract tests, `UnavailableTelegramVerification` |
| 5 | 43 | 0.121 | `AdoptionService` + `AdoptionApplication` + tests |
| 6 | 37 | 0.098 | `UserService`, `LocaleCode`, `ResourceMessageCatalog` |
| 7 | 37 | 0.069 | `NotificationService`, `Notification`, `Report` |
| 8 | 23 | 0.158 | Android fragments, navigation, data binding |
| 9 | 19 | 0.123 | `Conversation`, `Message`, `MessageRepository` |
| 10 | 16 | 0.192 | Room outbox (`MiyavDatabase`, DAO, entity) |
| 11 | 16 | 0.167 | `EnvironmentConfig`, catalog file I/O |
| 12 | 15 | 0.133 | GitHub Actions workflow YAML |
| 13–19 | 11–14 | 0.15–0.27 | Listing status mapper, login states, `PetQuery`, pet mapping, species, `LoginViewModel`, gender |
| 20 | 10 | 0.289 | `OutboxSyncWorker` |
| 21–29 | 5–10 | 0.27–0.40 | Home locale, `Pet` fields, care profiles, login factory, OkHttp, `MainActivity`, `Bird`/`Cat`/`OtherPet` |
| 30 | 4 | 0.833 | `gradlew` shell functions |
| 31 | 4 | 0.500 | `Timestamps` |
| 32–40 | 1 | 1.0 | Isolated `build.gradle` / `settings.gradle` nodes |

Community 0 mixing HTTP adapters with `AuthSession` is a clustering artifact: `AuthSession` is used as a parameter everywhere, so Louvain pulls it toward data. That is not a Java package cycle.

`.graphify_analysis.json` `surprises` lists `LoginFragment.Factory` → `AuthService`, `LoginViewModel` → `AuthService`, and `DesktopGraph` → `AuthService` / `PetService` / `RoleGuard`. Those are expected composition-root edges, not unexpected coupling. Graphify flags them because they cross directories.

## 6. Module reviews

### 6.1 shared/common

Verified (graph + source): error types (`AppException` and four subclasses), `LocaleCode`, `MessageCatalog` / `ResourceMessageCatalog`, `TimeSource` / `SystemTimeSource`. Nothing in this module imports other first-party modules.

Message catalogs on disk (`messages_az.properties`, `messages_en.properties`, `messages_ru.properties`) are **not** graph nodes. `ResourceMessageCatalog` loads them at runtime; Graphify cannot show key parity.

### 6.2 shared/domain

Largest module. Entities (`User`, `Pet` hierarchy, `AdoptionApplication`, `Conversation`, `Message`, `Notification`, `Report`, `AuthSession`, `GeoPoint`), validators, and six repository ports.

`implements` edges from data (verified graph):

| Adapter | Port |
| --- | --- |
| `SupabaseAuthRepository` | `AuthRepository` |
| `SupabaseUserRepository` | `UserRepository` |
| `SupabasePetRepository` | `PetRepository` |
| `SupabaseAdoptionRepository` | `AdoptionRepository` |
| `SupabaseMessageRepository` | `MessageRepository` |
| `SupabaseNotificationRepository` | `NotificationRepository` |

Also: `MemoryAdoptions` in tests → `AdoptionRepository`; `MemorySessionStore` → `SessionStore`; `OkHttpTransport` / `RecordingTransport` → `HttpTransport`; `UnavailableTelegramVerification` → `TelegramVerification`; `ResourceMessageCatalog` → `MessageCatalog`; `SystemTimeSource` → `TimeSource`.

`Pet` inheritance (verified graph): `Dog`, `Cat`, `Bird`, `OtherPet` inherit `Pet`.

There is no `ReportRepository` in the indexed files. `Report` is a domain type only. Assumption: reporting is not wired yet. That matches the roadmap, but the graph cannot prove a future table.

### 6.3 shared/application

Services present: `AuthService`, `PetService`, `UserService`, `AdoptionService`, `NotificationService`, `LocationService`, `TelegramVerification`, `UnavailableTelegramVerification`.

Inbound from UI composition roots (verified graph):

| Service | UI / composition inbound |
| --- | --- |
| `AuthService` | `MiyavApplication`, `LoginViewModel`, `LoginFragment`, `DesktopGraph` |
| `PetService` | `MiyavApplication`, `DesktopGraph` |
| `RoleGuard` | `MiyavApplication`, `DesktopGraph`, also `AdoptionService` |
| `UserService` | **none** (only `contains` on its own file) |
| `AdoptionService` | tests only |
| `NotificationService` | none |
| `LocationService` | tests only |
| `UnavailableTelegramVerification` | none |

Verified (source, stronger than the graph for this point): `MiyavApplication` and `DesktopGraph` **do** construct `UserService`. Graphify missed those import/reference edges. Do not treat the missing graph inbound as missing code.

Verified (source and graph): neither composition root constructs `AdoptionService`, `NotificationService`, `LocationService`, or `UnavailableTelegramVerification`. `MiyavBackend` still builds the adoption, message, and notification repositories. Those ports exist and are unused by the shells.

`AuthService` depends on `SessionStore` (security) and repository ports (domain). `AdoptionService` depends on `RoleGuard` (security). That is the only application → security coupling. It is allowed by Gradle.

### 6.4 shared/data

`MiyavBackend.connect` (verified source) builds one `SupabaseClient` plus OkHttp and six repositories. `SupabaseConfig.parse` rejects secrets; that policy is in source and in contract tests, not in SQL.

`graphify path` (undirected):

```text
AdoptionService --references--> AdoptionRepository <--implements-- SupabaseAdoptionRepository
LoginViewModel --references--> AuthService --references--> AuthRepository <--implements-- SupabaseAuthRepository
SupabaseClient <--references-- SupabaseAuthRepository --implements--> AuthRepository <--references-- AuthService
```

Directed `--directed` searches returned no path. That is a Graphify limitation (`graph.directed == false`), not proof that Java has no call chain.

`NotReady` has no inbound from other classes in the graph. Source still uses it for message send (`schema.not_ready`). Assumption: Graphify did not attach those throw sites to the `NotReady` class node.

`HttpExchange` degree 23 is expected: every adapter goes through `SupabaseClient.send`.

### 6.5 shared/security

Three types: `SessionStore`, `MemorySessionStore`, `RoleGuard`.

`graphify query "RoleGuard session admin"` connects `RoleGuard.requireAdmin` to desktop `HomeController.onAdmin`, `AuthSession.role`, and `UserRole.ADMIN`. Android `MiyavApplication` holds a `RoleGuard` instance. Whether Android UI actually calls `requireAdmin` is not shown as a `HomeFragment` call in the truncated query; source of `HomeFragment` uses it for the admin button (verified source).

Sessions are memory-only. Nothing in the graph writes tokens to disk or Android EncryptedSharedPreferences. That matches `docs/SECURITY.md`. It is a product risk, not a graph contradiction.

### 6.6 android-app

Indexed Java: `MiyavApplication`, `MainActivity`, splash/login/home fragments, `LoginViewModel`, `Codes`, Room outbox, `OutboxSyncWorker`.

Composition (verified source): `onCreate` builds `MiyavBackend` from `BuildConfig` URL/key, `MemorySessionStore`, `RoleGuard`, Room `miyav.db`, `AuthService`, `PetService`, `UserService`. It does not construct `AdoptionService` or schedule WorkManager.

`graphify affected "OutboxSyncWorker" --depth 2` returned **no affected nodes**. The worker’s only graph inbound is its own file `contains`. `doWork()` returns `Result.failure()` (verified source). Room is constructed in `MiyavApplication` but the database field is unused by fragments. The outbox is an isolated shell.

Android navigation, manifests, and layouts are XML and are absent from the graph (section 8).

### 6.7 desktop-app

Indexed Java: `MiyavDesktopApp`, `DesktopGraph`, `LoginController`, `HomeController`. FXML and CSS are not indexed.

`DesktopGraph` is a hub (degree 16). It is the desktop composition root, analogous to `MiyavApplication`.

Undirected `graphify path HomeController → SupabasePetRepository` returned a 4-hop route through `java.util.List`. That is not a design path. The verified source path is `HomeController` → `PetService.discover` → `PetRepository` → `SupabasePetRepository`.

## 7. Authentication flow

Verified (source + graph neighbors of `AuthService`):

1. Android: `LoginFragment` → `LoginViewModel.login` → `AuthService.login`.
2. Desktop: `LoginController.onLogin` → `AuthService.login`.
3. `AuthService` validates email/password, then `AuthRepository.signIn`.
4. `SupabaseAuthRepository` posts to `/auth/v1/token` (path string is in source; Graphify has no HTTP-route nodes).
5. `AuthService.establish` loads `UserRepository.findById`, rejects `suspended`, copies `User.role` onto `AuthSession`, saves `SessionStore`.
6. Logout signs out then `sessions.clear()` in `finally`.

`graphify affected "MiyavBackend"` reaches `MiyavApplication.onCreate` and `DesktopGraph.load` only (plus login/home imports). The backend factory is not referenced from tests in this graph.

Google: `AuthService.googleAuthorizeUri` exists. Java clients do not complete PKCE. That is documented in `docs/API_CONTRACTS.md`; the graph shows the URI builder, not a missing PKCE class, because PKCE was never implemented.

Telegram: `UnavailableTelegramVerification` implements `TelegramVerification` and has no UI inbound. Fail-closed is source-verified.

## 8. Database and application flows

### 8.1 Hosted Postgres

Graph-limited: the only SQL file on disk in this repo, `supabase/migrations/20261107212305_adoption_applications_and_language.sql`, is **not** a graph node. RLS, grants, RPCs, and partial unique indexes cannot be reviewed from Graphify.

What Java source does show:

- Profile read/write: `SupabaseUserRepository` selects `preferred_language` and PATCHes it; it does not send `role` or `suspended`.
- Listings: `SupabasePetRepository` reads `adoption_listings` with `status=eq.active`.
- Applications: `SupabaseAdoptionRepository.save` inserts `listing_id`+`message` for `PENDING`, otherwise POSTs `/rest/v1/rpc/{accept|reject|cancel}_adoption_application`.
- Messages: list conversations; send/listMessages throw `schema.not_ready` (source).
- Notifications: list and mark read.

Those HTTP paths are string literals. Graphify stores them as code, not as schema objects. There is no edge from `SupabaseAdoptionRepository` to a table named `adoption_applications`.

### 8.2 Adoption use case

Verified (graph): `AdoptionService` → `AdoptionRepository` ← `SupabaseAdoptionRepository`.

Verified (source): client-side rules (not owner, listing published, actor checks) live in `AdoptionService`. Concurrency lock and unique indexes live in SQL, which this graph does not contain.

Verified (graph + source): Android and desktop shells do not call `AdoptionService`. Milestone 4 persisted the contract; it did not add application screens.

### 8.3 Discovery

Verified (source): `HomeFragment` / `HomeController` call `PetService.discover(PetQuery.firstPage(), session)`.

### 8.4 Locale

Verified (source): after login, home language buttons call `UserService.updateLocale` then switch UI. Login-screen buttons stay local. Graphify inbound to `UserService` does not yet show those UI edges (extraction gap).

## 9. Missing relationships (XML, Gradle, SQL, and other non-AST files)

`.graphify_build.json` is `code_only: true`. Combined with Graphify’s AST extractor, that explains most gaps.

### 9.1 Gradle (indexed as islands)

These files are nodes with **degree 0**: `settings.gradle`, root `build.gradle`, and every module `build.gradle`. Missing relationships:

- `include` of the seven projects
- `implementation project(...)` / `api project(...)`
- Android `applicationId`, `minSdk`, Room / Navigation / WorkManager libraries
- JavaFX plugin and main class
- Version catalog `gradle/libs.versions.toml` (not indexed at all)

Without those edges, Graphify cannot answer “what happens if I change `shared:domain`?” at the **build** layer. `graphify affected` on a Java symbol can; `graphify affected` on `build.gradle` cannot.

### 9.2 Android XML (not indexed)

| File | Relationship Graphify cannot see |
| --- | --- |
| `AndroidManifest.xml` | Application class, activities, backup/cleartext flags |
| `navigation/nav_graph.xml` | Splash → login → home |
| `layout/*.xml` | Binding between FXML-like layouts and fragments |
| `values/strings.xml` and `values-en`, `values-ru` | `Codes` resource ids |
| `values/themes.xml`, `colors.xml` | Theme |
| `drawable/ic_mark.xml` | Launcher/mark |

`az.miyav.android.databinding.FragmentHomeBinding` appears as an external concept. The generator is implied, not linked to XML.

### 9.3 Desktop FXML/CSS (not indexed)

`login.fxml`, `home.fxml`, `miyav.css`. `@FXML` fields on controllers have no graph edge to the FXML `fx:id`s. `MiyavDesktopApp.show` loading FXML is Java-only.

### 9.4 SQL (not indexed)

`supabase/migrations/20261107212305_adoption_applications_and_language.sql`. Also, the five older migrations live in the Expo repo, not this graph.

Missing: tables, RLS policies, grants, triggers, `private.accept_adoption_application`, public wrappers, partial unique indexes.

### 9.5 Properties / catalogs (not indexed)

`messages_*.properties`, `gradle.properties`, `gradle-wrapper.properties`, gitignored `local.properties`. Locale key parity is tested in Java (`ResourceMessageCatalogTest`) but the properties files themselves have no nodes.

### 9.6 Docs and CI

Markdown under `docs/` is not indexed. CI YAML **is** indexed (community 12) but has no edges to Gradle tasks or test classes.

### 9.7 Expo app

Not in this repository graph. Shared backend is an operational fact from earlier inspection, not a Graphify edge.

## 10. Cycles and layering risks

### 10.1 Circular dependencies

Verified (graph): no module-level import cycle among first-party modules.

Verified (source): Gradle DAG is acyclic.

The two self-loops from `diagnose multigraph` are:

1. `SupabaseClient.send` → `send` (overload / recursive-looking AST, not a module cycle).
2. `LoginFragment.Factory` `implements` `Factory` (inner class vs AndroidX `ViewModelProvider.Factory` collapsed to the same label).

Neither is an architectural cycle.

### 10.2 Risks that are verified

1. **`AuthSession` blast radius.** Degree 105; `affected` depth 2 already spans UI, every service, and every adapter. Token shape changes are expensive.
2. **Composition roots skip several services.** `AdoptionService`, `NotificationService`, `LocationService`, and Telegram verification are compiled but not constructed by Android or desktop shells. `MiyavBackend` still allocates unused repositories.
3. **Outbox is dead.** Room database is built; worker always fails; `affected OutboxSyncWorker` is empty. Offline sync is a stub, not an architecture path.
4. **Message send is fail-closed.** Domain + adapter exist; send throws `schema.not_ready`. UI has no messaging screen in the indexed Java.
5. **`RoleGuard` is UX, not RLS.** Graph shows the desktop admin button calling `requireAdmin`. Postgres policies are not in the graph. Treating `RoleGuard` as authorization would be a design error already documented in `docs/SECURITY.md`.
6. **Memory sessions.** No encrypted store class exists in the graph. Process death drops the Android session.
7. **Apps depend on `data` directly.** Allowed for composition, but it means Android/desktop can skip `application` and call repositories. Current shells mostly go through services; `MiyavBackend.connect` is the remaining data leak surface.
8. **`application` depends on `security`.** `AuthService` needs `SessionStore`; `AdoptionService` needs `RoleGuard`. That is a real extra edge. Alternative would be ports in domain; that is a design choice, not a cycle.
9. **Clustering noise.** Community 0 mixes data HTTP with `AuthSession`. Do not refactor packages based on community id 0.
10. **Graph is undirected.** `path --directed` is not usable for proving “UI does not depend on data.” Use Gradle and import tables instead.

### 10.3 Risks that are assumptions

- Hosted RLS still matches migration `20261107212305` (not in this graph; previously verified out of band).
- Expo `select('*')` still requires `authenticated` SELECT on `profiles.role` (Expo not indexed).
- Unused-index advisor lints remain INFO because of empty tables (database, not graph).
- Future screens will keep using services rather than calling `Supabase*Repository` from fragments (convention, not enforced).

## 11. `implements` map (verified graph)

First-party implementations:

- Data: six `Supabase*Repository` classes implement the six domain ports.
- Security: `MemorySessionStore` implements `SessionStore`.
- Application: `UnavailableTelegramVerification` implements `TelegramVerification`.
- Common: `ResourceMessageCatalog` implements `MessageCatalog`; `SystemTimeSource` implements `TimeSource`.
- Data HTTP: `OkHttpTransport` implements `HttpTransport`.
- Tests: `MemoryAdoptions`, `RecordingTransport`.

No second production implementation of any repository port exists in this graph. Swapping Supabase would mean new classes plus a new `MiyavBackend` factory, not a rewire of services.

## 12. What this review does not claim

- It does not claim production readiness.
- It does not re-verify hosted grants or RLS.
- It does not measure runtime performance.
- It does not treat Graphify community labels as package names.
- It does not treat undirected shortest paths through JDK types (`List`) as design dependencies.

## 13. Summary

The Java tree is a layered monorepo: `common` ← `domain` ← (`application`, `data`, `security`) ← (`android-app`, `desktop-app`). Graphify’s Java import graph and the Gradle files agree that this DAG has no cycle. Data implements domain ports; UI constructs services at two composition roots.

The graph’s most connected symbol is `AuthSession`, because session passing is ubiquitous. The largest product gap visible in both graph and source is that adoption, notifications, location, Telegram, messaging send, and the Android outbox are not on a UI path.

The graph is incomplete for XML navigation, FXML, Gradle project edges, SQL/RLS, and locale property files, because extraction was `code_only` AST. Those missing relationships are extraction limits, not missing features in Gradle or Postgres.

Next architecture work that would show up in a later graph: wire `AdoptionService` / `NotificationService` into the composition roots when screens exist; schedule or remove the outbox worker; add a non-memory `SessionStore`; and, if Graphify is used again for layering proofs, extract with Gradle/SQL/XML enabled or keep using Gradle files as the module authority.
