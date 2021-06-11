/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Nuxeo
 */
package org.nuxeo.ai.metrics;

import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;

/**
 * AWS Metrics service
 */
public class AWSMetrics extends DefaultComponent {

    protected final static MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected Counter rekognitionGlobalCalls;

    protected Counter textractGlobalCalls;

    protected Timer rekognitionVideoCalls;

    protected Timer rekognitionVideoFaceDetectionCall;

    protected Timer rekognitionVideoLabelDetectionCall;

    private Timer rekognitionVideoCelebritiesDetectionCall;

    private Timer rekognitionVideoUnsafeDetectionCall;

    protected Counter rekognitionImgCalls;

    protected Counter rekognitionImgLabelDetectionCounter;

    protected Counter rekognitionImgTextDetectionCounter;

    protected Counter rekognitionImgFaceDetectionCounter;

    protected Counter rekognitionImgCelebritiesDetectionCounter;

    protected Counter rekognitionImgUnsafeDetectionCounter;

    protected Histogram comprehendTotalUnits;

    protected Histogram comprehendSentimentUnits;

    protected Histogram comprehendKeyphraseUnits;

    protected Histogram comprehendEntitiesUnits;

    protected Histogram translateTotalChars;

    protected Counter transcribeGlobalCalls;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        this.register();
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        this.unregister();
    }

    /**
     * Method register metrics within shared registry of ${@link MetricRegistry}
     */
    public void register() {
        String rekognitionName = getRekognitionName();
        String comprehendName = getComprehendName();
        String textractName = getTextractName();
        String transcribeName = getTranscribeName();
        String translateName = getTranslateName();

        // Rekognition
        rekognitionGlobalCalls = registry.counter(MetricRegistry.name(rekognitionName, "rekognitionGlobalCalls"));

        // Rekognition Images
        rekognitionImgCalls = registry.counter(MetricRegistry.name(rekognitionName, "rekognitionImgCalls"));
        rekognitionImgLabelDetectionCounter = registry.counter(
                MetricRegistry.name(rekognitionName, "rekognitionImgLabelDetectionCounter"));
        rekognitionImgTextDetectionCounter = registry.counter(
                MetricRegistry.name(rekognitionName, "rekognitionImgTextDetectionCounter"));
        rekognitionImgFaceDetectionCounter = registry.counter(
                MetricRegistry.name(rekognitionName, "rekognitionImgFaceDetectionCounter"));
        rekognitionImgCelebritiesDetectionCounter = registry.counter(
                MetricRegistry.name(rekognitionName, "rekognitionImgCelebritiesDetectionCounter"));
        rekognitionImgUnsafeDetectionCounter = registry.counter(
                MetricRegistry.name(rekognitionName, "rekognitionImgUnsafeDetectionCounter"));

        // Rekognition Videos
        rekognitionVideoCalls = registry.timer(MetricRegistry.name(rekognitionName, "rekognitionVideoCalls"));
        rekognitionVideoFaceDetectionCall = registry.timer(
                MetricRegistry.name(rekognitionName, "rekognitionVideoFaceDetectionCall"));
        rekognitionVideoLabelDetectionCall = registry.timer(
                MetricRegistry.name(rekognitionName, "rekognitionVideoLabelDetectionCall"));
        rekognitionVideoCelebritiesDetectionCall = registry.timer(
                MetricRegistry.name(rekognitionName, "rekognitionVideoCelebritiesDetectionCall"));
        rekognitionVideoUnsafeDetectionCall = registry.timer(
                MetricRegistry.name(rekognitionName, "rekognitionVideoUnsafeDetectionCall"));

        // Comprehend
        comprehendTotalUnits = registry.histogram(MetricRegistry.name(comprehendName, "comprehendTotalUnits"));
        comprehendSentimentUnits = registry.histogram(MetricRegistry.name(comprehendName, "comprehendSentimentUnits"));
        comprehendKeyphraseUnits = registry.histogram(MetricRegistry.name(comprehendName, "comprehendKeyphraseUnits"));
        comprehendEntitiesUnits = registry.histogram(MetricRegistry.name(comprehendName, "comprehendEntitiesUnits"));

        // Textract (Price is based on pages number - we don't use pdfbox to calculate those page nb yet TODO AICORE-446)
        textractGlobalCalls = registry.counter(MetricRegistry.name(textractName, "textractGlobalCalls"));

        // Transcribe (Price is based on track length - we do not count this information yet TODO AICORE-446)
        transcribeGlobalCalls = registry.counter(MetricRegistry.name(transcribeName, "transcribeGlobalCalls"));

        // Translate
        translateTotalChars = registry.histogram(MetricRegistry.name(translateName, "translateTotalChars"));
    }

    /**
     * Method removes all class defined metrics from shared registry of ${@link MetricRegistry}
     */
    public void unregister() {
        String basicName = getAWSName();
        registry.removeMatching((name, metrics) -> name.startsWith(basicName));
    }

    protected String getAWSName(String... names) {
        return MetricRegistry.name("nuxeo.ai.aws", names);
    }

    protected String getRekognitionName() {
        return getAWSName("rekognition");
    }

    protected String getTranscribeName() {
        return getAWSName("transcribe");
    }

    protected String getTextractName() {
        return getAWSName("textract");
    }

    protected String getComprehendName() {
        return getAWSName("comprehend");
    }

    protected String getTranslateName() {
        return getAWSName("translate");
    }

    public Histogram getTranslateTotalChars() {
        return translateTotalChars;
    }

    public Counter getTextractGlobalCalls() {
        return textractGlobalCalls;
    }

    public long getRekognitionGlobalCalls() {
        return rekognitionGlobalCalls.getCount();
    }

    public long getRekognitionVideoCalls() {
        return rekognitionVideoCalls.getCount();
    }

    public long getRekognitionImgCalls() {
        return rekognitionImgCalls.getCount();
    }

    public long getRekognitionVideoFaceDetectionCall() {
        return rekognitionVideoFaceDetectionCall.getCount();
    }

    public long getRekognitionVideoLabelDetectionCall() {
        return rekognitionVideoLabelDetectionCall.getCount();
    }

    public long getRekognitionVideoCelebritiesDetectionCall() {
        return rekognitionVideoCelebritiesDetectionCall.getCount();
    }

    public Counter getTranscribeGlobalCalls() {
        return transcribeGlobalCalls;
    }

    public long getRekognitionVideoUnsafeDetectionCall() {
        return rekognitionVideoUnsafeDetectionCall.getCount();
    }

    public Counter getRekognitionImgLabelDetectionCounter() {
        return rekognitionImgLabelDetectionCounter;
    }

    public Counter getRekognitionImgTextDetectionCounter() {
        return rekognitionImgTextDetectionCounter;
    }

    public Counter getRekognitionImgFaceDetectionCounter() {
        return rekognitionImgFaceDetectionCounter;
    }

    public Counter getRekognitionImgCelebritiesDetectionCounter() {
        return rekognitionImgCelebritiesDetectionCounter;
    }

    public Counter getRekognitionImgUnsafeDetectionCounter() {
        return rekognitionImgUnsafeDetectionCounter;
    }

    public Histogram getComprehendTotalUnits() {
        return comprehendTotalUnits;
    }

    public Histogram getComprehendSentimentUnits() {
        return comprehendSentimentUnits;
    }

    public Histogram getComprehendKeyphraseUnits() {
        return comprehendKeyphraseUnits;
    }

    public Histogram getComprehendEntitiesUnits() {
        return comprehendEntitiesUnits;
    }

    public void incrementRekognitionGlobalCalls() {
        rekognitionGlobalCalls.inc();
    }

    public void incrementRekognitionImgCalls() {
        rekognitionImgCalls.inc();
    }

    public Counter rekognitionImgLabelDetectionCounter() {
        return rekognitionImgLabelDetectionCounter;
    }

    public Timer rekognitionVideoCall() {
        return rekognitionVideoCalls;
    }

    public Timer rekognitionVideoFaceDetectionCall() {
        return rekognitionVideoFaceDetectionCall;
    }

    public Timer rekognitionVideoLabelDetectionCall() {
        return rekognitionVideoLabelDetectionCall;
    }

    public Counter rekognitionImgTextDetectionCounter() {
        return rekognitionImgTextDetectionCounter;
    }

    public Counter rekognitionImgFaceDetectionCounter() {
        return rekognitionImgFaceDetectionCounter;
    }

    public Counter rekognitionImgCelebritiesDetectionCounter() {
        return rekognitionImgCelebritiesDetectionCounter;
    }

    public Timer rekognitionVideoCelebritiesDetectionCall() {
        return rekognitionVideoCelebritiesDetectionCall;
    }

    public Counter rekognitionImgUnsafeDetectionCounter() {
        return rekognitionImgUnsafeDetectionCounter;
    }

    public Timer rekognitionVideoUnsafeDetectionCall() {
        return rekognitionVideoUnsafeDetectionCall;
    }

    public void updateComprehendSentimentUnits(long value) {
        getComprehendSentimentUnits().update(value);
        getComprehendTotalUnits().update(value);
    }

    public void updateComprehendKeyphraseUnits(long value) {
        getComprehendKeyphraseUnits().update(value);
        getComprehendTotalUnits().update(value);
    }

    public void updateComprehendEntitiesUnits(long value) {
        getComprehendEntitiesUnits().update(value);
        getComprehendTotalUnits().update(value);
    }
}
