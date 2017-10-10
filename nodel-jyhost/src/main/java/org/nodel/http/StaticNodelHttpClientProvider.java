package org.nodel.http;

import org.nodel.http.impl.ApacheNodelHttpClient;
import org.nodel.net.NodelHTTPClient;
import org.nodel.net.NodelHttpClientProvider;

/**
 * Provides HTTP clients based on the Apache HTTP client
 */
public class StaticNodelHttpClientProvider extends NodelHttpClientProvider {

    @Override
    public NodelHTTPClient create() {
        return new ApacheNodelHttpClient();
    }
    
}
