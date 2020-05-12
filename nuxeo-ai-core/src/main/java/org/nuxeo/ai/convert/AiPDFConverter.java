/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.convert;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;

/**
 * Converter for PDF to produce text/plain
 */
public class AiPDFConverter implements Converter {

    public static final String AI_PDF_CONVERTER = "aiPDF2text";

    public static final String PDF_MIME_TYPE = "application/pdf";

    @Override
    public void init(ConverterDescriptor converterDescriptor) {
        /* NOP */
    }

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> map) throws ConversionException {
        try (InputStream is = blobHolder.getBlob().getStream(); //
                PDDocument document = PDDocument.load(is)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            String text = textStripper.getText(document);
            return new SimpleBlobHolder(Blobs.createBlob(text));
        } catch (IOException e) {
            throw new ConversionException(e);
        }
    }
}
