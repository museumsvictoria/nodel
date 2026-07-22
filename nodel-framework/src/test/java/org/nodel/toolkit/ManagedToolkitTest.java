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
}