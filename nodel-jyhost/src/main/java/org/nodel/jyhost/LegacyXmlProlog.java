package org.nodel.jyhost;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conservative, bounded recognition of the legacy UI stylesheet, not an XML parser. */
final class LegacyXmlProlog {
    static final int READ_BUDGET = 16 * 1024;

    private LegacyXmlProlog() { }

    static boolean matches(InputStream input) throws IOException {
        Scanner scanner = new Scanner(input);
        int ch = scanner.read();
        if (ch == 0xfeff) {
            ch = scanner.read();
        }
        boolean declarationAllowed = true;
        while (ch != -1) {
            while (whitespace(ch)) {
                declarationAllowed = false;
                ch = scanner.read();
            }
            if (ch != '<')
                return false;
            ch = scanner.read();
            if (ch == '!') {
                if (scanner.read() != '-' || scanner.read() != '-' || !scanner.comment())
                    return false; // includes DOCTYPE and all unsupported declarations
            } else if (ch == '?') {
                StringBuilder name = new StringBuilder();
                ch = scanner.read();
                if (!nameStart(ch))
                    return false;
                do {
                    name.append((char) ch);
                    ch = scanner.read();
                } while (nameChar(ch));
                String body;
                if (ch == '?') {
                    // Without whitespace after the target, only an immediate ?> is legal.
                    if (scanner.read() != '>')
                        return false;
                    body = "";
                } else if (whitespace(ch)) {
                    body = scanner.instruction(ch);
                } else {
                    return false;
                }
                if (body == null)
                    return false;
                String target = name.toString();
                if (target.equalsIgnoreCase("xml")) {
                    if (!target.equals("xml") || !declarationAllowed || !supportedDeclaration(body))
                        return false;
                } else if (target.equals("xml-stylesheet")) {
                    Map<String, String> attrs = attributes(body);
                    if (attrs == null)
                        return false;
                    String href = attrs.get("href");
                    String alternate = attrs.get("alternate");
                    if ("text/xsl".equals(attrs.get("type"))
                            && ("v1/index.xsl".equals(href) || "./v1/index.xsl".equals(href))
                            && (alternate == null || alternate.equals("no")))
                        return true;
                }
            } else {
                return false; // first root tag (or malformed markup): never scan element content
            }
            declarationAllowed = false;
            ch = scanner.read();
        }
        return false;
    }

    private static boolean supportedDeclaration(String body) {
        Map<String, String> attrs = attributes(body);
        if (attrs == null || attrs.isEmpty() || !attrs.keySet().iterator().next().equals("version")
                || !("1.0".equals(attrs.get("version")) || "1.1".equals(attrs.get("version"))))
            return false;
        boolean standaloneSeen = false;
        for (String name : attrs.keySet()) {
            if (name.equals("encoding")) {
                String encoding = attrs.get(name);
                if (standaloneSeen || !(encoding.equalsIgnoreCase("UTF-8")
                        || encoding.equalsIgnoreCase("US-ASCII") || encoding.equalsIgnoreCase("ASCII")))
                    return false;
            } else if (name.equals("standalone")) {
                standaloneSeen = true;
                if (!(attrs.get(name).equals("yes") || attrs.get(name).equals("no")))
                    return false;
            } else if (!name.equals("version")) {
                return false;
            }
        }
        return true;
    }

    /** No entity decoding; reject duplicates and malformed pseudo-attributes. */
    private static Map<String, String> attributes(String body) {
        Map<String, String> attrs = new LinkedHashMap<>();
        int pos = 0;
        while (pos < body.length()) {
            if (!whitespace(body.charAt(pos)))
                return null;
            while (pos < body.length() && whitespace(body.charAt(pos))) pos++;
            if (pos == body.length()) break;
            int start = pos;
            if (!nameStart(body.charAt(pos))) return null;
            while (pos < body.length() && nameChar(body.charAt(pos))) pos++;
            String name = body.substring(start, pos);
            while (pos < body.length() && whitespace(body.charAt(pos))) pos++;
            if (pos == body.length() || body.charAt(pos++) != '=') return null;
            while (pos < body.length() && whitespace(body.charAt(pos))) pos++;
            if (pos == body.length()) return null;
            char quote = body.charAt(pos++);
            if (quote != '\'' && quote != '"') return null;
            start = pos;
            while (pos < body.length() && body.charAt(pos) != quote) {
                char value = body.charAt(pos++);
                if (value == '<' || value == '&' || (value < 32 && !whitespace(value))) return null;
            }
            if (pos == body.length()) return null;
            if (attrs.put(name, body.substring(start, pos++)) != null) return null;
        }
        return attrs;
    }

    private static boolean whitespace(int ch) {
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
    }

    private static boolean nameStart(int ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_' || ch == ':';
    }

    private static boolean nameChar(int ch) {
        return nameStart(ch) || ch >= '0' && ch <= '9' || ch == '-' || ch == '.';
    }

    private static final class Scanner {
        private final InputStream input;
        private final byte[] buffer = new byte[1024];
        private int fetched, pos, end;

        Scanner(InputStream input) { this.input = input; }

        private int readByte() throws IOException {
            if (pos == end) {
                if (fetched == READ_BUDGET) return -1;
                end = input.read(buffer, 0, Math.min(buffer.length, READ_BUDGET - fetched));
                if (end <= 0) return -1;
                fetched += end;
                pos = 0;
            }
            return buffer[pos++] & 0xff;
        }

        int read() throws IOException {
            int first = readByte();
            if (first < 128) return first;
            int remaining, value, minimum;
            if (first >= 0xc2 && first <= 0xdf) {
                remaining = 1; value = first & 0x1f; minimum = 0x80;
            } else if (first >= 0xe0 && first <= 0xef) {
                remaining = 2; value = first & 0x0f; minimum = 0x800;
            } else if (first >= 0xf0 && first <= 0xf4) {
                remaining = 3; value = first & 7; minimum = 0x10000;
            } else {
                return -1;
            }
            for (int i = 0; i < remaining; i++) {
                int next = readByte();
                if (next < 0x80 || next > 0xbf) return -1;
                value = value << 6 | next & 0x3f;
            }
            return value < minimum || value > 0x10ffff || value >= 0xd800 && value <= 0xdfff
                    || value == 0xfffe || value == 0xffff ? -1 : value;
        }

        boolean comment() throws IOException {
            int ch;
            while ((ch = read()) != -1) {
                if (ch == '-') {
                    ch = read();
                    if (ch == '-') return read() == '>';
                    if (ch == -1) return false;
                }
                if (ch < 32 && !whitespace(ch)) {
                    return false;
                }
            }
            return false;
        }

        String instruction(int ch) throws IOException {
            StringBuilder body = new StringBuilder();
            while (ch != -1) {
                if (ch == '?') {
                    ch = read();
                    if (ch == '>') return body.toString();
                    body.append('?');
                    continue;
                }
                if (ch < 32 && !whitespace(ch)) return null;
                body.appendCodePoint(ch);
                ch = read();
            }
            return null;
        }
    }
}
