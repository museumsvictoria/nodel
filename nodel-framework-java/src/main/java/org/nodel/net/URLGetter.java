package org.nodel.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.nodel.Strings;
import org.nodel.io.Stream;
import org.nodel.json.JSONObject;

public class URLGetter {
    
    // Safe URL timeouts are optimised for servers that are likely available and responsive.
    
    private final static int DEFAULT_CONNECTTIMEOUT = 10000;
    
    private final static int DEFAULT_READTIMEOUT = 15000;
    
    /**
     * A very simple URL getter. queryArgs, contentType, postData are all optional.
     * 
     * Safe timeouts are used to avoid non-responsive servers being able to hold up connections indefinitely.
     */
    public static String getURL(DefaultHttpClient httpClient, String urlStr, Map<String, String> query, 
                                String username, String password, 
                                Map<String, String> headers, String reference, String contentType, 
                                String post, 
                                Integer connectTimeout, Integer readTimeout) throws IOException {
        
        String fullURL = buildQueryString(urlStr, query);

        // (out of scope for clean up purposes)
        InputStream inputStream = null;
        // OutputStream outputStream = null;

        try {
            HttpRequestBase request;

            if (post == null)
                request = new HttpGet(fullURL);
            else
                request = new HttpPost(fullURL);

            // URLConnection urlConn = url.openConnection();

            // urlConn.setConnectTimeout(connectTimeout != null ? connectTimeout : DEFAULT_CONNECTTIMEOUT);
            // urlConn.setReadTimeout(readTimeout != null ? readTimeout : DEFAULT_READTIMEOUT);

            // set 'Content-Type' header
            if (!Strings.isNullOrEmpty(contentType))
                request.setHeader("Content-Type", contentType);

            // add (or override) any request headers
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet())
                    request.setHeader(entry.getKey(), entry.getValue());
            }
            
            if (!Strings.isNullOrEmpty(post)) {
                HttpPost httpPost = (HttpPost) request;
                httpPost.setEntity(new StringEntity(post));
            }
            
            // if username is supplied, apply security
            if (!Strings.isNullOrEmpty(username))
                applySecurity(httpClient, request, username, !Strings.isNullOrEmpty(password) ? password : "");
            
            // perform the request
            HttpResponse httpResponse;
            httpResponse = httpClient.execute(request);
            
            // safely get the response (regardless of response code for now)
            
            // safely get the content encoding
            HttpEntity entity = httpResponse.getEntity();
            Header contentEncodingHeader = entity.getContentEncoding();
            String contentEncoding = null;
            if (contentEncodingHeader != null)
                contentEncoding = contentEncodingHeader.getValue();
            
            inputStream = entity.getContent();
            
            // deals with encoding
            InputStreamReader isr = null;
            
            // try using the given encoding
            if (!Strings.isNullOrEmpty(contentEncoding)) {
                // any unknown content encodings will cause an exception to propagate
                isr = new InputStreamReader(inputStream,  contentEncoding);
            } else {
                isr = new InputStreamReader(inputStream);
            }
            
            String content = Stream.readFully(isr);
            
            StatusLine statusLine = httpResponse.getStatusLine();
            
            if (statusLine.getStatusCode() == HttpURLConnection.HTTP_OK) {
                // 'OK' response
                return content;
            } else {
                // raise an exception including the content
                throw new IOException(String.format("Server returned '%s' with content %s", 
                        statusLine, Strings.isNullOrEmpty(content) ? "<empty>" : JSONObject.quote(content)));
            }
        } finally {
            Stream.safeClose(inputStream);
        }
    }

    /**
     * Builds up query string if args given, e.g. ...?name=My%20Name&surname=My%20Surname
     */
    private static String buildQueryString(String urlStr, Map<String, String> query) {
        StringBuilder queryArg = null;
        if (query != null) {
            StringBuilder sb = new StringBuilder();

            for (Entry<String, String> entry : query.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (Strings.isNullOrEmpty(key) || Strings.isNullOrEmpty(value))
                    continue;

                if (sb.length() > 0)
                    sb.append('&');

                sb.append(urlEncode(key))
                  .append('=')
                  .append(urlEncode(value));
            }
            
            if (sb.length() > 0)
                queryArg = sb;
        }

        String fullURL;
        if (queryArg == null)
            fullURL = urlStr;
        else
            fullURL = String.format("%s?%s", urlStr, queryArg);
        return fullURL;
    }
    
    /**
     * Applies security for a given HTTP request.
     */
    private static void applySecurity(DefaultHttpClient client, HttpRequestBase httpRequest, String username, String password) {
        // in case of NTLM, check for "\" in username
        String userPart = username;
        String domainPart = null;
        int indexOfBackSlash = username.indexOf('\\');
        if (indexOfBackSlash > 0) {
            domainPart = username.substring(0, indexOfBackSlash);
            userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));
        }

        if (domainPart == null) {
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);

            // basic or digest or whatever
            client.getCredentialsProvider().setCredentials(new AuthScope(httpRequest.getURI().getHost(), httpRequest.getURI().getPort(), AuthScope.ANY_SCHEME), creds);

            httpRequest.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false));
        } else {
            // normally used with NTLM
            client.getCredentialsProvider().setCredentials(new AuthScope(httpRequest.getURI().getHost(), httpRequest.getURI().getPort(), AuthScope.ANY_SCHEME), 
                    new NTCredentials(userPart, password, "WORKSTATION", domainPart));
        }
    }
    
    /**
     * (exception-less, convenience function)
     */
    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }    

}
