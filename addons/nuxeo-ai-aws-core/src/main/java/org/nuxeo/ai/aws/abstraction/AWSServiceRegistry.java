/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.ai.aws.abstraction.impl.*;

/**
 * Central registry for all AWS service facades. This is the single entry point for accessing AWS services without any
 * SDK dependencies. Service implementations only need to inject this registry and access facades through it. All AWS
 * SDK dependencies are completely isolated in facade implementations.
 */
public class AWSServiceRegistry extends DefaultComponent {

    private ComprehendServiceFacade comprehendFacade;

    private RekognitionServiceFacade rekognitionFacade;

    private TextractServiceFacade textractFacade;

    private TranslateServiceFacade translateFacade;

    @Override
    public void start(ComponentContext context) {
        super.start(context);

        // Initialize all service facades
        comprehendFacade = new ComprehendServiceFacadeImpl();
        rekognitionFacade = new RekognitionServiceFacadeImpl();
        textractFacade = new TextractServiceFacadeImpl();
        translateFacade = new TranslateServiceFacadeImpl();

        // Start facade components
        startFacadeComponent(comprehendFacade, context);
        startFacadeComponent(rekognitionFacade, context);
        startFacadeComponent(textractFacade, context);
        startFacadeComponent(translateFacade, context);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);

        // Stop facade components
        stopFacadeComponent(comprehendFacade, context);
        stopFacadeComponent(rekognitionFacade, context);
        stopFacadeComponent(textractFacade, context);
        stopFacadeComponent(translateFacade, context);

        // Clear references
        comprehendFacade = null;
        rekognitionFacade = null;
        textractFacade = null;
        // transcribeFacade = null;
        translateFacade = null;
    }

    /**
     * Get Comprehend service facade - no AWS SDK imports needed
     */
    public ComprehendServiceFacade getComprehendService() {
        return comprehendFacade;
    }

    /**
     * Get Rekognition service facade - no AWS SDK imports needed
     */
    public RekognitionServiceFacade getRekognitionService() {
        return rekognitionFacade;
    }

    /**
     * Get Textract service facade - no AWS SDK imports needed
     */
    public TextractServiceFacade getTextractService() {
        return textractFacade;
    }

    /**
     * Get Translate service facade - no AWS SDK imports needed
     */
    public TranslateServiceFacade getTranslateService() {
        return translateFacade;
    }

    /**
     * Convenience method to get AWS service registry instance
     */
    public static AWSServiceRegistry getInstance() {
        return Framework.getService(AWSServiceRegistry.class);
    }

    // Helper methods for component lifecycle management
    private void startFacadeComponent(Object facade, ComponentContext context) {
        if (facade instanceof DefaultComponent) {
            ((DefaultComponent) facade).start(context);
        }
    }

    private void stopFacadeComponent(Object facade, ComponentContext context) throws InterruptedException {
        if (facade instanceof DefaultComponent) {
            ((DefaultComponent) facade).stop(context);
        }
    }
}
