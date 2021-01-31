/*
 *   Copyright 2016 the original author or authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.powermock.core.classloader;

import org.powermock.core.transformers.ClassWrapper;
import org.powermock.core.transformers.ClassWrapperFactory;
import org.powermock.core.transformers.MockTransformer;
import org.powermock.core.transformers.MockTransformerChain;
import org.powermock.core.transformers.javassist.support.JavaAssistClassWrapperFactory;
import org.powermock.core.transformers.support.DefaultMockTransformerChain;
import org.powermock.tests.utils.IgnorePackagesExtractor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * <p>
 * The classloader loads and modified all classes except:
 * </p>
 * <ol>
 * <li>system classes. They are deferred to system classloader</li>
 * <li>classes that locate in packages that specified as packages to ignore with using {@link MockClassLoaderConfiguration#addIgnorePackage(String...)}</li>
 * </ol>
 * <p>
 * Testing frameworks classes are loaded, but not modified.
 * </p>
 * <p>
 * The classloader uses list of {@link MockTransformer} to modify classes during loading.
 * </p>
 *
 * @author Johan Haleby
 * @author Jan Kronquist
 * @author Artur Zagretdinov
 *
 * @see MockClassLoaderConfiguration
 * @see ClassLoader#getSystemClassLoader()
 * @see IgnorePackagesExtractor
 */
public abstract class MockClassLoader extends DeferSupportingClassLoader {
    
    /**
     * Pass this string to the constructor to indicate that all classes should
     * be modified.
     */
    public static final String MODIFY_ALL_CLASSES = "*";
    private static final String MOCK_MAKER_RESOURCE_NAME = "mockito-extension/org.mockito.plugins.MockMaker";
    protected ClassMarker classMarker;
    protected ClassWrapperFactory classWrapperFactory;
    private MockTransformerChain mockTransformerChain;
    
    /**
     * Creates a new instance of the  based on the
     * following parameters:
     *
     * @param classesToMock   The classes that must be modified to prepare for testability.
     * @param packagesToDefer Classes in these packages will be defered to the system
     *                        class-loader.
     */
    protected MockClassLoader(String[] classesToMock, String[] packagesToDefer) {
        this(new MockClassLoaderConfiguration(classesToMock, packagesToDefer), new JavaAssistClassWrapperFactory());
    }
    
    /**
     * Creates a new instance of the  based on the
     * following parameters:
     *
     * @param configuration The configuration of class loader. Configuration contains information about classes
     *                      which should be loaded by class loader, defer to system and mocked.
     * @param classWrapperFactory an instance of {@link ClassWrapperFactory} which is used to wrap internal framework's representation of
     *                            the class into {@link ClassWrapper}
     * @see MockClassLoaderConfiguration
     */
    protected MockClassLoader(MockClassLoaderConfiguration configuration, final ClassWrapperFactory classWrapperFactory) {
        super(MockClassLoader.class.getClassLoader(), configuration);
        this.classWrapperFactory = classWrapperFactory;
        this.mockTransformerChain = DefaultMockTransformerChain.newBuilder().build();
    }
    
    @Override
    protected Class<?> loadClassByThisClassLoader(String className) throws ClassFormatError, ClassNotFoundException {
        final Class<?> loadedClass;
        Class<?> deferClass = deferTo.loadClass(className);
        if (getConfiguration().shouldMockClass(className)) {
            loadedClass = loadMockClass(className, deferClass.getProtectionDomain());
        } else {
            loadedClass = loadUnmockedClass(className, deferClass.getProtectionDomain());
        }
        return loadedClass;
    }
    
    public void setMockTransformerChain(MockTransformerChain mockTransformerChain) {
        this.mockTransformerChain = mockTransformerChain;
    }
    
    public MockTransformerChain getMockTransformerChain() {
        return mockTransformerChain;
    }
    
    protected Class<?> loadUnmockedClass(final String name, final ProtectionDomain protectionDomain) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");
        URL res = deferTo.getResource(path);
        if (res != null) {
            try {
                return defineClass(name, res, protectionDomain);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        } else {
            throw new ClassNotFoundException(name);
        }
    }
    
    private Class<?> defineClass(String name, URL url, final ProtectionDomain protectionDomain) throws IOException {
        byte[] b = readClass(url);
        return defineClass(name, b, 0, b.length, protectionDomain);
    }
    
    private byte[] readClass(final URL url) throws IOException {
        final URLConnection connection = url.openConnection();
        
        final InputStream in = connection.getInputStream();
        ByteArrayOutputStream tmpOut = null;
        
        try {
            
            final int contentLength = connection.getContentLength();
            
            // To avoid having to resize the array over and over and over as
            // bytes are written to the array, provide an accurate estimate of
            // the ultimate size of the byte array
            
            if (contentLength != -1) {
                tmpOut = new ByteArrayOutputStream(contentLength);
            } else {
                tmpOut = new ByteArrayOutputStream(16384);
            }
            
            byte[] buf = new byte[512];
            while (true) {
                int len = in.read(buf);
                if (len == -1) {
                    break;
                }
                tmpOut.write(buf, 0, len);
            }
            return tmpOut.toByteArray();
        } finally {
            in.close();
            if (tmpOut != null) {
                tmpOut.close();
            }
        }
    }
    
    private Class<?> loadMockClass(String name, ProtectionDomain protectionDomain) throws ClassNotFoundException {
        final byte[] clazz = defineAndTransformClass(name, protectionDomain);
    
        return defineClass(name, protectionDomain, clazz);
    }
    
    public Class<?> defineClass(final String name, final ProtectionDomain protectionDomain, final byte[] clazz) {
        return defineClass(name, clazz, 0, clazz.length, protectionDomain);
    }
    
    protected <T> ClassWrapper<T> transformClass(ClassWrapper<T> wrappedType) throws Exception {
        wrappedType = mockTransformerChain.transform(wrappedType);
        
        if (classMarker != null) {
            classMarker.mark(wrappedType);
        }
        return wrappedType;
    }
    
    protected abstract byte[] defineAndTransformClass(final String name, final ProtectionDomain protectionDomain) throws ClassNotFoundException;

    @Override
    protected final Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> resources = super.findResources(name);
        if (name.equals(MOCK_MAKER_RESOURCE_NAME))
            return prioritizePowermockMockMaker(resources);
        return resources;
    }

    /**
     * Prioritize the MockMaker from powermock-api-mockito2 dependency
     *
     * @param resourcesEnumeration the given enumeration of resource URLs
     * @return the reordered enumeration of resource URLs
     */
    private static Enumeration<URL> prioritizePowermockMockMaker(Enumeration<URL> resourcesEnumeration) {
        Vector<URL> resources = new Vector<URL>();
        final String powermockAPIMockito2 = "powermock-api-mockito2";
        URL powermockMockMakerURL = null;
        while(resourcesEnumeration.hasMoreElements()) {
            URL current = resourcesEnumeration.nextElement();
            String currentPath = current.getPath();
            if (currentPath.contains(powermockAPIMockito2))
                powermockMockMakerURL = current;
            else resources.add(current);
        }
        if (powermockMockMakerURL != null) resources.add(0, powermockMockMakerURL);
        else throw new IllegalArgumentException("given enumeration has to contain the resource, " + MOCK_MAKER_RESOURCE_NAME + ", in " + powermockAPIMockito2 + " jar. Please make sure the dependency configuration is correct");
        return resources.elements();
    }
}
