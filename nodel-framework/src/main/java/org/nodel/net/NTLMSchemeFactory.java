package org.nodel.net;

import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.params.HttpParams;

/**
 * Part of NTLM support.
 */
public class NTLMSchemeFactory implements AuthSchemeFactory {

    public AuthScheme newInstance(HttpParams params) {
        return new NTLMScheme(new JCIFSEngine());
    }

    /**
     * (for lazy singleton)
     */
    private static class Instance {
        private static final NTLMSchemeFactory INSTANCE = new NTLMSchemeFactory();
    }

    /**
     * (singleton)
     */
    public static NTLMSchemeFactory instance() {
        return Instance.INSTANCE;
    }

}
