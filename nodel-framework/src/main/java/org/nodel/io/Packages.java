package org.nodel.io;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Packages {
    
    /**
     * Downloads a package into a folder. Returns the "File" if
     * a *new* download has occurred.
     */
    public static File downloadPackage(String packageURL, File folder, Map<String, String> properties) {
        logger.info("Checking package - '" + packageURL + "'...");

        try {
            String url0;
            
            if (properties != null && properties.size() > 0) {
                StringBuilder sb = new StringBuilder(packageURL);
                sb.append("?");
                boolean first = true;
                for (String key : properties.keySet()) {
                    if (first)
                        first = false;
                    else
                        sb.append("&");
                    
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode(properties.get(key), "UTF-8"));
                } // (for)
                
                url0 = sb.toString();
            } else {
                url0 = packageURL;
            }
            
            URL url = new URL(url0);
            String path = url.getPath();
            String fileName = new File(path).getName();
            
            File finalOutFile = new File(folder, fileName);
            
            // using 'etag' mechanism for cache check
            String etag = null;
            
            // check for an 'etag'
            File etagFile = new File(folder, fileName + ".etag");
            if (etagFile.exists())
                etag = Stream.readFully(etagFile);

            long lastModified = 0;
            if (finalOutFile.exists())
                lastModified = finalOutFile.lastModified();
            
            // to ensure integrity, use a temporary file (renaming it after success)
            File tmpFile = new File(folder, fileName + ".download");
            if (tmpFile.exists()) {
                // delete the temporary file
                if (!tmpFile.delete())
                    throw new IOException("Could not delete temporary file '" + tmpFile + "'");
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // check for 'etag' vs 'last-modified'
            if (etag != null) {
                conn.setRequestProperty("If-None-Match", etag);
            } else if (lastModified != 0) {
                // use 'last-modified' header
                conn.setIfModifiedSince(lastModified);
            }

            // do the response
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // download it
                lastModified = conn.getLastModified();
                long size = conn.getContentLength();
                
                etag = conn.getHeaderField("etag");
                
                logger.info("Package available. size:" + size);

                FileOutputStream fos = null;
                InputStream is = null;

                long totalBytesRead = 0;

                try {
                    fos = new FileOutputStream(tmpFile);
                    is = conn.getInputStream();

                    byte[] buffer = new byte[1024 * 10];
                    for (;;) {
                        int bytesRead = is.read(buffer);
                        if (bytesRead <= 0)
                            break;

                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                } finally {
                    if (is != null)
                        is.close();
                    if (fos != null)
                        fos.close();
                }

                if (lastModified > 0) {
                    // use 'last-modified' ideally
                    tmpFile.setLastModified(lastModified);
                } else if (etag != null) {
                    // use 'etag' as fall back
                    Stream.writeFully(etagFile, etag);
                }
                
                long fileLength = tmpFile.length();
                
                if (totalBytesRead != fileLength)
                    throw new IOException("Size mismatch - expected " + size + " bytes, but downloaded " + fileLength + " bytes");
                
                // delete original file (not normal, but under unusual circumstances can occur)
                // and continue regardlessly
                if (finalOutFile.exists()) {
                    if (!finalOutFile.delete())
                        throw new IOException("Could not delete destination file.");
                }
                
                // rename the temp file now
                if (!tmpFile.renameTo(finalOutFile))
                    throw new IOException("Could not rename temporary.");
                
                return finalOutFile;
                
            } else if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                logger.info("Package not changed, no need to download.");
                
                return null;
                
            } else {
                throw new IOException("The server did not allow the download; returned " + responseCode);
                
            }

        } catch (Exception exc) {
            logger.warn("Package download failed.", exc);
            
            return null;
        }
    } // (method)
    
    /**
     * Unpacks a zip file into a folder. 
     */
    public static boolean unpackZip(File zipFile, File root) {
        try {
            return unpackZip(new FileInputStream(zipFile), root);
        } catch (IOException exc) {
            logger.warn("Unzip operation failed.", exc);
            
            return false;
        }
    }

    /**
     * Unpacks an input stream (assumed zipped) into a folder. 
     */
    public static boolean unpackZip(InputStream is, File root) {
        byte[] buffer = new byte[10 * 1024];
        
        ZipInputStream zis = null;
        
        try {
            zis = new ZipInputStream(new BufferedInputStream(is));

            ZipEntry ze;

            int count;

            while ((ze = zis.getNextEntry()) != null) {
                File outFile = new File(root, ze.getName());

                if (ze.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs())
                        throw new IOException("Could not create a directory to unzip files.");
                    
                } else {
                    // is not a directory, but still might not have a directory created
                    // (directory ZIP utilities order differently)
                    if (!outFile.getParentFile().exists())
                        if(!outFile.getParentFile().mkdirs())
                            throw new IOException("Could not create directory to unzip files.");
                    
                    FileOutputStream fout = null;
                    try {
                        fout = new FileOutputStream(outFile);

                        while ((count = zis.read(buffer)) != -1) {
                            fout.write(buffer, 0, count);
                        }
                        
                    } finally {
                        if (fout != null)
                            fout.close();
                    }
                    
                    // update the last modified time to make things sensible
                    // when browsing files directly
                    outFile.setLastModified(ze.getTime());
                }
                
                zis.closeEntry();
            } // (while)
            
            return true;
        } catch (Exception exc) {
            logger.warn("Unzip operation failed.", exc);
            return false;
            
        } finally {
            if (zis != null) {
                try { zis.close(); } catch (Exception exc) { }
            }
        }
    } // (method)
    
    private static Logger logger = LoggerFactory.getLogger(Packages.class.getName());

} // (class)
