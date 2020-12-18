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
package org.powermock.core;

import org.powermock.core.spi.MethodInvocationControl;
import org.powermock.core.spi.NewInvocationControl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hold mock objects that should be used instead of the concrete implementation.
 * Mock transformers may use this class to gather information on which classes
 * and methods that are mocked.
 */
public class MockRepository {

	private static Set<Object> objectsToAutomaticallyReplayAndVerify = new IdentityHashSet<Object>();

	private static Map<Class<?>, NewInvocationControl<?>> newSubstitutions = new HashMap<Class<?>, NewInvocationControl<?>>();

	/**
	 * Holds info about general method invocation mocks for classes.
	 */
	private static Map<Class<?>, MethodInvocationControl> classMocks = new HashMap<Class<?>, MethodInvocationControl>();

	/**
	 * Holds info about general method invocation mocks for instances.
	 */
	private static Map<Object, MethodInvocationControl> instanceMocks = new ListMap<Object, MethodInvocationControl>();

	/**
	 * Holds info about which methods that should return a substitute/another
	 * instance instead of the default instance.
	 */
	private static Map<Method, Object> substituteReturnValues = new HashMap<Method, Object>();

	/**
	 * Holds info about which methods that are proxied.
	 */
	private static Map<Method, InvocationHandler> methodProxies = new HashMap<Method, InvocationHandler>();

	/**
	 * Holds info about which class that should have their static initializers
	 * suppressed.
	 */
	private static Set<String> suppressStaticInitializers = new HashSet<String>();

	/**
	 * Sometimes mock frameworks needs to store additional state. They can do
	 * this using this key/value based approach.
	 */
	private static Map<String, Object> additionalState = new HashMap<String, Object>();

	/**
	 * Set of constructors that should be suppressed.
	 */
	private static final Set<Constructor<?>> suppressConstructor = new HashSet<Constructor<?>>();

	/**
	 * Set of methods that should be suppressed.
	 */
	private static final Set<Method> suppressMethod = new HashSet<Method>();

	/**
	 * Set of methods that should be suppressed.
	 */
	private static final Set<Field> suppressField = new HashSet<Field>();

	/**
	 * Set of field types that should always be suppressed regardless of
	 * instance.
	 */
	private static final Set<String> suppressFieldTypes = new HashSet<String>();

    /**
     * Set of runnables that will be executed after the test (method) is completed.
	 */
	private static final Set<Runnable> afterMethodRunners = new HashSet<Runnable>();

	/**
	 * Clear all state of the mock repository except for static initializers.
	 * The reason for not clearing static initializers is that when running in a
	 * suite with many tests the clear method is invoked after each test. This
	 * means that before the test that needs to suppress the static initializer
	 * has been reach the state of the MockRepository would have been wiped out.
	 * This is generally not a problem because most state will be added again
	 * but suppression of static initializers are different because this state
	 * can only be set once per class per CL. That's why we cannot remove this
	 * state.
	 */
	public synchronized static void clear() {
		newSubstitutions.clear();
		classMocks.clear();
		instanceMocks.clear();
		objectsToAutomaticallyReplayAndVerify.clear();
		additionalState.clear();
		suppressConstructor.clear();
		suppressMethod.clear();
		substituteReturnValues.clear();
		suppressField.clear();
		suppressFieldTypes.clear();
		methodProxies.clear();
        for (Runnable runnable : afterMethodRunners) {
            runnable.run();
        }
        afterMethodRunners.clear();
	}

	/**
	 * Removes an object from the MockRepository if it exists.
	 */
	public static void remove(Object mock) {
		if (mock instanceof Class<?>) {
			if (newSubstitutions.containsKey(mock)) {
				newSubstitutions.remove(mock);
			}
			if (classMocks.containsKey(mock)) {
				classMocks.remove(mock);
			}
		} else if (instanceMocks.containsKey(mock)) {
			instanceMocks.remove(mock);
		}
	}

	public static synchronized MethodInvocationControl getStaticMethodInvocationControl(Class<?> type) {
		return classMocks.get(type);
	}

	public static synchronized MethodInvocationControl putStaticMethodInvocationControl(Class<?> type, MethodInvocationControl invocationControl) {
		return classMocks.put(type, invocationControl);
	}

	public static synchronized MethodInvocationControl removeClassMethodInvocationControl(Class<?> type) {
		return classMocks.remove(type);
	}

	public static synchronized MethodInvocationControl getInstanceMethodInvocationControl(Object instance) {
		return instanceMocks.get(instance);
	}

	public static synchronized MethodInvocationControl putInstanceMethodInvocationControl(Object instance, MethodInvocationControl invocationControl) {
		return instanceMocks.put(instance, invocationControl);
	}

	public static synchronized MethodInvocationControl removeInstanceMethodInvocationControl(Class<?> type) {
		return classMocks.remove(type);
	}

	public static synchronized NewInvocationControl<?> getNewInstanceControl(Class<?> type) {
		return newSubstitutions.get(type);
	}

	public static synchronized NewInvocationControl<?> putNewInstanceControl(Class<?> type, NewInvocationControl<?> control) {
		return newSubstitutions.put(type, control);
	}

	/**
	 * Add a fully qualified class name for a class that should have its static
	 * initializers suppressed.
	 *
	 * @param className
	 *            The fully qualified class name for a class that should have
	 *            its static initializers suppressed.
	 */
	public static synchronized void addSuppressStaticInitializer(String className) {
		suppressStaticInitializers.add(className);
	}

	/**
	 * Remove a fully qualified class name for a class that should no longer
	 * have its static initializers suppressed.
	 *
	 * @param className
	 *            The fully qualified class name for a class that should no
	 *            longer have its static initializers suppressed.
	 */
	public static synchronized void removeSuppressStaticInitializer(String className) {
		suppressStaticInitializers.remove(className);
	}

	/**
	 * Check whether or not a class with the fully qualified name should have
	 * its static initializers suppressed.
	 *
	 * @param className
	 *            {@code true} if class with the fully qualified name
	 *            {@code className} should have its static initializers
	 *            suppressed, {@code false} otherwise.
	 */
	public static synchronized boolean shouldSuppressStaticInitializerFor(String className) {
		return suppressStaticInitializers.contains(className);
	}

	/**
	 * @return All classes that should be automatically replayed or verified.
	 */
	public static synchronized Set<Object> getObjectsToAutomaticallyReplayAndVerify() {
		return Collections.unmodifiableSet(objectsToAutomaticallyReplayAndVerify);
	}

	/**
	 * Add classes that should be automatically replayed or verified.
	 */
	public static synchronized void addObjectsToAutomaticallyReplayAndVerify(Object... objects) {
		Collections.addAll(objectsToAutomaticallyReplayAndVerify, objects);
	}

	/**
	 * When a mock framework API needs to store additional state not applicable
	 * for the other methods, it may use this method to do so.
	 *
	 * @param key
	 *            The key under which the <tt>value</tt> is stored.
	 * @param value
	 *            The value to store under the specified <tt>key</tt>.
	 * @return The previous object under the specified <tt>key</tt> or
	 *         {@code null}.
	 */
	public static synchronized Object putAdditionalState(String key, Object value) {
		return additionalState.put(key, value);
	}

	public static synchronized Object removeAdditionalState(String key) {
		return additionalState.remove(key);
	}

	public static synchronized InvocationHandler removeMethodProxy(Method method) {
		for (Method proxiedMethod : methodProxies.keySet()) {
			Class<?> proxiedMethodClass = proxiedMethod.getDeclaringClass();
			if (proxiedMethod.getName().equals(method.getName())
					&& ClassLocator.getCallerClass().getName()
					.equals(proxiedMethodClass.getName())) {
				return methodProxies.remove(proxiedMethod);
			}
		}
		return null;
	}

	/**
	 * Retrieve state based on the supplied key.
	 */
	@SuppressWarnings("unchecked")
	public static synchronized <T> T getAdditionalState(String key) {
		return (T) additionalState.get(key);
	}

	/**
	 * Add a method to suppress.
	 *
	 * @param method
	 *            The method to suppress.
	 */
	public static synchronized void addMethodToSuppress(Method method) {
		suppressMethod.add(method);
	}

	/**
	 * Add a field to suppress.
	 *
	 * @param field
	 *            The field to suppress.
	 */
	public static synchronized void addFieldToSuppress(Field field) {
		suppressField.add(field);
	}

	/**
	 * Add a field type to suppress. All fields of this type will be suppressed.
	 *
	 * @param fieldType
	 *            The fully-qualified name to a type. All fields of this type
	 *            will be suppressed.
	 */
	public static synchronized void addFieldTypeToSuppress(String fieldType) {
		suppressFieldTypes.add(fieldType);
	}

	/**
	 * Add a constructor to suppress.
	 *
	 * @param constructor
	 *            The constructor to suppress.
	 */
	public static synchronized void addConstructorToSuppress(Constructor<?> constructor) {
		suppressConstructor.add(constructor);
	}

	/**
	 * @return {@code true} if the <tt>method</tt> should be proxied.
	 */
	public static synchronized boolean hasMethodProxy(Method method) {
		for (Method proxiedMethod : methodProxies.keySet()) {
			Class<?> proxiedMethodClass = proxiedMethod.getDeclaringClass();
			if (proxiedMethod.getName().equals(method.getName())
					&& ClassLocator.getCallerClass().getName()
					.equals(proxiedMethodClass.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return {@code true} if the <tt>method</tt> should be suppressed.
	 */
	public static synchronized boolean shouldSuppressMethod(Method method,
			Class<?> objectType) throws ClassNotFoundException {
		for (Method suppressedMethod : suppressMethod) {
			Class<?> suppressedMethodClass = suppressedMethod
					.getDeclaringClass();
			if (suppressedMethodClass.getClass().isAssignableFrom(
					objectType.getClass())
					&& suppressedMethod.getName().equals(method.getName())
					&& ClassLocator.getCallerClass().getName()
							.equals(suppressedMethodClass.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return {@code true} if the <tt>field</tt> should be suppressed.
	 */
	public static synchronized boolean shouldSuppressField(Field field) {
		return suppressField.contains(field) || suppressFieldTypes.contains(field.getType().getName());
	}

	/**
	 * @return {@code true} if the <tt>constructor</tt> should be
	 *         suppressed.
	 */
	public static synchronized boolean shouldSuppressConstructor(Constructor<?> constructor) {
		return suppressConstructor.contains(constructor);
	}

	/**
	 * @return {@code true} if the <tt>method</tt> has a substitute return
	 *         value.
	 */
	public static synchronized boolean shouldStubMethod(Method method) {
		return substituteReturnValues.containsKey(method);
	}

	/**
	 * @return The substitute return value for a particular method, may be
	 *         {@code null}.
	 */
	public static synchronized Object getMethodToStub(Method method) {
		return substituteReturnValues.get(method);
	}

	/**
	 * Set a substitute return value for a method. Whenever this method will be
	 * called the {@code value} will be returned instead.
	 *
	 * @return The previous substitute value if any.
	 */
	public static synchronized Object putMethodToStub(Method method, Object value) {
		return substituteReturnValues.put(method, value);
	}

	/**
	 * @return The proxy for a particular method, may be {@code null}.
	 */
	public static synchronized InvocationHandler getMethodProxy(Method method) {
		return methodProxies.get(method);
	}

	/**
	 * Set a proxy for a method. Whenever this method is called the invocation
	 * handler will be invoked instead.
	 *
	 * @return The method proxy if any.
	 */
	public static synchronized InvocationHandler putMethodProxy(Method method, InvocationHandler invocationHandler) {
		return methodProxies.put(method, invocationHandler);
	}

    /**
     * Add a {@link Runnable} that will be executed after each test
     * @param runnable - an instance of {@link Runnable} that will be executed.
     */
    public static synchronized void addAfterMethodRunner(Runnable runnable) {
        afterMethodRunners.add(runnable);
    }
}
