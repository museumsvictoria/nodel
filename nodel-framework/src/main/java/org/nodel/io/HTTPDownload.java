package org.nodel.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class HTTPDownload {
    /**
     * Downloads a file from a given URL to a folder, using tmp files to ensure integrity.
     */
    public static void downloadFile(String[] fileURL, File folder) {
        try {
            // successfully avoided regex
            URL URL = new URL(fileURL[0] + fileURL[1]);
            String fileName = fileURL[1];
            ReadableByteChannel readChannel = Channels.newChannel(URL.openStream());
            // to ensure integrity, use a temporary file (renaming it after success)

            File tmpFile = new File(folder, fileName + ".download");
            // make sure all directories are created
            tmpFile.getParentFile().mkdirs();
            tmpFile.createNewFile();

            File finalOutFile = new File(folder, fileName);
            FileOutputStream fileStream = new FileOutputStream(tmpFile);
            
            FileChannel writeChannel = fileStream.getChannel();
            writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
            if (finalOutFile.exists()) {
                if (!finalOutFile.delete())
                    fileStream.close();
                    readChannel.close();
                    writeChannel.close();
                    throw new IOException("Could not delete destination file.");
                }
                
            fileStream.close();
            readChannel.close();
            writeChannel.close();
                // rename the temp file now
            if (!tmpFile.renameTo(finalOutFile)){
                throw new IOException("Could not rename temporary.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
