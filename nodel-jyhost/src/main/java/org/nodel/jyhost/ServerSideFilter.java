package org.nodel.jyhost;

// converts:
//
// <% import datetime %>
// <html>
// <head></head>
// <body>
// Hello there! The time is <% datetime.datetime.now().isoformat() %>.
// </body>
// </html>
//
//
// into:
//
// py.eval("import datetime");
// response.write("<html>\r\n<head></head>\r\n<body>\r\nHello there! The time is ");
// response.write(py.eval("datetime.datetime.now().isoformat()");
// response.write("</body></html>");

public abstract class ServerSideFilter {

    private static final int STATE_NORMAL = 0;

    private static final int STATE_FOUND = 1;
    
    protected String template;

    public ServerSideFilter(String template) {
        this.template = template;
    }
    
    public abstract void resolveExpression(String expr) throws Throwable;
    
    public abstract void evaluateBlock(String block) throws Throwable;
    
    public abstract void passThrough(String data) throws Throwable;
    
    public abstract void comment(String comment) throws Throwable;
    
    public abstract void resolveEscapedExpression(String expr) throws Throwable;
    
    public abstract void handleError(Throwable th);

    /**
     * Processes JSP-like tags.  e.g. <% =ipAddress %>
     */
    public void process() {
        StringBuilder output = new StringBuilder();

        int state = STATE_NORMAL;

        StringBuilder sb = new StringBuilder();

        char previousChar = 0;

        for (int a = 0; a < this.template.length(); a++) {
            char c = this.template.charAt(a);

            switch (state) {
            case STATE_NORMAL:
                if (c == '%' && previousChar == '<') {
                    state = STATE_FOUND;

                    // drop the previous character
                    output.setLength(output.length() - 1);
                } else {
                    output.append(c);
                }
                break;

            case STATE_FOUND:
                if (c == '>' && previousChar == '%') {
                    // pass through what we've got and reset the output
                    try {
                        passThrough(escape(output.toString()));
                        
                    } catch (Throwable th) {
                        handleError(th);
                    }
                    output.setLength(0);
                    
                    // adjust it by one and then resolve
                    sb.setLength(sb.length() - 1);
                    try {
                        evaluate(sb.toString());
                        
                    } catch (Throwable th) {
                        handleError(th);
                    }

                    sb.setLength(0);

                    state = STATE_NORMAL;
                } else {
                    sb.append(c);
                }
                break;
            }

            previousChar = c;
        } // (while)
        
        if (output.length() > 0) {
            try {
                passThrough(escape(output.toString()));
                
            } catch (Throwable th) {
                handleError(th);
            }
        }
    } // (method)

    /**
     * Resolves an object model using a REST-like path.
     */
    private void evaluate(String fullValue) throws Throwable {
        boolean isExpression = fullValue.startsWith("=");
        
        boolean isComment = fullValue.startsWith("--");
        
        boolean isEscapedExpression = fullValue.startsWith("!");
        
        if (fullValue.length() <= 1) {
            return;
            
        } else if (isExpression) {
            String value = fullValue.substring(1).trim();
            resolveExpression(value);

        } else if (isEscapedExpression) {
            String value = fullValue.substring(1).trim();
            resolveEscapedExpression(value);

        } else if (isComment) {
            comment(fullValue);

        } else {
            evaluateBlock(fullValue);
        }

    } // (method)

    /**
     * Prepares a string for use within typical scripting languages 
     */
    public static String escape(String raw) {
        StringBuilder sb = new StringBuilder();
        int len = raw.length();
        
        for (int a = 0; a < len; a++) {
            char c = raw.charAt(a);
            
            if (c == '\t')
                sb.append("\\t");
            
            else if (c == '\n')
                sb.append("\\n");
            
            else if (c == '\r')
                sb.append("\\r");
            
            else if (c == '\'')
                sb.append("\\'");
            
            else if (c == '\"')
                sb.append("\\\"");
            
            else if (c == '\\')
                sb.append("\\\\");
           
            else
                sb.append(c);
        }
        
        return sb.toString();
    }


} // (class)
