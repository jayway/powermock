/*
 * Copyright 2011 the original author or authors.
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

package org.powermock.tests.utils.impl;

import org.powermock.configuration.GlobalConfiguration;
import org.powermock.configuration.PowerMockConfiguration;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.tests.utils.IgnorePackagesExtractor;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class PowerMockIgnorePackagesExtractorImpl implements IgnorePackagesExtractor {
    
    @Override
    public String[] getPackagesToIgnore(AnnotatedElement element) {
        Set<String> ignoredPackages = new HashSet<String>();
        
        extractPackageToIgnore(element, ignoredPackages);
        
        final String[] packageToIgnore = ignoredPackages.toArray(new String[ignoredPackages.size()]);
        final String[] allPackageToIgnore;
        
        final PowerMockConfiguration powerMockConfiguration = GlobalConfiguration.powerMockConfiguration();
        
        String[] globalIgnore = powerMockConfiguration.getGlobalIgnore();
        if (globalIgnore != null) {
            allPackageToIgnore = addGlobalIgnore(packageToIgnore, globalIgnore);
        } else {
            allPackageToIgnore = packageToIgnore;
        }
        
        return allPackageToIgnore;
    }
    
    private void extractPackageToIgnore(final AnnotatedElement element, final Set<String> ignoredPackages) {
        addValueFromAnnotation(element, ignoredPackages);
        addValuesFromSuperclass((Class<?>) element, ignoredPackages);
    }
    
    private void addValuesFromSuperclass(final Class<?> element, final Set<String> ignoredPackages) {
        final Collection<Class<?>> superclasses = new ArrayList<Class<?>>();
        Collections.addAll(superclasses, element.getSuperclass());
        Collections.addAll(superclasses, element.getInterfaces());
        
        for (Class<?> superclass : superclasses) {
            if (superclass != null && !superclass.equals(Object.class)) {
                extractPackageToIgnore(superclass, ignoredPackages);
            }
        }
    }
    
    private void addValueFromAnnotation(final AnnotatedElement element, final Set<String> ignoredPackages) {
        PowerMockIgnore annotation = element.getAnnotation(PowerMockIgnore.class);
        
        if (annotation != null) {
            String[] ignores = annotation.value();
            Collections.addAll(ignoredPackages, ignores);
        }
    }
    
    private String[] addGlobalIgnore(final String[] packageToIgnore, final String[] globalIgnore) {
        final String[] allPackageToIgnore;
        
        allPackageToIgnore = new String[globalIgnore.length + packageToIgnore.length];
        
        System.arraycopy(globalIgnore, 0, allPackageToIgnore, 0, globalIgnore.length);
        System.arraycopy(packageToIgnore, 0, allPackageToIgnore, globalIgnore.length, packageToIgnore.length);
        
        return allPackageToIgnore;
    }
    
}
