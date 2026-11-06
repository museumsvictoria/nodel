# Legacy wrapper checks

Run these checks from `nodel-webui-js` with Node.js 24 (jsdom also supports
Node.js 20.19+ or 22.13+) and a JDK with `jshell` on `PATH`:

```sh
npm ci --legacy-peer-deps --ignore-scripts
npm run test:legacy-wrapper
npm run compare:markup
```

The regression tests cover current-URL rendering, raw XML fetches, query
handling, empty action/visibility values, and descendant text content. The host's `LegacyXmlServingTests` cover
HTTP semantics, real-browser navigation and live action/event bindings.

The markup comparison renders seven built-in XML pages and two compatibility
fixtures with both the Java XSLT engine and the JavaScript wrapper. It compares
normalized HTML without executing the legacy runtime. The Java XSLT engine is
only a test oracle; the wrapper does not execute XSLT in the browser.

Ship the host and frontend together: this loader requires the matching host.
Recognised legacy UI `.xml` URLs return HTTP 200 HTML at the original address,
without redirects or click interception. The loader fetches that XML with
`_raw=1`, retaining other query parameters; the address and fragment stay intact.
External XML consumers must add `?_raw=1` (or `&_raw=1` to an existing query),
or use the existing `_source` endpoint. Raw serving retains its normal bytes,
conditional requests and existing range/error handling. The lightweight
`index.htm` selects node `index.xml` (falling back to `nodel.xml`) or host
`locals.xml`, then replaces the bootstrap URL while preserving other query
parameters and fragments. Explicit `index.htm?xml=filename.xml` selection
remains supported and removes only the selector parameter.

Detection scans at most 16 KiB of the prolog, supporting ASCII/UTF-8 with an
optional UTF-8 BOM and the exact `xml-stylesheet` PI with `type="text/xsl"` and
`href="v1/index.xsl"` or `href="./v1/index.xsl"`, not alternate stylesheets.
Unsupported encodings, DTDs before the stylesheet, malformed or over-budget
prologs and different stylesheet paths fall back to ordinary serving and may
still require native browser XSLT. Detection is not document validation.
General nested-directory asset/REST rebasing and arbitrary XSLT are not supported.
