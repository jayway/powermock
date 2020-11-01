/*
 *
 *   Copyright 2017 the original author or authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package org.powermock.configuration.support;

import org.powermock.configuration.Configuration;
import org.powermock.configuration.ConfigurationType;
import org.powermock.PowerMockInternalException;

import com.googlecode.openbeans.BeanInfo;
import com.googlecode.openbeans.Introspector;
import com.googlecode.openbeans.PropertyDescriptor;
import java.util.Properties;

class ConfigurationMapper<T extends Configuration> {
    private final Class<T> configurationClass;
    private final T configuration;
    private final ValueAliases aliases;
    
    ConfigurationMapper(final Class<T> configurationClass, final T configuration, final ValueAliases aliases) {
        this.configurationClass = configurationClass;
        this.configuration = configuration;
        this.aliases = aliases;
    }
    
    public void map(final Properties properties) {
        try {
            
            BeanInfo info = Introspector.getBeanInfo(configurationClass, Object.class);
            PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
            
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                if (propertyDescriptor.getWriteMethod() != null) {
                    mapProperty(propertyDescriptor, properties);
                }
            }
            
        } catch (Exception e) {
            throw new PowerMockInternalException(e);
        }
    }
    
    private void mapProperty(final PropertyDescriptor propertyDescriptor, final Properties properties) {
        
        final ConfigurationKey key = new ConfigurationKey(ConfigurationType.forClass(configurationClass), propertyDescriptor.getName());
        final String value = aliases.findValue((String) properties.get(key.toString()));
        
        PropertyWriter.forProperty(propertyDescriptor)
                      .writeProperty(propertyDescriptor, this.configuration, value);
    }
    
    private static class ConfigurationKey {
        private final ConfigurationType configurationType;
        private final String name;
        
        private ConfigurationKey(final ConfigurationType configurationType, final String name) {
            this.configurationType = configurationType;
            this.name = name;
        }
        
        @Override
        public String toString() {
            StringBuilder key = new StringBuilder();
            
            if (configurationType.getPrefix() != null) {
                key.append(configurationType.getPrefix());
                key.append(".");
            }
            
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isUpperCase(c)) {
                    key.append('-');
                    key.append(Character.toLowerCase(c));
                } else {
                    key.append(c);
                }
            }
            return key.toString();
        }
    }
    
    @SuppressWarnings("unchecked")
    private enum PropertyWriter {
        ArrayWriter {
            @Override
            public void writeProperty(final PropertyDescriptor pd, final Object target, final String value) {
                try {
                    if (value != null) {
                        String[] array = value.split(",");
                        pd.getWriteMethod().invoke(target, (Object) array);
                    }
                } catch (Exception e) {
                    throw new PowerMockInternalException(e);
                }
            }
        },
        StringWriter {
            @Override
            public void writeProperty(final PropertyDescriptor pd, final Object target, final String value) {
                try {
                    if (value != null) {
                        pd.getWriteMethod().invoke(target, value);
                    }
                } catch (Exception e) {
                    throw new PowerMockInternalException(e);
                }
            }
        },
        EnumWriter {
            @Override
            public void writeProperty(final PropertyDescriptor pd, final Object target, final String value) {
                try {
                    if (value != null) {
                        final Class<Enum<?>> enumClass = (Class<Enum<?>>) pd.getPropertyType();
                        final Enum<?>[] constants = enumClass.getEnumConstants();
                        for (Enum<?> constant : constants) {
                            if(value.equals(constant.name())){
                                pd.getWriteMethod().invoke(target, constant);
                                return;
                            }
                        }
                        throw new PowerMockInternalException(String.format(
                            "Find unknown enum constant `%s` for type `%s` during reading configuration.", value, enumClass
                        ));
                    }
                } catch (Exception e) {
                    throw new PowerMockInternalException(e);
                }
            }
        };
    
        private static PropertyWriter forProperty(final PropertyDescriptor pd) {
            if (String[].class.isAssignableFrom(pd.getPropertyType())) {
                return ArrayWriter;
            } else if (Enum.class.isAssignableFrom(pd.getPropertyType())) {
                return EnumWriter;
            } else {
                return StringWriter;
            }
        }
        
        abstract void writeProperty(final PropertyDescriptor propertyDescriptor, final Object target, final String value);
    }
}
