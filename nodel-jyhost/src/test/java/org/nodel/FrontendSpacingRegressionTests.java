package org.nodel;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression coverage for issue #395.
 * Ensures grouped controls keep the same vertical rhythm as ungrouped controls.
 */
public class FrontendSpacingRegressionTests extends TestBase {

    private static final String TEST_NODE = "Frontend Spacing Regression";

    private static final String TEST_SCRIPT =
        "def main():\n" +
        "    console.info('Frontend spacing regression started')\n";

    private static final String INDEX_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<?xml-stylesheet type=\"text/xsl\" href=\"v1/index.xsl\"?>\n" +
        "<pages title='Frontend Spacing Regression' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:noNamespaceSchemaLocation='v1/index.xsd'>\n" +
        "  <page title='Repro'>\n" +
        "    <row>\n" +
        "      <column sm='4'>\n" +
        "        <title>Grouped</title>\n" +
        "        <group>\n" +
        "          <subtitle>Speakers</subtitle>\n" +
        "          <button join='DSP Speaker1-On' arg-on='true' arg-off='false'>Speaker 1</button>\n" +
        "          <range join='DSP Speaker1-Fader' min='-50' max='0'/>\n" +
        "        </group>\n" +
        "      </column>\n" +
        "      <column sm='4'>\n" +
        "        <title>Ungrouped</title>\n" +
        "        <subtitle>Speakers</subtitle>\n" +
        "        <button join='DSP Speaker1-On' arg-on='true' arg-off='false'>Speaker 1</button>\n" +
        "        <range join='DSP Speaker1-Fader' min='-50' max='0'/>\n" +
        "      </column>\n" +
        "    </row>\n" +
        "  </page>\n" +
        "</pages>\n";

    @BeforeAll
    public static void setup() {
        initBrowser();

        boolean created = createTestNode(TEST_NODE, TEST_SCRIPT);
        assumeTrue(created, "Test node must be created and discovered for spacing regression tests");

        APIResponse response = uploadNodeFile(TEST_NODE, "content/index.xml", INDEX_XML);

        assertEquals(200, response.status(), "content/index.xml upload should return 200");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGroupedControlsMatchUngroupedSpacing() throws Exception {
        String reducedName = getReducedName(TEST_NODE);
        String nodeBaseUrl = BASE_URL + "/nodes/" + reducedName + "/";

        APIResponse xmlResponse = page.request().get(nodeBaseUrl + "index.xml");
        assertEquals(200, xmlResponse.status(), "index.xml should be fetchable");

        APIResponse xslResponse = page.request().get(nodeBaseUrl + "v1/index.xsl");
        assertEquals(200, xslResponse.status(), "v1/index.xsl should be fetchable");

        String transformedHtml = transformXmlToHtml(xmlResponse.text(), xslResponse.text(), nodeBaseUrl);
        assertTrue(transformedHtml.contains("class=\"well"),
            "Transformed output should include the grouped well container");

        page.setContent(absolutizeAssetUrls(transformedHtml, nodeBaseUrl));
        waitForElement(".well");

        Map<String, Object> metrics = (Map<String, Object>) page.evaluate(
            "() => {\n" +
            "  const colByTitle = (title) => {\n" +
            "    const heading = [...document.querySelectorAll('h4')].find(h => h.textContent.trim() === title);\n" +
            "    return heading ? heading.parentElement : null;\n" +
            "  };\n" +
            "  const groupedCol = colByTitle('Grouped');\n" +
            "  const ungroupedCol = colByTitle('Ungrouped');\n" +
            "  if (!groupedCol || !ungroupedCol) return null;\n" +
            "\n" +
            "  const groupedButton = groupedCol.querySelector('a.btn');\n" +
            "  const ungroupedButton = ungroupedCol.querySelector('a.btn');\n" +
            "  const groupedRange = groupedCol.querySelector('.range');\n" +
            "  const ungroupedRange = ungroupedCol.querySelector('.range');\n" +
            "  const groupedWell = groupedCol.querySelector('.well');\n" +
            "\n" +
            "  if (!groupedButton || !ungroupedButton || !groupedRange || !ungroupedRange || !groupedWell) return null;\n" +
            "\n" +
            "  const groupedButtonMb = parseFloat(window.getComputedStyle(groupedButton).marginBottom);\n" +
            "  const ungroupedButtonMb = parseFloat(window.getComputedStyle(ungroupedButton).marginBottom);\n" +
            "\n" +
            "  const groupedGap = groupedRange.getBoundingClientRect().top - groupedButton.getBoundingClientRect().bottom;\n" +
            "  const ungroupedGap = ungroupedRange.getBoundingClientRect().top - ungroupedButton.getBoundingClientRect().bottom;\n" +
            "\n" +
            "  return {\n" +
            "    groupedButtonMarginBottom: groupedButtonMb,\n" +
            "    ungroupedButtonMarginBottom: ungroupedButtonMb,\n" +
            "    groupedButtonToRangeGap: groupedGap,\n" +
            "    ungroupedButtonToRangeGap: ungroupedGap,\n" +
            "    groupHasWell: groupedWell.classList.contains('well')\n" +
            "  };\n" +
            "}");

        assumeTrue(metrics != null, "Grouped and ungrouped controls must be rendered before assertions");

        double groupedButtonMarginBottom = ((Number) metrics.get("groupedButtonMarginBottom")).doubleValue();
        double ungroupedButtonMarginBottom = ((Number) metrics.get("ungroupedButtonMarginBottom")).doubleValue();
        double groupedGap = ((Number) metrics.get("groupedButtonToRangeGap")).doubleValue();
        double ungroupedGap = ((Number) metrics.get("ungroupedButtonToRangeGap")).doubleValue();

        assertTrue(groupedButtonMarginBottom > 0.1,
            "Expected non-zero button margin to confirm stylesheet applied");
        assertTrue((Boolean) metrics.get("groupHasWell"), "Grouped container should preserve the 'well' class");
        assertEquals(ungroupedButtonMarginBottom, groupedButtonMarginBottom, 0.1,
            "Grouped button margin-bottom should match ungrouped button margin-bottom");
        assertEquals(ungroupedGap, groupedGap, 1.0,
            "Grouped button-to-range gap should match ungrouped gap");
    }

    private static String transformXmlToHtml(String xml, String xsl, String nodeBaseUrl) throws Exception {
        StreamSource xslSource = new StreamSource(new StringReader(xsl));
        xslSource.setSystemId(nodeBaseUrl + "v1/index.xsl");

        StreamSource xmlSource = new StreamSource(new StringReader(xml));
        xmlSource.setSystemId(nodeBaseUrl + "index.xml");

        Transformer transformer = TransformerFactory.newInstance().newTransformer(xslSource);
        StringWriter output = new StringWriter();
        transformer.transform(xmlSource, new StreamResult(output));
        return output.toString();
    }

    private static String absolutizeAssetUrls(String html, String nodeBaseUrl) {
        return html
            .replace("href=\"v1/", "href=\"" + nodeBaseUrl + "v1/")
            .replace("src=\"v1/", "src=\"" + nodeBaseUrl + "v1/")
            .replace("href='v1/", "href='" + nodeBaseUrl + "v1/")
            .replace("src='v1/", "src='" + nodeBaseUrl + "v1/");
    }
}
