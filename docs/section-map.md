# VTOP Section Map

**Date:** 2026-10-24 (UTC)
**Session source:** existing cookie jar `/tmp/opencode/vtop/fetch.cookies`; no re-login was performed.
A fresh `GET /vtop/content` returned `200` with `122730` bytes, and its dashboard
`csrfValue`/`authorizedIDX` matched `/tmp/opencode/vtop/fetch_tmp/content.html`.
**Fetch method:** one `POST` per endpoint with
`verifyMenu=true&authorizedID=<id>&_csrf=<csrf>&nocache=<epochMillis>`,
`Referer: https://vtop.vitbhopal.ac.in/vtop/content`, and a Chrome mobile UA.
Requests were spaced 2 seconds apart; no retries. Raw responses are in
`/tmp/opencode/vtop/sections/<endpoint with / replaced by _>.html`, with status
and size metadata in `/tmp/opencode/vtop/sections/_meta.tsv`.

**Sanitization:** this document contains no cookie values, CSRF tokens,
authorized IDs, names, roll numbers, account numbers, or email addresses. Raw
server responses still contain that data and live only in `/tmp`, not in the
repository. File names are sanitized only by replacing `/` with `_`.

**Counts:** `Tables` and `Selects` are counts of actual DOM tags after removing
HTML comments and script bodies. `Notes` gives `btn` = `<button>` tags, `inp` =
`<input>` tags, `ta` = `<textarea>` tags, `inline` = inline `<script>` tags,
and `ext` = external `<script src>` tags. A total of 44 tables and 15 selects
were found across 15 and 10 endpoints respectively. `static` means no server
interaction was observed; client-side-only JavaScript is called out in parentheses
(e.g. accordion, barcode modal, print).

## Inventory

| Section | Label | Endpoint | Status | Bytes | Pattern | Tables | Selects | Notes |
|---|---|---|---:|---:|---|---:|---:|---|
| Student | Profile | `studentsRecord/StudentProfileAllView` | 200 | 71591 | static (client-side accordion) | 4 | 0 | btn=5 inp=2 inline=0 ext=0; Bootstrap `data-bs-toggle=collapse` buttons; all sections are in the DOM; 4 td-only tables |
| Student | Credentials | `proctor/viewStudentCredentials` | 200 | 7644 | static | 3 | 0 | btn=0 inp=2 inline=0 ext=0; form `action=addStudents` has no submit control or script; 3 tables incl. nested; td-only headers |
| Student | Spot-light | `spotlight/spotlightViewOld` | 404 | 682 | failed (HTTP 404) | 0 | 0 | Tomcat 9.0.36 `HTTP Status 404` page; not VTOP content |
| Student | Acknowledgement View | `admissions/AcknowledgmentView` | 200 | 13684 | static | 2 | 0 | btn=0 inp=4 inline=0 ext=0; two forms without action/method; hidden inputs only; td-only tables |
| Student | Student Bank Info | `studentBankInformation/BankInfoStudent` | 200 | 12356 | jQuery ajax (multipart form) | 1 | 1 | btn=1 inp=10 inline=1 ext=4; text/select/radio/file controls are inside a table; Save calls `$.ajax` with `FormData` |
| Student | Contact Us | `hrms/contactDetails` | 200 | 1479 | static | 0 | 0 | btn=0 inp=0 inline=0 ext=0; contact cards only |
| Academics | Class Messages | `academics/common/StudentClassMessage` | 200 | 2306 | static (empty) | 0 | 0 | btn=0 inp=0 inline=1 ext=4; empty state: "No Messages Sent by Faculty" |
| Academics | My Curriculum | `academics/common/Curriculum` | 200 | 169506 | plain form submit (PDF) | 13 | 0 | btn=1 inp=16 inline=1 ext=5; button listener calls `form.submit()` to `academics/curriculDown`; 12 `example_N` tables plus 1; Bootstrap tabs |
| Academics | Time Table | `academics/common/StudentTimeTable` | 200 | 11568 | select+ConfirmBox | 0 | 1 | btn=0 inp=2 inline=2 ext=0; select onchange -> `ConfirmBox submitTo.data` -> `#loadMyFragment`; no timetable in initial response |
| Academics | Class Attendance | `academics/common/StudentAttendance` | 200 | 15137 | select+ConfirmBox | 1 | 1 | btn=1 inp=2 inline=2 ext=1; select onchange -> `ConfirmBox submitTo.data` -> `#loadMyFragment`; detail modal table is empty initially |
| Academics | Course Page | `academics/common/StudentCoursePage` | 200 | 13055 | jQuery ajax (cascading selects) | 0 | 4 | btn=0 inp=2 inline=1 ext=4; 4 selects (semester/course/slot/faculty), each onchange -> `$.ajax` into its div |
| Academics | Academics Calendar | `academics/common/CalendarPreview` | 200 | 11979 | jQuery ajax (cascading selects) | 1 | 2 | btn=0 inp=2 inline=1 ext=4; 2 selects onchange -> `$.ajax`; calendar table shell |
| Academics | Course Withdraw | `academics/withdraw/courseWithdraw` | 200 | 6635 | jQuery ajax (modal flow) | 1 | 0 | btn=2 inp=1 inline=1 ext=6; empty course table/modal; `$.ajax` for process/request/confirm/reload; no visible course action |
| COE | Exam Schedule | `examinations/StudExamSchedule` | 200 | 7017 | jQuery ajax (form submit intercepted) | 0 | 1 | btn=1 inp=1 inline=1 ext=2; Search submit -> validationEngine -> `$.ajax doSearchExamScheduleForStudent` |
| COE | Marks | `examinations/StudentMarkView` | 200 | 6862 | jQuery ajax (select onchange) | 0 | 1 | btn=0 inp=1 inline=1 ext=2; select onchange -> `$.ajax doStudentMarkView` -> `#main-section` |
| COE | Grades | `examinations/examGradeView/StudentGradeView` | 200 | 6707 | select+ConfirmBox dataform | 0 | 1 | btn=0 inp=2 inline=1 ext=0; select onchange -> `ConfirmBox submitTo.dataform` -> `#main-section` |
| COE | Grade History | `examinations/examGradeView/StudentGradeHistory` | 200 | 55486 | ConfirmBox dataform | 8 | 0 | btn=0 inp=1 inline=1 ext=0; 8 tables incl. nested; 3 `toggleDiv` links inside cells; `ConfirmBox dataform` for details |
| Hostels | Late Hour Request | `hostels/late/hour/student/request/1` | 200 | 11493 | jQuery ajax (data-* buttons) | 0 | 0 | btn=3 inp=0 inline=1 ext=1; 3 buttons with `data-urlText` -> `$.ajax`; `ConfirmBox` appears only in commented-out code |
| Hostels | Leave Request | `hostels/student/leave/1` | 200 | 6531 | static notice (AJAX helpers defined) | 0 | 0 | btn=0 inp=0 inline=1 ext=0; "This option is for Hosteller Only"; generic `ajaxCall`/`ajaxCallProcess` helpers defined but no control invokes them |
| Hostels | Leave History | `hostels/student/barcodeleave` | 200 | 19176 | static (client-side barcode modal) | 1 | 0 | btn=0 inp=0 inline=1 ext=1; 5 `viewBarcode` links inside table cells; base64 barcode in `data-barcode`; `swal`, no server call |
| Hostels | Hostel Room Allotment | `hostels/room/allotment/home` | 200 | 1039 | static (empty) | 0 | 0 | btn=0 inp=0 inline=0 ext=0; "No allotment records found." |
| Finance | Payments | `finance/Payments` | 200 | 11477 | static notice (jQuery ajax handlers) | 1 | 0 | btn=2 inp=2 inline=2 ext=1; no visible pay action ("no payment dues"); hidden modals; `$.ajax` handlers defined; `homePageForm.submit()` after success |
| Finance | Payment Receipts | `finance/getStudentReceipts` | 200 | 9998 | jQuery ajax (row buttons) | 1 | 0 | btn=6 inp=14 inline=2 ext=0; 6 icon-only view buttons inside table cells -> `$.ajax doDuplicateReceipt`; hidden inputs repeated per row |
| Finance | Fees Intimation | `finance/getFeesIntimation` | 200 | 23435 | static (client-side print) | 3 | 0 | btn=1 inp=0 inline=1 ext=14; 3 tables; Print -> client-side `window.open`/print; datepicker libraries referenced but no date input captured |
| Others | Apply Bonafide | `others/bonafide/StudentBonafidePageControl` | 200 | 21016 | jQuery ajax (select onchange) | 3 | 1 | btn=0 inp=3 inline=2 ext=2; select onchange -> `$.ajax StudentBonafideOnChange`; 3 tables incl. nested; datetimepicker referenced but no date input captured |
| Others | Support & Complaints | `ticketrise/createTicket` | 200 | 26192 | jQuery ajax (multipart form) | 1 | 2 | btn=7 inp=5 ta=1 inline=1 ext=0; multipart form; submit intercepted by `$.ajax`; textarea+file; client-side ticket table/filter |
| Others | Change Password | `controlpanel/ChangePassword` | 200 | 5394 | raw XHR (FormData) | 0 | 0 | btn=1 inp=6 inline=1 ext=0; 3 password inputs + checkbox; `doUpdatePassword` uses `XMLHttpRequest` POST `/vtop/controlpanel/UpdatePassword`; `$.blockUI` only |

## Failures

| Endpoint | Result | Reason |
|---|---|---|
| `spotlight/spotlightViewOld` | 404 | Tomcat 9.0.36 status page; the endpoint returns no VTOP content. No retry was attempted. |

No other endpoint failed: all remaining 26 returned `200` with
`text/html;charset=UTF-8` (the 404 is `text/html;charset=utf-8`). No response
was a login redirect or login page, so the run did not abort.

The following are successful responses with empty/notice content, not failures:
`hostels/room/allotment/home` (no records), `academics/common/StudentClassMessage`
(no messages), `hostels/student/leave/1` (hosteller-only notice),
`finance/Payments` (no dues), and `academics/withdraw/courseWithdraw`
(empty course table/modal).

## Parser implications

- **Documents and fragments are mixed.** 24 of the 26 successful responses start
  with `<!DOCTYPE html>` and contain `head`/`body`; Acknowledgement View and
  Support & Complaints return body-only fragments (`<div ...>`), and the 404 is a
  bare Tomcat page. `SectionBridge.hostPage` must handle both full documents and
  fragments rather than blindly wrapping every response inside
  `<div id="page-wrapper">`.
- **Select drive is common.** 10 endpoints contain 15 selects. 11 `onchange`
  attributes occur across 7 endpoints. Selecting a semester triggers either
  `ConfirmBox` (Time Table, Attendance, Grades) or `$.ajax` (Calendar, Course Page,
  Marks, Bonafide). Course Page, Calendar, and Bonafide are cascading: a response
  replaces a subtree and inserts the next select, so the host must rescan after
  each response.
- **ConfirmBox has two payload shapes.** Time Table and Attendance use
  `submitTo.data` and update `#loadMyFragment`; Grades and Grade History use
  `submitTo.dataform` (serializes the form). The shim must implement both and keep
  hidden inputs alive in the hidden DOM. Common helper scripts also call
  `ConfirmBox` for `showMessage`, but only the four endpoints above have
  `submitTo` calls.
- **jQuery is assumed, but not shipped in the fragments.** 12 fragments contain
  `$.ajax` and 8 contain `$.blockUI`; 16 have inline scripts referencing `$`,
  `jQuery`, `swal`, `dataTable`, `validationEngine`, or `bootstrap`. The external
  script tags (51 tags, 17 unique sources: validationEngine, DataTables,
  sweetalert, validate2, Bootstrap pickers/multiselect, summernote, and one
  Bootstrap CDN bundle) contain no jQuery source. If the host strips external
  scripts without providing jQuery itself, these inline handlers will throw.
- **Actions and fields inside tables are the biggest model gap.** 44 tables occur
  in 15 endpoints. Receipts has 6 icon-only view buttons in cells; Grade History
  has 3 `toggleDiv` links in cells; Leave History has 5 barcode links in cells;
  Bank Info puts text, select, radio, and file controls inside a table. The
  current `Table(headers, rows)` block stores only strings, and "never descend
  into tables" drops these controls. A cell-level action/field representation or
  a special-case extractor is needed for those pages.
- **Table shapes are irregular.** Several tables use `<td>` for their header row
  (Profile, Credentials, Acknowledgement, Receipts, Fees Intimation), not `<th>`.
  `colspan`/`rowspan` are used, and 3 endpoints contain nested tables (Grade
  History, Bonafide, Credentials). Extracting headers only from `th` will leave
  many tables headerless; flat row extraction can misalign columns.
- **Unsupported input types.** Ticket has a `textarea` and a `file` input; Bank
  Info has `radio` and `file`; Change Password has `password` and `checkbox`.
  `SectionBlock.Field` only covers text/date/number/password, so textarea, file,
  radio, and checkbox need model extensions or custom blocks. Icon-only buttons
  (Receipts) also need an accessible `aria-label`/`title` fallback because their
  text content is only an `<i>` icon.
- **Raw XHR and form submits exist.** Change Password uses `XMLHttpRequest`, not
  `$.ajax`, so the host's XHR completion hook is required. Curriculum uses a plain
  `form.submit()` to a PDF endpoint. Payments calls `homePageForm.submit()` after a
  successful ajax call. Ticket's form has an `action` but its submit handler uses
  `$.ajax` with `FormData`.
- **Hidden Bootstrap UI will leak into the parser.** Profile uses collapse
  accordions, Curriculum uses tabs, and Payments/Attendance/Withdraw use modals.
  Without Bootstrap CSS, `.collapse`, `.tab-pane`, and `.modal` content is still in
  the DOM and can be emitted as ordinary blocks. "Visible content only" needs
  class-based exclusion or serialization of the current visible state.
- **Dates were not present in these captures.** No `input[type=date]` or visible
  date text field appears in any of the 27 responses. Fees Intimation and Bonafide
  reference datepicker/datetimepicker libraries, but the fields themselves are not
  in the initial response. Do not infer date fields for these endpoints from the
  script includes alone; follow-up responses were not fetched in this task.
- **Dashboard globals can be missing.** Bank Info has a
  `javascript:vtopDownload(...)` link, but `vtopDownload` is not defined in the
  fragment; it must come from the host page or the click will fail. Similar
  assumptions apply to jQuery, `swal`, and Bootstrap collapse.

### Unique external script sources observed

`/vtop/assets/js/datatables.js`, `/vtop/assets/js/jquery.validationEngine-en.js`,
`assets/js/bootstrap-datepicker.js`, `assets/js/bootstrap-datetimepicker.js`,
`assets/js/bootstrap-multiselect.js`, `assets/js/bootstrap-timepicker.min.js`,
`assets/js/datatables.js`, `assets/js/datatables.min.js`,
`assets/js/daterangepicker.js`, `assets/js/jquery.dataTables.min.js`,
`assets/js/jquery.validate.min.js`, `assets/js/jquery.validationEngine-en.js`,
`assets/js/jquery.validationEngine.js`, `assets/js/summernote/summernote.min.js`,
`assets/js/sweetalert.min.js`, `assets/js/validate2.js`,
`https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js`.
