package org.nodel.jyhost;

import java.io.ByteArrayInputStream;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.nodel.Strings;
import org.nodel.host.NanoHTTPD;
import org.nodel.io.Files;
import org.nodel.io.Stream;
import org.nodel.io.UnexpectedIOException;
import org.nodel.reflection.Param;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;

/**
 * A end-point to allow the management of files.
 */
public class FilesEndPoint {
    
    public static class FileInfo {

        @Value(name = "modified", order = 1)
        public DateTime modified;

        @Value(name = "path", order = 2)
        public String path;

    }

    private File _root;
    
    public FilesEndPoint(File root) {
        _root = root;
    }
    
    /**
     * Lists the files relative to this node.
     */
    @Value(name = "list", desc = "Lists all files", treatAsDefaultValue = true)
    public List<FileInfo> list() {
        List<FileInfo> result = new ArrayList<>();

        listFiles(result, "", _root);

        return result;
    }
    
    /**
     * Returns the contents of a file
     */
    @Service(name = "contents", desc = "Returns the contents of the file.")
    public NanoHTTPD.Response contents(@Param(name = "path") String path) {
        try {
            if (Strings.isBlank(path))
                throw new RuntimeException("No file path provided");
            
            FileInputStream fis = new FileInputStream(new File(_root, path));
            
            return new NanoHTTPD.Response(NanoHTTPD.HTTP_OK, null, fis);
            
        } catch (IOException exc) {
            throw new UnexpectedIOException(exc);
        }
    }
    
    @Service(name = "save", desc = "Saves the contents of a file (full success or nothing), creates a new one if it does not exist.")
    public void save(@Param(name = "path") String path, ByteArrayInputStream is) {
        if (is == null)
            throw new RuntimeException("The contents is completely missing");

        FileOutputStream fos = null;
        File tmpDst = null;
        
        try {
            File file = new File(_root, path);

            if (!file.exists()) {
                // new file, so make sure its parent directory exists first

                File parent = file.getParentFile();

                if (!parent.exists() && parent.mkdirs()) {
                    // sometimes .mkdirs "succeeds" but returns false, so double-check...
                    if (!parent.exists())
                        throw new RuntimeException("Could not create parents directories for file " + path);
                }
            }

            tmpDst = Files.getTmpFile(file);
            fos = new FileOutputStream(tmpDst);
            
            byte[] buffer = new byte[10240];
            for (;;) {
                int bytesRead = is.read(buffer);
                if (bytesRead < 0)
                    break;
                
                fos.write(buffer, 0, bytesRead);
            }
            
            Stream.safeClose(fos);
            
            // delete the existing file if is exists
            if (file.exists() && !file.delete())
                throw new RuntimeException("Could not copy over file - locking? permissions?");

            // and then overwrite it (rename)
            if (!tmpDst.renameTo(file))
                throw new RuntimeException("File operation failure");

            return;

        } catch (IOException exc) {
            throw new UnexpectedIOException(exc);
            
        } finally {
            Stream.safeClose(fos);
            
            if (tmpDst != null && tmpDst.exists())
                tmpDst.delete();
        }
    }

    @Service(name= "delete")
    public void delete(@Param(name = "path") String path) {
        File file = new File(_root, path);
        
        if (!file.exists())
            throw new RuntimeException("No such file exists to delete - " + path);
        
        if (!file.delete())
            throw new RuntimeException("The file could not be deleted for unknown reasons; locking?");
        
        // check if directory is empty and delete that too
        File parent = file.getParentFile();
        if (parent.exists() && parent.list().length == 0)
            parent.delete();
    }
    
    /**
     * (recursive helper)
     */
    private static void listFiles(List<FileInfo> result, String path, File root) {
        for (File item : root.listFiles()) {
            if (item.isHidden())
                continue;

            String name = item.getName();

            if (name.equals(".nodel"))
                continue;
            
            // further filtering could be done here

            String newPath = path.length() > 0 ? path + "/" + name : name;

            if (item.isDirectory())
                listFiles(result, newPath, item);

            else if (item.isFile()) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.modified = new DateTime(item.lastModified());
                fileInfo.path = newPath;
                
                result.add(fileInfo);
            }

            // otherwise skip
        }
    }
    
}