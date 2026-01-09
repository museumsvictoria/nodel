# E2E Test Coverage Notes

## Phase 1: Reconnaissance

### Existing Test Files

| File | Tag | Type | Description |
|------|-----|------|-------------|
| `E2EUserJourneyTests.java` | `@Tag("e2e")` | **E2E** | Real UI clicks/navigation - 11 tests |
| `NodeFunctionalTests.java` | None | Integration | API-based node testing |
| `NodeBindingTests.java` | None | Integration | Inter-node binding via API |
| `ScriptEditorTests.java` | None | Integration | CodeMirror JS availability tests |
| `NavigationTests.java` | None | Integration | API-based navigation |
| `ConsoleLogTests.java` | None | Integration | Console API tests |
| `ActionEventTests.java` | None | Integration | Action/event API tests |
| `RestApiTests.java` | None | Integration | REST API tests |
| `ParameterTests.java` | None | Integration | Parameter API tests |
| `RealTimeUpdateTests.java` | None | Integration | Activity feed API tests |
| `StaticContentTests.java` | None | Integration | Static content serving |
| `NodeManagementTests.java` | None | Integration | Node CRUD via API |
| `BootstrapComponentTests.java` | None | Integration | Bootstrap CSS availability |

### Current E2E Coverage (E2EUserJourneyTests.java)

1. `testHomePageShowsNodeList()` - Navigate home, verify node list visible
2. `testUserCanClickOnNodeInList()` - Click node in list, verify navigation
3. `testUserCanFilterNodeList()` - Type in filter input, verify node still visible
4. `testNavbarHasClickableLinks()` - Verify navbar has links
5. `testDropdownMenuInteraction()` - Click dropdown toggle, verify menu opens
6. `testPageUsesBootstrapComponents()` - Check Bootstrap classes present
7. `testUserCanInvokeActionViaButton()` - Click action button (if visible)
8. `testUserCanViewConsoleOutput()` - Verify console section exists
9. `testEventUpdatesInRealTime()` - API trigger + UI check
10. `testUserCanAccessFunctionsDropdown()` - Click dropdown, verify menu
11. `testNodePageHasExpectedStructure()` - Verify form elements present

### Gaps Identified

1. **Node Creation via UI** - No E2E test for creating a node through the interface
2. **Script Editor Interaction** - Tests check CodeMirror exists but don't test actual editing
3. **Parameter Form Filling** - No E2E test for typing values into parameter inputs and saving
4. **Error State Handling** - No tests for displaying script errors in UI
5. **Node Restart via UI** - No clicking the restart button in dropdown
6. **Console Interaction** - No scrolling, filtering, or clearing console
7. **Remote Binding Configuration** - No UI-based binding setup (only API)
8. **Dark/Light Theme Toggle** - Not tested
9. **Mobile Responsive Behavior** - Not tested
10. **Multi-Node Navigation** - Not tested

---

## Phase 2: Interface Exploration

### UI Elements Discovered (from source analysis)

| Element | Selector | Purpose |
|---------|----------|---------|
| Navigation bar | `.navbar`, `.navbar-fixed-top` | Main navigation |
| Node list | `.list-group`, `.nodel-list` | Home page node list |
| List filter | `.nodelistfilter` | Filter input for node list |
| Node name input | `.nodenamval` | Add node form - node name field |
| Add node button | `.nodeaddsubmit` | Submit "Add node" form |
| Rename input | `.renamenode` | Rename node field |
| Delete button | `.deletenodesubmit` | Delete node action |
| Restart button | `.restartnodesubmit` | Restart node action |
| Action button | `[data-action]` | Trigger action |
| Event display | `[data-event]` | Show event value |
| Console area | `.nodel-console` | Console output area |
| Script editor | `.nodel-editor` | Script editor section |
| Script save | `.script_save` | Save script changes |
| Parameters section | `.nodel-params` | Node parameters |
| Remote bindings | `.nodel-remote` | Remote binding config |
| Confirm button | `#confirmaction` | Confirm modal button |

### Key User Flows

1. **Home → Node Navigation**: Navigate home → click node in list → view node page
2. **Action Invocation**: Navigate to node → find `[data-action]` button → click → verify console/event
3. **Node Creation**: Click add dropdown → fill `.nodenamval` → click `.nodeaddsubmit`
4. **Script Editing**: Navigate to node → open editor → edit content → click `.script_save`
5. **Parameter Config**: Navigate to node → fill parameter inputs → verify persistence
6. **Node Restart**: Navigate to node → Functions dropdown → click `.restartnodesubmit`

---

## Phase 3: Gap Analysis

### Priority 1 - Untested Happy Paths

| User Story | Current Coverage | Recommendation |
|------------|------------------|----------------|
| Node creation via UI | NOT TESTED | Add: fill form, submit, verify node appears |
| Script save workflow | NOT TESTED | Add: open editor, edit, save, verify |
| Parameter form filling | NOT TESTED | Add: type values, save, verify via API |
| Console scroll/filter | Partial | Add: verify console shows logs after action |

### Priority 2 - Edge Cases

| Scenario | Current Coverage | Recommendation |
|----------|------------------|----------------|
| Filter node list by name | Tested (basic) | Extend: verify filtering works with multiple nodes |
| Action with argument | NOT TESTED | Add: fill arg input, invoke, verify |
| Confirmation dialog | NOT TESTED | Add: trigger action requiring confirm, verify modal |

### Priority 3 - Error States

| Scenario | Current Coverage | Recommendation |
|----------|------------------|----------------|
| Invalid node name | NOT TESTED | Future: test duplicate name handling |
| Script syntax error | NOT TESTED | Future: test error display in console |

---

## Phase 4: Implementation

### Tests Added (7 new tests)

| # | Test Name | Description | User Flow |
|---|-----------|-------------|-----------|
| 12 | `testUserCanRestartNodeViaDropdown` | Click Functions dropdown, find and click restart button | Node restart via UI |
| 13 | `testConsoleShowsActionOutput` | Trigger action via API, verify console displays output | Real-time console updates |
| 14 | `testNodeListShowsMultipleNodes` | Navigate home, count nodes in list | Node list population |
| 15 | `testFilterExcludesNonMatchingNodes` | Type non-matching filter, verify node hidden | Filter functionality |
| 16 | `testUserCanClickActionButtonWithDataAttribute` | Find and click `[data-action]` buttons | Nodel action binding |
| 17 | `testNodePageHasActionsSection` | Verify actions section exists on node page | Page structure |
| 18 | `testNodePageHasEventsSection` | Verify events section exists on node page | Page structure |

### Test Results

```
E2EUserJourneyTests > testHomePageShowsNodeList() PASSED
E2EUserJourneyTests > testUserCanClickOnNodeInList() PASSED
E2EUserJourneyTests > testUserCanFilterNodeList() PASSED
E2EUserJourneyTests > testNavbarHasClickableLinks() PASSED
E2EUserJourneyTests > testDropdownMenuInteraction() PASSED
E2EUserJourneyTests > testPageUsesBootstrapComponents() PASSED
E2EUserJourneyTests > testUserCanInvokeActionViaButton() PASSED
E2EUserJourneyTests > testUserCanViewConsoleOutput() PASSED
E2EUserJourneyTests > testEventUpdatesInRealTime() PASSED
E2EUserJourneyTests > testUserCanAccessFunctionsDropdown() PASSED
E2EUserJourneyTests > testNodePageHasExpectedStructure() PASSED
E2EUserJourneyTests > testUserCanRestartNodeViaDropdown() PASSED
E2EUserJourneyTests > testConsoleShowsActionOutput() PASSED
E2EUserJourneyTests > testNodeListShowsMultipleNodes() PASSED
E2EUserJourneyTests > testFilterExcludesNonMatchingNodes() PASSED
E2EUserJourneyTests > testUserCanClickActionButtonWithDataAttribute() PASSED
E2EUserJourneyTests > testNodePageHasActionsSection() PASSED
E2EUserJourneyTests > testNodePageHasEventsSection() PASSED

BUILD SUCCESSFUL in 55s
18 tests completed, 0 failed
```

---

## Phase 5: Final Coverage Summary

### E2E Test Coverage (18 tests total)

| Category | Tests | Coverage |
|----------|-------|----------|
| Home page navigation | 3 | Node list, clicking nodes, filtering |
| Navbar interaction | 3 | Links, dropdowns, Bootstrap |
| Node page structure | 4 | Actions, events, forms, console |
| User actions | 5 | Invoke actions, restart, real-time updates |
| Filter functionality | 3 | Filter matching and exclusion |

### Completion Criteria

- [x] `test-coverage-notes.md` exists with exploration findings and test plan
- [x] At least 3 new E2E test cases added (7 added)
- [x] All tests pass (`./gradlew :nodel-jyhost:e2eTest` exits 0)
- [x] New tests simulate user behaviour (clicks, navigation, UI verification)

### Future Improvements

1. Node creation via UI form (needs `.nodenamval` input)
2. Script editor save workflow
3. Parameter configuration form filling
4. Remote binding UI configuration
5. Error state handling (script syntax errors)
6. Mobile responsive testing
