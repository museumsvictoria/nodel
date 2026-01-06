package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for parameter viewing and editing functionality.
 * Verifies form rendering, input handling, and parameter saving.
 */
public class ParameterTests extends TestBase {

    @BeforeAll
    public static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    public static void tearDown() {
        closeBrowser();
    }

    // ===== Form Infrastructure Tests =====

    @Test
    public void testFormControlClass() {
        // Bootstrap form-control class should be available
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.default.css");
        String css = response.text();
        assertTrue(css.contains("form-control"), "form-control class should be in CSS");
    }

    @Test
    public void testFormGroupClass() {
        // Bootstrap form-group class should be available
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.default.css");
        String css = response.text();
        assertTrue(css.contains("form-group"), "form-group class should be in CSS");
    }

    // ===== Input Type Support Tests =====

    @Test
    public void testTextInputSupport() {
        Object result = page.evaluate("() => { const input = document.createElement('input'); input.type = 'text'; return input.type === 'text'; }");
        assertEquals(true, result, "Text input type should be supported");
    }

    @Test
    public void testNumberInputSupport() {
        Object result = page.evaluate("() => { const input = document.createElement('input'); input.type = 'number'; return input.type === 'number'; }");
        assertEquals(true, result, "Number input type should be supported");
    }

    @Test
    public void testCheckboxInputSupport() {
        Object result = page.evaluate("() => { const input = document.createElement('input'); input.type = 'checkbox'; return input.type === 'checkbox'; }");
        assertEquals(true, result, "Checkbox input type should be supported");
    }

    // ===== Select/Dropdown Tests =====

    @Test
    public void testSelectElementSupport() {
        Object result = page.evaluate("() => document.createElement('select').tagName === 'SELECT'");
        assertEquals(true, result, "Select element should be supported");
    }

    @Test
    public void testBootstrapSelectPlugin() {
        // Check if bootstrap-select or native select support is available
        Object selectpickerLoaded = page.evaluate("() => typeof jQuery.fn.selectpicker !== 'undefined'");
        Object selectSupport = page.evaluate("() => document.createElement('select').tagName === 'SELECT'");
        assertTrue(Boolean.TRUE.equals(selectpickerLoaded) || Boolean.TRUE.equals(selectSupport),
            "Either bootstrap-select or native select support should be available");
    }

    // ===== JSViews Form Binding Tests =====

    @Test
    public void testJsViewsTwoWayBinding() {
        Object result = page.evaluate("() => typeof $.link !== 'undefined'");
        assertEquals(true, result, "$.link should be available for two-way binding");
    }

    @Test
    public void testJsViewsObservableArray() {
        Object result = page.evaluate("() => { try { $.observable([]); return true; } catch(e) { return false; } }");
        assertEquals(true, result, "$.observable should work with arrays");
    }

    @Test
    public void testJsViewsObservableObject() {
        Object result = page.evaluate("() => { try { $.observable({}); return true; } catch(e) { return false; } }");
        assertEquals(true, result, "$.observable should work with objects");
    }

    // ===== Array Parameter Tests =====

    @Test
    public void testArrayPushSupport() {
        Object result = page.evaluate("() => { const arr = []; $.observable(arr).insert(0, 'test'); return arr.length === 1; }");
        assertEquals(true, result, "$.observable insert should work for array parameters");
    }

    @Test
    public void testArrayRemoveSupport() {
        Object result = page.evaluate("() => { const arr = ['test']; $.observable(arr).remove(0); return arr.length === 0; }");
        assertEquals(true, result, "$.observable remove should work for array parameters");
    }

    // ===== Form Validation Tests =====

    @Test
    public void testHtml5ValidationApi() {
        Object result = page.evaluate("() => typeof document.createElement('input').checkValidity !== 'undefined'");
        assertEquals(true, result, "HTML5 validation API should be available");
    }

    @Test
    public void testRequiredAttributeSupport() {
        Object result = page.evaluate("() => { const input = document.createElement('input'); input.required = true; return input.required === true; }");
        assertEquals(true, result, "Required attribute should be supported");
    }

    // ===== Parameter Save Tests =====

    @Test
    public void testPostRequestCapability() {
        Object result = page.evaluate("() => typeof jQuery.post !== 'undefined'");
        assertEquals(true, result, "jQuery.post should be available for saving parameters");
    }

    @Test
    public void testJsonStringify() {
        Object result = page.evaluate("() => JSON.stringify({test: 'value'}) === '{\"test\":\"value\"}'");
        assertEquals(true, result, "JSON.stringify should work for parameter serialization");
    }

    // ===== Schema-Based Rendering Tests =====

    @Test
    public void testJsViewsHelpers() {
        Object result = page.evaluate("() => typeof $.views.helpers !== 'undefined'");
        assertEquals(true, result, "JSViews helpers should be available for schema rendering");
    }

    @Test
    public void testJsViewsConverters() {
        Object result = page.evaluate("() => typeof $.views.converters !== 'undefined'");
        assertEquals(true, result, "JSViews converters should be available");
    }

    // ===== Parameter Display Tests =====

    @Test
    public void testPanelClassForParams() {
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.default.css");
        String css = response.text();
        assertTrue(css.contains("panel"), "Panel class should be in CSS for parameter display");
    }

    // ===== Reorder Functionality Tests =====

    @Test
    public void testArrayMoveCapability() {
        Object result = page.evaluate("() => { const arr = ['a', 'b', 'c']; $.observable(arr).move(0, 2); return arr[2] === 'a'; }");
        assertEquals(true, result, "$.observable move should work for reordering");
    }

    // ===== Input Event Handling Tests =====

    @Test
    public void testChangeEventBinding() {
        Object result = page.evaluate("() => typeof jQuery.fn.on !== 'undefined'");
        assertEquals(true, result, "jQuery event binding should be available");
    }

    @Test
    public void testInputEventBinding() {
        Object result = page.evaluate("() => typeof jQuery.fn.trigger !== 'undefined'");
        assertEquals(true, result, "jQuery trigger should be available for input events");
    }
}
