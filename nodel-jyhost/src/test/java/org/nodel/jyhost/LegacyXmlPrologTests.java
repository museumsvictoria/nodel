package org.nodel.jyhost;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class LegacyXmlPrologTests {
    private static final String PI = "<?xml-stylesheet type=\"text/xsl\" href=\"v1/index.xsl\"?>";

    private boolean matches(String text) throws IOException {
        return LegacyXmlProlog.matches(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void supportedVariants() throws Exception {
        for (String text : new String[] {
                PI, "\ufeff<?xml version='1.0' encoding='utf-8'?>\n" + PI + "<pages/>",
                "<?xml version='1.0' encoding='US-ASCII' standalone='yes'?>" + PI,
                "<!-- ignored -->\n<?other anything here?>\n" + PI,
                "<!-- café --><?other café \ud83d\ude00?>" + PI,
                "<?xml-stylesheet\n href = './v1/index.xsl'\t type = 'text/xsl' alternate='no' ?>",
                "<?xml-stylesheet href='other.xsl'?>" + PI }) {
            assertTrue(matches(text), text);
        }
    }

    @Test void unsupportedOrMalformedPrologs() throws Exception {
        for (String text : new String[] {
                "", "<pages/>", "<!--" + PI + "--><pages/>", "<pages>" + PI + "</pages>",
                "<!DOCTYPE pages>" + PI, "<![CDATA[anything]]>" + PI,
                "<?xml version='1.0' encoding='UTF-16'?>" + PI,
                "<?xml version='1.0' encoding='ISO-8859-1'?>" + PI,
                "<?xml version='1.0' encoding='UTF-8' encoding='UTF-16'?>" + PI,
                "<?xml version='1.0'?><?xml version='1.0'?>" + PI,
                " <?xml version='1.0'?>" + PI, "<?XML version='1.0'?>" + PI,
                "<?xml-stylesheet type='text/xsl' href='https://evil/v1/index.xsl'?>",
                "<?xml-stylesheet type='text/xsl' href='/v1/index.xsl'?>",
                "<?xml-stylesheet type='text/xsl' href='other/index.xsl'?>",
                "<?xml-stylesheet type='text/css' href='v1/index.xsl'?>",
                "<?xml-stylesheet type='text/xsl' href='v1/index.xsl' alternate='yes'?>",
                "<?xml-stylesheet type='text/xsl' href='v1/index.xsl' alternate='bogus'?>",
                "<?xml-stylesheet type='text/xsl' type='text/xsl' href='v1/index.xsl'?>",
                "<?xml-stylesheet type='text/xsl' href='v1/index.xsl' href='other.xsl'?>",
                "<?xml-stylesheet type='text/xsl'href='v1/index.xsl'?>",
                "<?xml-stylesheet type=text/xsl href='v1/index.xsl'?>",
                "<?xml-stylesheet type='text/xsl' href='v1&#47;index.xsl'?>",
                "<?xml-stylesheet-extra type='text/xsl' href='v1/index.xsl'?>",
                "<?xml-stylesheet type='text/xsl' href='v1/index.xsl'",
                "<?other incomplete " + PI, "<!-- broken -- comment -->" + PI,
                "<!-- -\u0000 -->" + PI,
                "garbage" + PI, "\u0000" + PI,
                " ".repeat(LegacyXmlProlog.READ_BUDGET) + PI }) {
            assertFalse(matches(text), text.substring(0, Math.min(100, text.length())));
        }
    }

    @Test void processingInstructionTargetSeparators() throws Exception {
        for (String preceding : new String[] {
                "<?other?broken?>", "<?other??>", "<?other? broken?>", "<?other?",
                "<?other>", "<?other/broken?>", "<?other=broken?>", "<?other!broken?>",
                "<?xml?version='1.0'?>", "<?xml-stylesheet?type='text/xsl' href='v1/index.xsl'?>" }) {
            assertFalse(matches(preceding + PI), preceding);
        }
        for (String preceding : new String[] {
                "<?other?>", "<?other ?>", "<?other ?broken?>", "<?other ??broken?>",
                "<?other data?still?>", "<?other.broken?>" }) {
            assertTrue(matches(preceding + PI), preceding);
        }
    }

    @Test void readBudgetAndEarlyExit() throws Exception {
        for (String prefix : new String[] { PI, "<pages>", "<!--" + "x".repeat(20000), "<?other " + "x".repeat(20000) }) {
            CountingInput input = new CountingInput((prefix + "x".repeat(50000)).getBytes(StandardCharsets.UTF_8));
            assertEquals(prefix.equals(PI), LegacyXmlProlog.matches(input));
            assertTrue(input.count <= LegacyXmlProlog.READ_BUDGET);
            if (prefix.equals(PI) || prefix.equals("<pages>")) assertTrue(input.count <= 1024);
            else assertEquals(LegacyXmlProlog.READ_BUDGET, input.count);
        }
        String exact = " ".repeat(LegacyXmlProlog.READ_BUDGET - PI.length()) + PI;
        assertTrue(matches(exact));
        assertFalse(matches(" " + exact));
    }

    @Test void handlesShortReadsAndDoesNotOwnStream() throws Exception {
        byte[] data = ("\ufeff" + PI).getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(data) {
            @Override public synchronized int read(byte[] b, int off, int len) { return super.read(b, off, Math.min(1, len)); }
            @Override public void close() { fail("caller owns the stream"); }
        };
        assertTrue(LegacyXmlProlog.matches(input));
        for (byte[] invalid : new byte[][] {
                {(byte) 0xff}, {(byte) 0xc0, (byte) 0x80}, {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, {(byte) 0xef, (byte) 0xbb},
                {(byte) 0xff, (byte) 0xfe, '<', 0} }) {
            assertFalse(LegacyXmlProlog.matches(new ByteArrayInputStream(invalid)));
        }
        assertThrows(IOException.class, () -> LegacyXmlProlog.matches(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("scan failed"); }
        }));
    }

    private static class CountingInput extends ByteArrayInputStream {
        int count;
        CountingInput(byte[] bytes) { super(bytes); }
        @Override public synchronized int read(byte[] b, int off, int len) {
            assertTrue(count + len <= LegacyXmlProlog.READ_BUDGET, "must not even request bytes past budget");
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }
    }
}
