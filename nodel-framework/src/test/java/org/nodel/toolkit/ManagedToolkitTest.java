package org.nodel.toolkit;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.nodel.GitHubIssue;
import org.nodel.SimpleName;
import org.nodel.core.NodelServerAction;
import org.nodel.core.NodelServerEvent;
import org.nodel.core.NodelServers;
import org.nodel.host.BaseDynamicNode;
import org.nodel.host.Binding;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedToolkitTest {

    private ManagedToolkit managedToolkit;
    private Binding metadata;
    private ActionFunction actionFunction;

    @FunctionalInterface
    interface ActionFunction {
        void handle(Object arg);
    }

    @BeforeEach
    void setUp() {
        BaseDynamicNode mockNode = mock(BaseDynamicNode.class);
        when(mockNode.getName()).thenReturn(new SimpleName("MockNode"));
        managedToolkit = new ManagedToolkit(mockNode);
        metadata = new Binding();
        actionFunction = arg -> {};
    }

    @Disabled("Awaiting fix for issue: #284")
    @DisplayName("Original name of generated action")
    @GitHubIssue("https://github.com/museumsvictoria/nodel/issues/284")
    @ParameterizedTest
    @CsvSource({
            "Action With Spaces, Action With Spaces",
            "Action-With-Hyphens, Action-With-Hyphens",
    })
    void testCreateActionWithDefaultName(String actionName, String expectedName) {
        try (NodelServerAction action = managedToolkit.createAction(actionName, actionFunction::handle, metadata)) {
            assertEquals(expectedName, action.getAction().getOriginalName());
        }
    }

    @DisplayName("Reduced name of generated action")
    @ParameterizedTest
    @CsvSource({
            "Action With Spaces, ActionWithSpaces",
            "Action-With-Hyphens, ActionWithHyphens",
    })
    void testCreateActionWithReducedName(String actionName, String expectedName) {
        try (NodelServerAction action = managedToolkit.createAction(actionName, actionFunction::handle, metadata)) {
            assertEquals(expectedName, action.getAction().getReducedName());
        }
    }

    @Disabled("Awaiting fix for issue: #284")
    @DisplayName("Original name of generated event")
    @GitHubIssue("https://github.com/museumsvictoria/nodel/issues/284")
    @ParameterizedTest
    @CsvSource({
            "Event With Spaces, Event With Spaces",
            "Event-With-Hyphens, Event-With-Hyphens",
    })
    void testCreateEventWithDefaultName(String eventName, String expectedName) {
        try (NodelServerEvent event = managedToolkit.createEvent(eventName, metadata)) {
            NodelServers.instance().registerEvent(event);
            assertEquals(expectedName, event.getEvent().getOriginalName());
        }
    }

    @DisplayName("Reduced name of generated event")
    @ParameterizedTest
    @CsvSource({
            "Event With Spaces, EventWithSpaces",
            "Event-With-Hyphens, EventWithHyphens",
    })

    void testCreateEventWithReducedName(String eventName, String expectedName) {
        try (NodelServerEvent event = managedToolkit.createEvent(eventName, metadata)) {
            NodelServers.instance().registerEvent(event);
            assertEquals(expectedName, event.getEvent().getReducedName());
        }
    }

    @Test
    @DisplayName("Test getURLAsync method exists and returns CompletableFuture")
    void testGetURLAsync_MethodSignature() {
        // Test that the method exists and has correct signature
        try {
            java.lang.reflect.Method method = ManagedToolkit.class.getMethod("getURLAsync", 
                String.class, String.class, java.util.Map.class, String.class, String.class, 
                java.util.Map.class, String.class, String.class, Integer.class, Integer.class);
            
            assertEquals(CompletableFuture.class, method.getReturnType());
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("getURLAsync method should exist with proper signature");
        }
    }
    
    @Test
    @DisplayName("Test CompletableFuture callback pattern")
    void testCompletableFutureCallbackPattern() throws Exception {
        // Test that CompletableFuture callback pattern works as expected for our Python integration
        CompletableFuture<String> future = new CompletableFuture<>();
        
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicReference<Exception> errorHolder = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        
        // Mimic the Python callback pattern from nodetoolkit.py
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                resultHolder.set(result);
            } else {
                errorHolder.set((Exception) exception);
            }
            completed.set(true);
        });
        
        // Complete the future
        future.complete("test result");
        
        // Wait briefly for callback
        Thread.sleep(100);
        
        assertTrue(completed.get(), "Callback should have been called");
        assertEquals("test result", resultHolder.get(), "Should have received result");
        assertNull(errorHolder.get(), "Should not have error");
    }
}