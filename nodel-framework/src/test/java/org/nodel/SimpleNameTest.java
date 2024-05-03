package org.nodel;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleNameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "node_name",
            "Node Name",
            "node-name",
            "node.name",
            "node name"
    })
    void testGetOriginalName(String originalName) {
        SimpleName name = new SimpleName(originalName);
        assertEquals(originalName, name.getOriginalName());
    }

    @ParameterizedTest
    @CsvSource({
            "node_name, nodename",
            "Node Name, NodeName",
            "node-name, nodename",
            "node.name, nodename",
            "node name, nodename"
    })
    public void testGetReducedName(String originalName, String expectedReducedName) {
        SimpleName name = new SimpleName(originalName);
        assertEquals(expectedReducedName, name.getReducedName());
    }
}