package org.nodel.io;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.joda.time.DateTime;

public class Files {
    
    /**
     * Best effort to recursively flush (remove / delete) all files within a directory.
     * WARNING - destructive operation.
     * @return the number of items (files or directories) deleted (regardless of success of failure)
     */
    public static long tryFlushDir(File directory) {
        long itemsDeleted = 0;
        
        String[] fileList = directory.list();
        
        for (int a=0; a<fileList.length; a++) {
            String fileName = fileList[a];
            
            File file = new File(directory, fileName);
            
            if (!file.exists()) {
                continue;
            
            } else if (file.isFile()) {
                // delete single file
                if(file.delete())
                    itemsDeleted++;
            
            } else if (file.isDirectory()) {
                // delete (recursively) directory
                itemsDeleted += tryFlushDir(file);
                
                // delete the directory
                file.delete();
                itemsDeleted++;
            }
        } // (for)
        
        return itemsDeleted;
    } // (method)
    
    /**
     * Safely copies one file to another (new or overwrite). No warnings given.
     * Uses a temporary file in case of premature failure.
     */
    public static void copy(File src, File dst) {
        FileInputStream fis1 = null;

        File fileTmp = null;
        FileOutputStream fisTmp = null;

        try {
            fis1 = new FileInputStream(src);

            fileTmp = new File(src.getParentFile(), "temporary_" + DateTime.now().millisOfDay() + ".tmp");
            fisTmp = new FileOutputStream(fileTmp);

            byte[] buffer = new byte[65536];

            // write out the file into a temporary one
            for (;;) {
                int bytesRead = fis1.read(buffer);
                if (bytesRead <= 0)
                    break;

                fisTmp.write(buffer, 0, bytesRead);
            } // (for)
            
            // close the temporary file
            fisTmp.close();

            // delete the destination if it exists
            if (dst.exists())
                dst.delete();

            // rename the temporary file
            fileTmp.renameTo(dst);

        } catch (Exception exc) {
            throw new RuntimeException("File copy failed.", exc);
        } finally {
            // clean up best we can
            
            if (fisTmp != null) {
                fileTmp.delete();
            }

            if (fis1 != null) {
                try {
                    fis1.close();
                } catch (IOException e) {
                    // ignore
                }
            }

            if (fisTmp != null) {
                try {
                    fisTmp.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        } // (finally)
        
    } // (method)
    
} // (class)
