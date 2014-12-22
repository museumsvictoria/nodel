package org.nodel.io;

import java.nio.charset.Charset;

/**
 * A fast lookup class for the UTF8 character set.
 */
public class UTF8Charset {

    private Charset _charset = Charset.forName("UTF8");

    private UTF8Charset() {
        _charset = Charset.forName("UTF8");
    }

    private static class LazyHolder {
        private static final UTF8Charset INSTANCE = new UTF8Charset();
    }

    public static Charset instance() {
        return LazyHolder.INSTANCE.charset();
    }

    private Charset charset() {
        return _charset;
    }

} // (class)
