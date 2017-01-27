package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.Reader;

import org.nodel.Formatting;

/**
 * Loosely reads JSON objects from a character stream, respecting white-space, quotes and escape-characters.
 * 
 * Rapidly parses a JSON stream without building up any object model. 
 * 
 * Notes:
 * - stream consists of:
 *   [WHITESPACE]'{'[STREAM]'}'
 *   
 * FUTURE IMPROVEMENTS:
 * - support for comments within the stream: e.g. '// blah blah'
 * - allow adjustment of size
 */
public class JSONStreamReader {
    
    /**
     * 10 MB size limit applies to (will vary platform to platform) 
     */
    private static int _sizeLimit = 10 * 1024 * 1024;
    
    /**
     * Initial capacity of the string builder. 
     */
    private static int START_CAPACITY = 256;
    
    /**
     * The base reader.
     */
    private Reader _reader;
    
    /**
     * Constructs a new reader that
     * @param reader
     */
    public JSONStreamReader(Reader reader) {
        if (reader == null)
            throw new IllegalArgumentException("Reader cannot be null.");
        
        _reader = reader;
    } // (constructor)
    
    /**
     * Blocks until a complete JSON message can be read i.e. opening brace '{' to closing brace. '}'
     * or null if end of stream occurs.
     * (not thread safe)
     * @throws IOException 
     */
    public String readJSONMessage() throws IOException {
        StringBuilder sb = new StringBuilder(START_CAPACITY);
        
        boolean gotOpening = false;
        boolean inQuotes = false;
        boolean escaping = false;
        
        int level = 0; // the nest level
        
        while (true) {
            int rawChar = _reader.read();
            if (rawChar < 0)
                return null;

            char c = (char) rawChar;
            
            if (!gotOpening) {
                // yet to get '{'
                
                if (Character.isWhitespace(c)) {
                    // white-space is acceptable

                } else if (c == '{') {
                    // got opening brace
                    appendChar(sb, c);

                    gotOpening = true;
                    level++;

                } else {
                    // otherwise unexpected character means corrupt stream
                    throw new IOException("Unexpected character before opening brace, '{'.");
                }
                
            } else {
                // we're passed the opening brace
                appendChar(sb, c);
                
                if (escaping) {
                    // just add the next character regardless
                    escaping = false;
                    
                } else {
                    // neither escaping nor in quotes,
                    // just a normal character
                    
                    if (c == '\\') {
                        // escape character to follow
                        escaping = true;
                        
                    } else if (c == '\"') {
                        // opening or closing quotes
                        if (inQuotes)
                            inQuotes = false;
                        else
                            inQuotes = true;
                        
                    } else if (c == '{') {
                        // got opening brace but we might be in quotes
                        if (inQuotes)
                            continue;

                        // raise nesting level
                        level++;
                        
                    } else if (c == '}') {
                        // got closing brace but we might be in quotes
                        if (inQuotes)
                            continue;
                        
                        level--;
                        
                        // check for closing outer brace
                        if(level == 0)
                            break;
                    }
                }
            }
        } // (while)
        
        // return the contents
        return sb.toString();
    } // (method)
    
    /**
     * Closes down the underlying stream.
     */
    public void close() throws IOException {
        _reader.close();
    }

    /**
     * Safely appends a char to the string builder checking for size limits.
     */
    private static void appendChar(StringBuilder sb, char c) throws IOException {
        if (sb.length() > _sizeLimit)
            throw new IOException("Message is longer than currently allowed - " + Formatting.formatByteLength(_sizeLimit));

        sb.append(c);
    } // (method)
    
    /**
     * Sets the maximum size of allowable JSON messages.
     */
    public static void setSizeLimit(int length) {
        _sizeLimit = length;
    }
    
    /**
     * Returns the maximum size of allowable JSON messages.
     * @return
     */
    public static int getSizeLimit() {
        return _sizeLimit;
    }
    
} // (class)