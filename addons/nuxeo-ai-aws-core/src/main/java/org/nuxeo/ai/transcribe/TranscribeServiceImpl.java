/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.transcribe;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ecm.core.api.Blob;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.transcribe.AmazonTranscribe;
import com.amazonaws.services.transcribe.AmazonTranscribeClient;
import com.google.common.primitives.Doubles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.Alternative;
import software.amazon.awssdk.services.transcribestreaming.model.Item;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

public class TranscribeServiceImpl implements TranscribeService {

    private static final Logger log = LogManager.getLogger(TranscribeServiceImpl.class);

    private static final int DEFAULT_HZ = 16_000;

    protected AmazonTranscribe client;

    protected AwsCredentials credentials;

    @Override
    public CompletableFuture<List<Alternative>> transcribe(Blob blob, LanguageCode code) {
        CompletableFuture<List<Alternative>> future = new CompletableFuture<>();
        Executors.newFixedThreadPool(1)
                .submit(new Transcribe(code, blob, future));

        return future;
    }

    @Override
    public List<Alternative> normalize(List<Alternative> alters) {
        List<Alternative> result = new LinkedList<>();

        double st = -1.f;
        Alternative last = null;
        for (Alternative alter : alters) {
            List<Item> items = alter.items();
            if (!items.isEmpty() && st < 0.f) {
                st = items.get(0).startTime();
            } else if (!items.isEmpty() && items.get(0).startTime() == st) {
                last = alter;
            } else if (!items.isEmpty() && items.get(0).startTime() > st) {
                st = items.get(0).startTime();
                result.add(last);
                last = alter;
            } else {
                /* NOP */
            }
        }

        if (last != null) {
            result.add(last);
        }

        return result;
    }

    protected AmazonTranscribe getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AmazonTranscribeClient.builder()
                            .withCredentials(AWSHelper.getInstance().getCredentialsProvider())
                            .withRegion(AWSHelper.getInstance().getRegion())
                            .build();
                }
            }
        }

        return client;
    }

    /**
     * @return {@link AwsCredentials} for AWS SDK 2.x
     */
    protected AwsCredentials awsCredentialsProvider2O() {
        if (credentials == null) {
            synchronized (this) {
                if (credentials == null) {
                    AWSCredentialsProvider credentialsProvider = AWSHelper.getInstance().getCredentialsProvider();
                    AWSCredentials creds = credentialsProvider.getCredentials();
                    if (creds instanceof BasicAWSCredentials) {
                        this.credentials = AwsBasicCredentials.create(creds.getAWSAccessKeyId(), creds.getAWSSecretKey());
                    } else {
                        BasicSessionCredentials sessCred = (BasicSessionCredentials) creds;
                        this.credentials = AwsSessionCredentials.create(sessCred.getAWSAccessKeyId(), sessCred.getAWSSecretKey(), sessCred.getSessionToken());
                    }
                }
            }
        }

        return credentials;
    }

    protected class Transcribe implements Callable<Object> {

        protected final LanguageCode code;

        protected final Blob blob;

        protected final CompletableFuture<List<Alternative>> future;

        public Transcribe(LanguageCode code, Blob blob, CompletableFuture<List<Alternative>> future) {
            this.code = code;
            this.blob = blob;
            this.future = future;
        }

        @Override
        public Object call() {
            TranscribeStreamingAsyncClient client = TranscribeStreamingAsyncClient.builder()
                    .credentialsProvider(TranscribeServiceImpl.this::awsCredentialsProvider2O)
                    .build();

            StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                    .mediaEncoding(MediaEncoding.PCM)
                    .languageCode(code)
                    .mediaSampleRateHertz(DEFAULT_HZ)
                    .build();

            AudioStreamPublisher publisher;
            try {
                publisher = new AudioStreamPublisher(blob.getStream());
            } catch (IOException e) {
                log.error(e);
                future.complete(Collections.emptyList());
                return null;
            }

            List<Alternative> result = new LinkedList<>();
            StartStreamTranscriptionResponseHandler response = StartStreamTranscriptionResponseHandler.builder()
                    .onError(e -> {
                        log.error(e);
                        future.complete(Collections.emptyList());
                    })
                    .subscriber(e -> {
                        TranscriptEvent event = (TranscriptEvent) e;
                        List<Alternative> alter = event.transcript().results().stream()
                                .flatMap(r -> r.alternatives().stream())
                                .collect(Collectors.toList());
                        result.addAll(alter);
                    })
                    .build();

            client.startStreamTranscription(request, publisher, response).join();

            sort(result);
            future.complete(result);

            return null;
        }


        protected void sort(List<Alternative> result) {
            result.sort((a1, a2) -> {
                double st1 = a1.items().stream()
                        .findFirst()
                        .orElseGet(() -> Item.builder().startTime(-1.0).build())
                        .startTime();

                double st2 = a2.items().stream()
                        .findFirst()
                        .orElseGet(() -> Item.builder().startTime(-1.0).build())
                        .startTime();
                return Doubles.compare(st1, st2);
            });
        }
    }
}
