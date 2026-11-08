# Test report

Run: `gradlew.bat test :desktop-app:compileJava :android-app:assembleDebug`

Result: `BUILD SUCCESSFUL`. 24 unit tests, 0 failures. Details: `TEST_RESULTS.md`.

JDK used to compile: Temurin 21.0.12. Bytecode target: 17. Android debug APK assembled with compileSdk 35.

| Module | Tests | Result |
| --- | --- | --- |
| `shared/common` | Catalog key parity and Azerbaijani text | Passed |
| `shared/domain` | Email, password, and pet polymorphism | Passed |
| `shared/application` | Adoption rules, suspended login, invalid email, session refresh, admin gate, location rounding and city snap | Passed |
| `shared/security` | Admin role gate and encrypted session round-trip | Passed |
| `shared/data` | Secret-key rejection, Google URL, password session parsing, refresh, listing write, search sanitizing, adoption insert/RPC, preferred language | Passed |
| `desktop-app` | Compiles. No desktop unit tests yet | Compile passed |
| `android-app` | No unit tests yet | `assembleDebug` passed |

These are JVM unit tests with Mockito and an in-memory adoption repository. They do not hit the hosted Supabase project, and they do not prove RLS. Milestone 4 applied and reviewed the grants on the hosted project. A Postgres policy test suite remains milestone 12.

The first data-module run failed because the search sanitizer test expected the semicolon in `Boncuk;drop` to delete the rest of the word. The sanitizer removes the semicolon and keeps the letters, which is the intended filter defense. The assertion now expects `Boncukdrop`.
