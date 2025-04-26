package org.nodel.http;

import org.nodel.http.impl.Apache5NodelHttpClient;
import org.nodel.net.NodelHTTPClient;
import org.nodel.net.NodelHttpClientProvider;

/**
 * Provides HTTP clients based on the Apache HTTP client 5
 */
public class StaticNodelHttpClientProvider extends NodelHttpClientProvider {

    @Override
    public NodelHTTPClient create() {
        return new Apache5NodelHttpClient();
    }
    
}
