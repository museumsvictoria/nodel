package org.nodel.io;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Random;

public class Files {

    /**
     * Used for temporary file operations
     */
    private static AtomicLong s_sequenceCounter = new AtomicLong(0);

    /**
     * Creates File ready for temporary existence (no file is created).
     * 
     * (Java does have its own built-in functions for temporary file but not all have JVM 1.7 coverage)
     */
    public static File getTmpFile(File basedOn) {
        return new File(basedOn.getParentFile(), "_tmp_nodel_" + Random.shared().nextInt(1000000) + "_" + s_sequenceCounter.getAndIncrement() + ".tmp");
    }

    /**
     * Best effort to recursively flush (remove / delete) all files within a directory. 'alsoDelete' also
     * delete the folder after the flush.
     * WARNING - destructive operation.
     * 
     * @return the number of items (files or directories) deleted (regardless of success of failure)
     */
    public static long tryFlushDir(File directory, boolean alsoDelete) {
        long itemsDeleted = 0;

        String[] fileList = directory.list();

        for (int a = 0; a < fileList.length; a++) {
            String fileName = fileList[a];

            File file = new File(directory, fileName);

            if (!file.exists()) {
                continue;

            } else if (file.isFile()) {
                // delete single file
                if (file.delete())
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
     * (see 'tryFlushDir')
     */
    public static long tryFlushDir(File directory) {
        return tryFlushDir(directory, false);
    }

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

            fileTmp = getTmpFile(dst);

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

            // re-timestamp (best effort; not critical if timestamp can not be copied)
            dst.setLastModified(src.lastModified());

            // rename the temporary file
            if (!fileTmp.renameTo(dst))
                throw new RuntimeException("Could not rename temporary file after file copy operation");

        } catch (Exception exc) {
            throw new RuntimeException("File copy failed.", exc);

        } finally {
            // clean up best we can

            if (fisTmp != null)
                fileTmp.delete();

            Stream.safeClose(fis1, fisTmp);

        }

    } // (method)

    /**
     * Recursively copies a directory. Performs in an atomic way using a temporary folder. Will completely succeed or cleanup and fail.
     */
    public static void copyDir(File src, File dst) {
        if (!src.exists() || !src.isDirectory())
            throw new RuntimeException("Source directory not found or is not a directory - " + src.getName());

        // cannot copy into existing folders
        if (dst.exists())
            throw new RuntimeException("Destination already exists - " + dst.getName());

        // used temporary file to perform atomic operation instead of exposing partially created directory
        File dstTmp = null;

        try {
            dstTmp = getTmpFile(dst);
            dstTmp.mkdirs();

            for (File item : src.listFiles()) {
                if (item.isFile())
                    copy(item, new File(dstTmp, item.getName()));

                else if (item.isDirectory())
                    copyDir(item, new File(dstTmp, item.getName()));

                // else neither a file nor folder
                // (should never be possible; continue regardless)
            }

            // re-timestamp (best effort; not critical if timestamp can not be copied)
            dst.setLastModified(src.lastModified());

            // rename the temporary file
            dstTmp.renameTo(dst);

        } catch (Exception exc) {
            // something went wrong, clean up and delete the temporary folder
            if (dstTmp != null && dstTmp.exists())
                tryFlushDir(dstTmp, true);

            // throw original exception
            throw exc;
        }
    } // (method)

} // (class)
