package org.nuxeo.ai.jwt;

import java.security.PublicKey;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.keystore.JWKService;
import org.nuxeo.ai.keystore.KeyPairContainer;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import static org.junit.Assert.*;

/**
 * Tests JWK key pair generation. Full PKS REST endpoint coverage (routing, content type, status codes) should be added
 * in a dedicated integration test using RestServerFeature.
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-config")
public class TestPKSEndpoint {

    @Inject
    protected JWKService jwk;

    @Test
    public void shouldGenerateValidRSAKeyPair() throws Exception {
        KeyPairContainer kpc = jwk.get();
        assertNotNull(kpc);
        assertNotNull(kpc.getKid());
        PublicKey pub = kpc.getPublicKey();
        assertEquals("RSA", pub.getAlgorithm());
        assertTrue(pub.getEncoded().length > 0);
    }
}
