/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Gethin James
 */
package org.nuxeo.runtime.stream.pipes.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.function.UnaryOperator;

import org.junit.Test;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.stream.LogConfigDescriptor;
import org.nuxeo.runtime.stream.pipes.functions.FilterFunction;

public class TestPipeDescriptor {

    @Test
    public void TestDescriptor() {
        PipeDescriptor descriptor = new PipeDescriptor();
        validate(descriptor, true, true, true);
        descriptor.id = "myId";
        validate(descriptor, true, true, true);
        descriptor.function = FilterFunction.class;
        validate(descriptor, false, true, true);
        PipeDescriptor.Supplier supplier = new PipeDescriptor.Supplier();
        descriptor.supplier = supplier;
        validate(descriptor, false, true, true);
        supplier.events = Collections.singletonList("bob");
        validate(descriptor, false, false, true);
        PipeDescriptor.Consumer logConsumer = new PipeDescriptor.Consumer();
        descriptor.consumer = logConsumer;
        validate(descriptor, false, false, true);
        logConsumer.streams = Collections.singletonList(new LogConfigDescriptor.StreamDescriptor());
        descriptor.consumer = logConsumer;
        descriptor.validate(); //now valid
    }

    @Test
    public void TestValidFunction() {
        PipeDescriptor pipeDescriptor = new PipeDescriptor();
        pipeDescriptor.function = UnaryOperator.class;
        try {
            pipeDescriptor.getFunction();
            assertFalse(true); //never get here
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("must define a valid Function class"));
        }
    }

    public void validate(PipeDescriptor descriptor, boolean func, boolean supplier, boolean consumer) {
        try {
            descriptor.validate();
            assertFalse(true); //never get here
        } catch (NuxeoException e) {
            assertEquals(func, e.getMessage().contains("You must specify a function"));
            assertEquals(supplier, e.getMessage().contains("Invalid supplier configuration"));
            assertEquals(consumer, e.getMessage().contains("Invalid consumer configuration"));
        }
    }
}
