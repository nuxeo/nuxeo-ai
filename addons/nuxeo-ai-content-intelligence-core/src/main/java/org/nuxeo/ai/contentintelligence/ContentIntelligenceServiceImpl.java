/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nuxeo.ai.contentintelligence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyland.sdk.cic.http.client.CICSdkException;
import org.hyland.sdk.cic.http.client.CICServiceException;
import org.hyland.sdk.cic.http.client.auth.AuthenticationHttpClient;
import org.hyland.sdk.cic.http.client.mapper.object.CICBlob;
import org.hyland.sdk.cic.ke.KEHttpClient;
import org.hyland.sdk.cic.ke.KEService;
import org.hyland.sdk.cic.ke.object.EnrichmentResult;
import org.hyland.sdk.cic.ke.object.ObjectKey;
import org.hyland.sdk.cic.ke.object.PresignedUrl;
import org.hyland.sdk.cic.ke.object.ProcessRequest;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * {@link ContentIntelligenceService} backed by one SDK client per complete {@code knowledgeEnrichment}
 * configuration. Every SDK failure is wrapped in a {@link NuxeoException} keeping the SDK exception as cause.
 *
 * @since 5.0.5
 */
public class ContentIntelligenceServiceImpl implements ContentIntelligenceService, AutoCloseable {

    private static final Logger log = LogManager.getLogger(ContentIntelligenceServiceImpl.class);

    protected static final String STATUS_PROCESSING = "PROCESSING";

    /** Configuration used when no (or a blank) configuration name is given. */
    public static final String DEFAULT_CONFIG_NAME = "default";

    protected static final String DEFAULT_GRANT_TYPE = "client_credentials";

    protected static final String DEFAULT_SCOPE = "environment_authorization";

    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** Bounds every request, including the upload of large documents. */
    protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    protected static final Pattern UPLOAD_STATUS = Pattern.compile("status code: (\\d{3})");

    protected static final String TOKEN_PATH = "/connect/token";

    protected final Map<String, KEHttpClient> clients;

    protected final int maxTries;

    protected final Duration pollInterval;

    public ContentIntelligenceServiceImpl(List<ContentIntelligenceDescriptor> descriptors, int maxTries,
            Duration pollInterval) {
        this.maxTries = Math.max(1, maxTries);
        this.pollInterval = pollInterval == null || pollInterval.isNegative() ? Duration.ZERO : pollInterval;
        Map<String, KEHttpClient> clientsByName = new HashMap<>();
        for (ContentIntelligenceDescriptor desc : descriptors) {
            if (!desc.hasAllValues()) {
                log.warn("Knowledge Enrichment configuration '{}' is incomplete (name, authentication URL, base URL, "
                        + "client id and client secret are required), Content Intelligence calls using it will fail",
                        desc.getName());
                continue;
            }
            if (!isHttpUrl(desc.getAuthenticationBaseUrl()) || !isHttpUrl(desc.getBaseUrl())) {
                log.warn("Knowledge Enrichment configuration '{}' has an invalid URL (authentication URL: {}, base "
                        + "URL: {}), Content Intelligence calls using it will fail", desc.getName(),
                        desc.getAuthenticationBaseUrl(), desc.getBaseUrl());
                continue;
            }
            KEHttpClient client = buildClient(desc);
            clientsByName.put(desc.getName(), client);
        }
        clients = Map.copyOf(clientsByName);
    }

    protected static boolean isHttpUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && StringUtils.isNotBlank(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    protected static String withoutTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    protected KEHttpClient buildClient(ContentIntelligenceDescriptor desc) {
        String tokenUrl = withoutTrailingSlash(desc.getAuthenticationBaseUrl()) + TOKEN_PATH;
        var auth = AuthenticationHttpClient.from(tokenUrl)
                                           .clientId(desc.getClientId())
                                           .clientSecret(desc.getClientSecret())
                                           .grantType(StringUtils.defaultIfBlank(desc.getTokenGrantType(),
                                                   DEFAULT_GRANT_TYPE))
                                           .scope(StringUtils.defaultIfBlank(desc.getTokenScope(), DEFAULT_SCOPE))
                                           .connectTimeout(CONNECT_TIMEOUT)
                                           .requestTimeout(REQUEST_TIMEOUT);
        return KEHttpClient.from(withoutTrailingSlash(desc.getBaseUrl()), auth)
                           .connectTimeout(CONNECT_TIMEOUT)
                           .requestTimeout(REQUEST_TIMEOUT)
                           .build();
    }

    @Override
    public EnrichmentResult enrich(String configName, Blob blob, ContentIntelligenceRequest request) {
        String name = StringUtils.defaultIfBlank(configName, DEFAULT_CONFIG_NAME);
        KEHttpClient client = clients.get(name);
        if (client == null) {
            throw new NuxeoException(String.format(
                    "No complete Knowledge Enrichment configuration '%s': check the nuxeo.hyland.cic.* properties "
                            + "or the knowledgeEnrichment contributions",
                    name));
        }
        // the steps of KEService#sendForEnrichment, run one by one to report the status of a failed upload
        CICBlob cicBlob = callCIC("blob preparation", name, () -> toCICBlob(blob));
        String contentType = cicBlob.getContentType().orElse("application/octet-stream");
        PresignedUrl presignedUrl = callCIC("presigned URL request", name, () -> client.getPresignedUrl(contentType));
        ProcessRequest processRequest = callCIC("enrichment request", name, () -> {
            ProcessRequest.Builder builder = ProcessRequest.builder();
            request.configure(builder);
            return builder.objectKeys(List.of(ObjectKey.forPath(presignedUrl.objectKey()))).build();
        });
        callCIC("upload", name, () -> {
            upload(client, presignedUrl.presignedUrl(), cicBlob);
            return null;
        });
        String processingId = callCIC("enrichment request", name, () -> client.process(processRequest));
        return pollResults(name, processingId);
    }

    /**
     * The SDK reports a non-2xx upload answer (after its own retries) as a plain {@link CICSdkException} with the
     * status in its message: turn it into a {@link CICServiceException} so callers can tell client from server errors.
     */
    protected static void upload(KEHttpClient client, String presignedUrl, CICBlob blob) {
        try {
            client.upload(presignedUrl, blob);
        } catch (CICServiceException e) {
            throw e;
        } catch (CICSdkException e) {
            Matcher matcher = UPLOAD_STATUS.matcher(StringUtils.defaultString(e.getMessage()));
            int code = matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
            if (code >= 400 && code < 600) {
                CICServiceException status = new CICServiceException(e.getMessage(), code);
                status.initCause(e);
                throw status;
            }
            throw e;
        }
    }

    /**
     * Polls with the client instead of {@link KEService#pollResults(String)}, which throws the same exception for an
     * exhausted budget as for a failure. A result still flagged in progress keeps being polled.
     */
    protected EnrichmentResult pollResults(String configName, String processingId) {
        KEHttpClient client = clients.get(configName);
        for (int attempt = 1; attempt <= maxTries; attempt++) {
            EnrichmentResult result = callCIC("results retrieval", configName,
                    () -> client.getResultsIfReady(processingId));
            if (result != null && !result.inProgress() && !STATUS_PROCESSING.equalsIgnoreCase(result.status())) {
                return result;
            }
            if (attempt < maxTries) {
                sleep(pollInterval);
            }
        }
        log.debug("Knowledge Enrichment results of {} not ready after {} tries", processingId, maxTries);
        return new EnrichmentResult(processingId, null, List.of(), STATUS_PROCESSING, true);
    }

    protected void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NuxeoException("Interrupted while waiting for Knowledge Enrichment results", e);
        }
    }

    protected static CICBlob toCICBlob(Blob blob) {
        CICBlob.Builder builder = CICBlob.builder(() -> {
            try {
                return blob.getStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        if (StringUtils.isNotBlank(blob.getMimeType())) {
            builder.contentType(blob.getMimeType());
        }
        if (StringUtils.isNotBlank(blob.getFilename())) {
            builder.name(blob.getFilename());
        }
        if (StringUtils.isNotBlank(blob.getDigest())) {
            builder.digest(blob.getDigest());
        }
        return builder.build();
    }

    protected static <T> T callCIC(String operation, String configName, Supplier<T> call) {
        // the SDK loads its JSON serializer and mappers with ServiceLoader, i.e. from the thread context class loader:
        // make sure it sees this bundle's classes whatever the calling (stream) thread is
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(ContentIntelligenceServiceImpl.class.getClassLoader());
        try {
            return call.get();
        } catch (CICServiceException e) {
            throw new NuxeoException(
                    String.format("Content Intelligence %s failed for configuration '%s' (HTTP %d): %s", operation,
                            configName, e.statusCode(), e.getMessage()),
                    e);
        } catch (RuntimeException e) {
            // CICSdkException (network, upload, parsing), UncheckedIOException from the blob, or any SDK mapping error:
            // never let it escape unwrapped, the enrichment retry policy would replay the whole upload
            throw new NuxeoException(String.format("Content Intelligence %s failed for configuration '%s': %s",
                    operation, configName, e.getMessage()), e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Override
    public void close() {
        clients.values().forEach(KEHttpClient::close);
    }
}
