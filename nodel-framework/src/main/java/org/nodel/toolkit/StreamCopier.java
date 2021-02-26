/*
 * Imported from SSHJ project and simplified.
 * https://github.com/hierynomus/sshj
 */

package org.nodel.toolkit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class StreamCopier {

    public interface Listener {

        void reportProgress(long transferred)
                throws IOException;
    }

    private static final Listener NULL_LISTENER = new Listener() {
        @Override
        public void reportProgress(long transferred) {
        }
    };

    private static AtomicLong instanceCounter = new AtomicLong();

    private long instance = instanceCounter.getAndIncrement();

    private final Logger log;
    private final InputStream in;
    private final OutputStream out;

    private Listener listener = NULL_LISTENER;

    private int bufSize = 1;
    private boolean keepFlushing = true;
    private long length = -1;

    public StreamCopier(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
        this.log = LoggerFactory.getLogger(String.format("%s.instance%d", this.getClass().getName(), instance));
    }

    public StreamCopier bufSize(int bufSize) {
        this.bufSize = bufSize;
        return this;
    }

    public StreamCopier keepFlushing(boolean keepFlushing) {
        this.keepFlushing = keepFlushing;
        return this;
    }

    public StreamCopier listener(Listener listener) {
        if (listener == null) {
            this.listener = NULL_LISTENER;
        } else {
            this.listener = listener;
        }
        return this;
    }

    public StreamCopier length(long length) {
        this.length = length;
        return this;
    }

    public void spawn(final String name) {
        new Thread() {
            {
                setName(name);
                setDaemon(false);
            }

            @Override
            public void run() {
                try {
                    log.debug("Will copy from {} to {}", in, out);
                    copy();
                    log.debug("Done copying from {}", in);
                } catch (IOException ioe) {
                    log.error(String.format("In pipe from %1$s to %2$s", in.toString(), out.toString()), ioe);
                }
            }
        }.start();
    }

    public long copy()
            throws IOException {
        final byte[] buf = new byte[bufSize];
        long count = 0;
        int read = 0;

        final long startTime = System.nanoTime();

        if (length == -1) {
            while ((read = in.read(buf)) != -1) {
                count += write(buf, count, read);
            }
        } else {
            while (count < length && (read = in.read(buf, 0, (int) Math.min(bufSize, length - count))) != -1) {
                count += write(buf, count, read);
            }
        }

        if (!keepFlushing)
            out.flush();

        final double timeSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) / 1000.0;
        final double sizeKiB = count / 1024.0;
        log.debug(String.format("%1$,.1f KiB transferred in %2$,.1f seconds (%3$,.2f KiB/s)", sizeKiB, timeSeconds, (sizeKiB / timeSeconds)));

        if (length != -1 && read == -1)
            throw new IOException("Encountered EOF, could not transfer " + length + " bytes");

        return count;
    }

    private long write(byte[] buf, long curPos, int len)
            throws IOException {
        out.write(buf, 0, len);
        if (keepFlushing)
            out.flush();
        listener.reportProgress(curPos + len);
        return len;
    }
}
