/*
 * (C) Copyright 2018-2020 Nuxeo SA (http://nuxeo.com/).
 *   This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 *   Notice of copyright on this source code does not indicate publication.
 *
 *   Contributors:
 *       Nuxeo
 */
package org.nuxeo.ai.cloud;

import okhttp3.Connection;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * @since 0.1
 */
public class LogInterceptor implements Interceptor {

    private static final Logger log = LogManager.getLogger(LogInterceptor.class);

    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    public static final String AUTHORIZATION = "Authorization";

    public static final String CONTENT_TYPE = "Content-Type";

    public static final String CONTENT_LENGTH = "Content-Length";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (log.isDebugEnabled()) {
            RequestBody requestBody = request.body();
            boolean hasRequestBody = requestBody != null;
            Connection connection = chain.connection();
            log.debug("{} {}{}{}", request.method(), request.url(),
                    connection != null ? " " + connection.protocol() : "",
                    hasRequestBody ? " (" + requestBody.contentLength() + "-byte body)" : "");
            if (log.isTraceEnabled()) {
                if (hasRequestBody) {
                    if (requestBody.contentType() != null) {
                        log.trace("  Content-Type: {}", requestBody.contentType());
                    }
                    if (requestBody.contentLength() != -1L) {
                        log.trace("  Content-Length: {}", requestBody.contentLength());
                    }
                }
                Headers headers = request.headers();
                int i = 0;
                for (int count = headers.size(); i < count; ++i) {
                    String name = headers.name(i);
                    if (PROXY_AUTHORIZATION.equalsIgnoreCase(name) || AUTHORIZATION.equalsIgnoreCase(name)) {
                        // do not log credentials
                        log.trace("  {}:****", name);
                    } else if (!CONTENT_TYPE.equalsIgnoreCase(name) && !CONTENT_LENGTH.equalsIgnoreCase(name)) {
                        log.trace("  {}:{}", name, headers.value(i));
                    }
                }
            }
        }
        Response response = chain.proceed(request);
        log.debug("RESPONSE {}", response.toString());
        return response;
    }
}
