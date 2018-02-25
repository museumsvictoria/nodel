package org.nodel.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.nodel.SimpleName;

/**
 * For message-based HTTP response (non-streams). 
 * Headers are stored as strings or list of strings.
 */
@SuppressWarnings("serial")
public class HTTPSimpleResponse extends HashMap<SimpleName, List<String>> {
    
    /**
     * The HTTP status code part e.g. 200
     */
    public int statusCode;
    
    /**
     * The HTTP reason phrase, e.g. 'OK'
     */
    public String reasonPhrase;
    
    /**
     * The content.
     */
    public String content;
    
    /**
     * Adds a header, allowing for multiple values for the same name
     */
    public void addHeader(Object headerName, String value) {
        SimpleName simpleName = SimpleName.intoSimple(headerName);
        
        List<String> list = this.get(simpleName);
        if (list == null) {
            list = new ArrayList<String>();
            this.put(simpleName, list);
        }
        
        list.add(value);
    }
    
    /**
     * ('get' and 'getAll' do the same)
     */
    @Override
    public List<String> get(Object headerName) {
        return super.get(SimpleName.intoSimple(headerName));
    }
    
    /**
     * Returns the first value of a given header
     */
    public String getFirstHeader(Object headerName) {
        List<String> result = this.get(headerName);
        
        if (result == null)
            return null;
        
        return result.get(0); 
    }
    
    /**
     * Returns the last value of a given header
     */
    public String getLastHeader(Object headerName) {
        List<String> result = this.get(headerName);
        
        if (result == null)
            return null;
        
        return result.get(result.size() - 1);
    } 
    
    @Override
    public String toString() {
        return String.format("[statusCode:%s; reasonPhrase:[%s]; %s header%s; %s]",
                statusCode, reasonPhrase, this.size(), this.size() != 1 ? "s" : "",
                content.length() == 0 ? "blank content" : "content length " + content.length());
    }
    
}