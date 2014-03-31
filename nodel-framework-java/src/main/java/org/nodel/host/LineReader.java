package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.Writer;

import org.nodel.Handler;

/**
 * Converts a stream into line events.
 */
public class LineReader extends Writer {
    
    /**
     * (instance lock)
     */
    private Object _lock = new Object(); 
    
    /**
     * The character buffer.
     */
    private StringBuilder _buffer = new StringBuilder();
    
    /**
     * The line handler.
     */
    private Handler.H1<String> _handler;
    
    /**
     * Set the line handler.
     */
    public void setHandler(Handler.H1<String> handler) {
        _handler = handler;
    }
    
    /**
     * Manually injects a line into this 'stream'
     */
    public void inject(String line) {
        _handler.handle(line);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        synchronized (_lock) {
            for (int a=0; a<len; a++) {
                char c = cbuf[a+off];
                if (c == '\r' || c == '\n') {
                    if (_buffer.length() > 0)
                        _handler.handle(_buffer.toString());

                    _buffer.setLength(0);
                } else {
                    _buffer.append(c);
                }
            } // (for)
        }
    } // (method)

    @Override
    public void flush() throws IOException {
        // safe to ignore
    } // (method)

    @Override
    public void close() throws IOException {
        synchronized (_lock) {
            if (_buffer.length() > 0) {
                _handler.handle(_buffer.toString());
                _buffer.setLength(0);
            }
        }
    } // (method)
    
} // (class)