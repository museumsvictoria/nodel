package org.nodel.jyhost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.nodel.SimpleName;

class NodelHostFilenameEncodingTest {

    @Test
    void encodesWindowsReservedCharacters() {
        assertEquals("%5C%2F%3A%2A%3F%22%3C%3E%7C",
                NodelHost.encodeIntoSafeFilename(new SimpleName("\\/:*?\"<>|")));
    }

    @Test
    void decodesEncodedAndLegacyAsteriskFilenames() {
        assertEquals("Node*Name", NodelHost.decodeFilenameIntoName("Node%2AName").getOriginalName());
        assertEquals("Node*Name", NodelHost.decodeFilenameIntoName("Node*Name").getOriginalName());
    }
}
