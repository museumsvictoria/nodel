package org.nodel.io;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class Stream {

    /**
     * Overloaded for InputStream.
     */
    public static String readFully(InputStream is) throws IOException {
        return readFully(new InputStreamReader(is));
    }
    
	/**
	 * Reads into a buffer
	 */
    public static byte[] readFullyIntoBuffer(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		int len = 10240;
		byte[] buffer = new byte[len];

		while (true) {
			int bytesRead = is.read(buffer, 0, len);
			if (bytesRead <= 0)
				break;

			baos.write(buffer, 0, bytesRead);
		}

		baos.close();

		return baos.toByteArray();
	}
    
    /**
     * Reads a specific amount of bytes into a buffer.
     */
    public static byte[] readFullyIntoBuffer(InputStream is, long length) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        long bytesLeft = length;
        
        int len = 10240;
        byte[] buffer = new byte[len];
        
        while(bytesLeft > 0) {
            int bytesRead = is.read(buffer, 0, (int) Math.min(len, bytesLeft));
            if (bytesRead <= 0)
                break;
            
            baos.write(buffer, 0, bytesRead);
            
            bytesLeft -= bytesRead;
        }
        
        baos.close();
        
        if (bytesLeft > 0)
        	throw new IOException("Stream ended before buffer was full (bytes left " + bytesLeft + ")");
        
        return baos.toByteArray();
    } 

    /**
     * Reads a stream into a String. Does not close the stream.
     */
    public static String readFully(InputStreamReader isr) throws IOException {
        char[] cbuffer = new char[1024];

        StringBuilder sb = new StringBuilder();
        for (;;) {
            int bytesRead = isr.read(cbuffer);
            if (bytesRead < 0) {
                isr.close();
                return sb.toString();
            }

            sb.append(cbuffer, 0, bytesRead);
        } // (for)

    } // (method)

    /**
     * Reads a file into a string.
     */
    public static String readFully(File file) throws IOException {
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(new FileInputStream(file), "UTF8");
            return readFully(isr);
        } finally {
            if (isr != null)
                isr.close();
        }
    } // (method)

    /**
     * Write an entire string out to a file.
     */
    public static void writeFully(File file, String str) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            writeFully(os, str);
        } finally {
            if (os != null)
                os.close();
        }
    } // (method)
    
    /**
     * (overloaded)
     */
    public static void writeFully(OutputStream os, String str) throws IOException {
        OutputStreamWriter osw = null;
        
        try {
            osw = new OutputStreamWriter(os, "UTF8");

            int bufferSize = 1024;
            int bytesLeft = str.length();
            int pos = 0;

            while (bytesLeft > 0) {
                int chunk = Math.min(bytesLeft, bufferSize);
                osw.write(str, pos, chunk);
                bytesLeft -= chunk;
                pos += chunk;
            } // (while)

        } finally {
            if (osw != null)
                osw.close();
        }
    }    

    /**
     * Closes an object without throwing any exceptions.
     */
    public static void safeClose(Closeable closable) {
        if (closable == null)
            return;
            
        try {
            closable.close();
        } catch (Exception ignore) {
            // (consume)
        }
    }
    
    /**
     * Closes all objects without ever throwing any exceptions.
     */
    public static void safeClose(Closeable... closables) {
        if (closables == null)
            return;

        for (Closeable closable : closables)
            safeClose(closable);
    }

} // (class)
