/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powermock.core.classloader;

import org.junit.Test;
import org.powermock.core.classloader.javassist.JavassistMockClassLoader;
import org.powermock.core.test.MockClassLoaderFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

public class MockClassLoaderCornerCasesTest {

    private static final MockClassLoaderFactory mockClassLoaderFactory = new MockClassLoaderFactory(JavassistMockClassLoader.class);
    
    @Test
    public void getResourcesMockMakerFromPowermockAPIMockito2ShouldBePrioritized() throws Exception {
        final MockClassLoader mockClassLoader = mockClassLoaderFactory.getInstance(new String[0]);
        final URL mockitoInline = new File("mockito-inline-x.x.x.jar!/mockito-extensions/org.mockito.plugins.MockMaker").toURI().toURL();
        final URL powermockAPIMockito2 = new File("powermock-api-mockito2-x.x.x.jar!/mockito-extensions/org.mockito.plugins.MockMaker").toURI().toURL();
        mockClassLoader.deferTo = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Enumeration<URL> findResources(String name) throws IOException {
                Vector<URL> vector = new Vector<URL>();
                vector.add(mockitoInline);
                vector.add(powermockAPIMockito2);
                return vector.elements();
            }
        };
        Enumeration<URL> result = mockClassLoader.getResources("mockito-extension/org.mockito.plugins.MockMaker");
        assertThat(result.nextElement()).isSameAs(powermockAPIMockito2);
        assertThat(result.nextElement()).isSameAs(mockitoInline);
    }
}
