# Native Section Mapping Plan (hidden WebView -> Compose)

## Goal
The user must never see a WebView. Sections fetched from VTOP are executed inside a
hidden (1dp, alpha 0, non-interactive) WebView that only acts as a JS engine; its DOM
is scraped and translated into native Compose UI. Interactions (selects/buttons) are
replayed into the hidden DOM.

## Hard rules
- No visible WebView anywhere.
- Do NOT commit to git. Do NOT push. (User has not asked for commits.)
- Do NOT edit this plan file; the orchestrator tracks status.
- Only touch the canonical paths listed for your task.

## Canonical APIs (agents must match exactly)

### SectionBlock.kt  (ui/section)
```kotlin
package dev.vtoppp.vtop.ui.section

sealed interface SectionBlock {
    data class Heading(val text: String, val level: Int) : SectionBlock
    data class Label(val text: String) : SectionBlock
    data class Text(val text: String) : SectionBlock
    data class Field(val label: String, val value: String) : SectionBlock
    data class Select(
        val actionIndex: Int?,
        val options: List<Option>,
        val selected: String,
    ) : SectionBlock {
        data class Option(val value: String, val label: String)
    }
    data class Action(val actionIndex: Int?, val label: String) : SectionBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : SectionBlock
}
```

### SectionParser.kt (ui/section)
```kotlin
object SectionParser { fun parse(html: String): List<SectionBlock> }
```
Jsoup-based. Emits blocks for visible content only. Skips script/style/noscript/meta/link/option.
- h1..h6 tags and elements whose class list contains h1..h6 -> Heading(level).
- label -> Label; standalone text containers -> Text (normalized whitespace, NBSP trimmed).
- select -> Select(actionIndex from data-vtop-act attr, options value/label, selected from option[selected]).
- button, input[type=button|submit], [onclick] elements -> Action(actionIndex, label).
- input[type=text|date|number|password] -> Field(label from aria-label/name/placeholder, value from value).
- table -> Table(headers from th row, rows from td cells). Never descend into tables.
- Inline-only containers (span/b/i/strong/em/u/small/font/br/a without onclick) collapse to one Text.
- Decode entities; drop empty blocks.

### SectionBridge.kt (ui/section)
```kotlin
internal object SectionBridge {
    fun hostPage(fragmentHtml: String): String
    fun scrapeJs(): String
    fun actionJs(actionIndex: Int, value: String?): String
    const val INTERFACE_NAME = "VTopBridge"
}
```
`hostPage` returns a full HTML document with:
- `<base href="https://vtop.vitbhopal.ac.in/vtop/">`
- Shim script BEFORE the fragment (see shim contract).
- `<div id="page-wrapper">` wrapping the fragment (external `<script src>` and `<link href>` stripped).
- Bootstrap CSS not required (native rendering).
`scrapeJs` tags actionable nodes (`select, button, input[type=button], input[type=submit], [onclick]`)
inside `#page-wrapper` with `data-vtop-act="<n>"` and returns `#page-wrapper` innerHTML.
`actionJs(index, value)`: for a select, sets value, updates `selected` attributes and dispatches
`change`; otherwise `el.click()`.

### HiddenSectionHost.kt (ui/section)
```kotlin
class HiddenSectionHost(context: Context, cookies: List<Pair<String, String>>) : AutoCloseable {
    val view: WebView
    var onSnapshot: ((String) -> Unit)?
    fun open(fragmentHtml: String)
    fun trigger(actionIndex: Int, value: String?)
    override fun close()
}
```
- Sync cookies into `CookieManager` for https://vtop.vitbhopal.ac.in with `Path=/vtop`.
- `settings.javaScriptEnabled = true`, `domStorageEnabled = true`, no images.
- JS interface `VTopBridge.onDomChanged()` (background thread) -> post scrape.
- Scrape result from `evaluateJavascript` is JSON-encoded; decode with `JSONTokener`.
- Debounce scrapes; ignore after close. `close()` removes interface, stops loading, destroys.

### SectionScreen.kt (ui/section)
```kotlin
@Composable
fun SectionScreen(html: String, cookies: List<Pair<String, String>>, modifier: Modifier = Modifier)
```
- Initial blocks = `SectionParser.parse(html)` (instant native render).
- Hidden host from `LocalContext.current`; attach `host.view` via `AndroidView` at 1.dp, `alpha(0f)`,
  non-clickable/focusable, inside a Box with the LazyColumn; `DisposableEffect(html)` opens/closes.
- onSnapshot: `blocks = SectionParser.parse(snapshot)`.
- Native rendering: LazyColumn of cards. Heading -> title styles; Label -> bold small;
  Text -> body; Field -> label/value row; Select -> ExposedDropdownMenuBox
  (`trigger(index, value)` on choose, disabled when index null); Action -> FilledTonalButton
  (`trigger(index, null)`); Table -> horizontally scrollable table with per-column widths
  `(maxChars * 8 + 32).coerceIn(64, 360).dp`, header row bold, dividers.
- No WebView pixels visible at any point.

## Tasks

### T2: Parser + model + unit tests
Owner files:
- app/src/main/java/dev/vtoppp/vtop/ui/section/SectionBlock.kt (create)
- app/src/main/java/dev/vtoppp/vtop/ui/section/SectionParser.kt (create)
- app/src/test/java/dev/vtoppp/vtop/ui/section/SectionParserTest.kt (create)
- app/src/test/resources/fixtures/*.html (create, sanitized)
Acceptance:
- Fixtures copied from /tmp/opencode/vtop/fetch_tmp (timetable.html, StudentAttendance.html,
  StudentMarkView.html, StudentGradeView.html, Payments.html), sanitized: `TESTUSER` -> `TESTUSER`,
  csrf UUIDs -> `00000000-0000-0000-0000-000000000000`; remove any other personal data.
- Tests assert real shapes: timetable -> Heading + Select with 7 options incl BL20262701 + Table rows;
  attendance -> Select + Action; grade -> Select + Action; payments -> non-empty parse.
- `./gradlew :app:testDebugUnitTest --tests '*SectionParserTest*'` passes (retry once if a
  concurrent agent's files are mid-flight).

### T3: Hidden WebView host + JS shim
Owner files:
- app/src/main/java/dev/vtoppp/vtop/ui/section/SectionBridge.kt (create)
- app/src/main/java/dev/vtoppp/vtop/ui/section/HiddenSectionHost.kt (create)
Acceptance:
- Exact APIs above. JS shim contract:
  - `ConfirmBox` (withParameters/call/show/connectServer/showText/closeModal) that POSTs
    `submitTo.url` as x-www-form-urlencoded, body from `submitTo.data` (encodeURI) or
    `submitTo.dataform` (serialize form), inserts response into `updateResponseTo`, runs inserted
    scripts, calls onSuccess/onError inside try/catch.
  - No-op globals: blockGUI, unblockGUI, showMessage, showMessageAndFocusInput,
    showAndClearMessage, callScrollTop, ajaxCall (fetch POST into target + notify).
  - `bootstrap.Modal` stub with show/hide; `window.open` stub.
  - MutationObserver (debounced 300ms) + fetch/XHR completion hooks -> `VTopBridge.onDomChanged()`.
  - Strip external script/link tags from the fragment; keep inline scripts.
- Verification: static review + report; no Gradle run required (other agents own compile).

### T6: Section mapping inventory (research/docs only)
Owner files:
- docs/section-map.md (create)
- /tmp/opencode/vtop/sections/*.html (create; outside repo)
Acceptance:
- Reuse /tmp/opencode/vtop/fetch.cookies + fetch_tmp/content.html (csrf not needed to read files).
- POST each menu endpoint from HomeViewModel.menuSections() using dashboard csrf + authorizedID
  + verifyMenu=true; save raw HTML per endpoint. 2s spacing; stop immediately if session expired
  (do NOT re-login).
- docs/section-map.md: one row per endpoint: section, label, HTTP status, size, interaction
  pattern (static / select+ConfirmBox / ConfirmBox dataform / ajaxCall / jQuery ajax / form submit),
  and whether it contains tables. Note any fetch failures.

### T4: Renderer + integration (wave 2, after T2-T3)
Owner files:
- app/src/main/java/dev/vtoppp/vtop/ui/section/SectionScreen.kt (create)
- app/src/main/java/dev/vtoppp/vtop/ui/home/HomeScreen.kt (edit)
- app/src/main/java/dev/vtoppp/vtop/ui/home/HomeViewModel.kt (edit)
- app/src/main/java/dev/vtoppp/vtop/data/VtopClient.kt (edit: remove dashboardHtml only)
Acceptance:
- SectionReady(title, html, cookies); HomeScreen renders SectionScreen; all visible WebView code
  and dashboard-hosting helpers removed.
- `./gradlew :app:assembleDebug` passes.

## Orchestrator (T5)
Final build, device install, native screen verification, fix wave, plan status, cleanup.

## Status (completed)

- T2 parser: DONE. SectionBlock.kt + SectionParser.kt + 12 JUnit tests green; sanitized
  fixtures in app/src/test/resources/fixtures. RED -> GREEN evidence recorded by the agent.
- T3 hidden host: DONE. SectionBridge.kt (host page + shim + scrape/action JS) and
  HiddenSectionHost.kt (1dp hidden WebView, cookie sync, JS bridge, debounced snapshots).
- T4 renderer/integration: DONE. SectionScreen.kt renders blocks natively; HomeScreen no
  longer contains any WebView code; HomeViewModel SectionReady(title, html, cookies);
  dashboardHtml removed from VtopClient. Build + parser tests pass.
- T6 mapping inventory: DONE. docs/section-map.md: 27 endpoints (26 x 200, 1 x 404
  spotlight), patterns classified; raw responses in /tmp/opencode/vtop/sections.
- Fix wave: DONE. Loading/Failure states have native UI + retry, stuck-Loading guarded by
  generation counter, Menu always available; backToMenu restores the menu; bootstrap
  modal/offcanvas subtrees are skipped by the parser (attendance detail noise gone).
- Device verification (Pixel 6 Pro): Time Table native screen + semester select triggers
  hidden-WebView ConfirmBox fetch -> native course table + schedule rows; Class Attendance
  native screen + semester select -> native attendance table. No WebView pixels anywhere.

Known gaps (future work): sections whose actions use jQuery ($.ajax/DataTables/plugins)
do not fetch on tap (content still renders natively); actions inside table cells are not
tappable; modal-only content is intentionally not shown; only the Time Table/Attendance
dance has been device-verified. No git commits were made.

