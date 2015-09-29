package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExamplePySp {
    
    /**
     * Generate an example script file.
     */
    public static String generateExamplePage() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        // embed version info
        pw.format("<%%\r\n# Nodel auto-generated example PySp (server-side script) that applies to version v%s or later.%n%%>", Launch.VERSION);
        pw.println("<html>");
        pw.println("<head >");
        pw.println("</head>");
        pw.println("<body>");
        pw.println("<p>Raw node name is <%= _node.getName() %>.");
        pw.println();
        pw.println("<p>HTML friendly node description is <strong><%# _node.getDesc() %></strong>.");
        pw.println();
        pw.println("<p>ipAddress parameter is <%# param_ipAddress %>.");
        pw.println();
        pw.println("<h1>Useful Python variables</h1>");
        pw.println();
        pw.println("<p><code>_ctx.req()</code> attributes:");
        pw.println("<ul>");
        pw.println("<%");
        pw.println("for x in dir(_ctx.req()):");
        pw.println("   if '__' not in x: %><li><%# x %></li>");
        pw.println("<%");
        pw.println("%>");
        pw.println("</ul>");
        pw.println();
        pw.println("<p><code>_ctx.resp()</code> attributes:");
        pw.println("<ul>");
        pw.println("<%");
        pw.println("for x in dir(_ctx.resp()):");
        pw.println("   if '__' not in x: %><li><%# x %></li>");
        pw.println("<%");
        pw.println("%>");
        pw.println("</ul>");
        pw.println("<p><code>locals</code> attributes:");
        pw.println("<ul>");
        pw.println("<%");
        pw.println("for x in dir(locals):");
        pw.println("   if '__' not in x: %><li><%# x %></li>");
        pw.println("<%");
        pw.println("%>");
        pw.println("</ul>");        
        pw.println();
        pw.println("<p><code>_node</code> attributes:");
        pw.println("<ul>");
        pw.println("<%");
        pw.println("for x in dir(_node):");
        pw.println("   if '__' not in x: %><li><%# x %></li>");
        pw.println("<%");
        pw.println("%>");
        pw.println("</ul>");
        pw.println();
        pw.println("<p><code>_pysp</code> is <a href='?_dump'>(can use '?_dump' querystring)</a>:<br/>");
        pw.println("<pre><%# _pysp %></pre>");
        pw.println("</body>");
        pw.println("</html>");
        
        return sw.toString();
    } // (method)

}
