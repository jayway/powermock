/*
 * Copyright 2013 the original author or authors.
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
package org.powermock.modules.junit4.internal.impl;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.powermock.core.spi.testresult.Result;
import org.powermock.core.spi.testresult.TestMethodResult;
import org.powermock.tests.utils.PowerMockTestNotifier;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateful class that, from information from JUnit and test-classes,
 * can build and send notifications to PowerMockTestNotifier.
 */
class NotificationBuilder {

    enum DetectedTestRunBehaviour {
        PENDING,
        START_FIRES_FIRST,
        TEST_INSTANCE_CREATED_FIRST,
        ALL_TESTINSTANCES_ARE_CREATED_FIRST,
        TEST_INSTANCES_ARE_REUSED,
        INCONSISTENT_BEHAVIOUR;
    }

    private static final Pattern methodDisplayNameRgx =
            Pattern.compile("(^[^\\(\\[]++)|([^(]+(?=\\)))");
    private final Method[] testMethods;
    private final List<?> pendingTestInstances;
    private final PowerMockTestNotifier powerMockTestNotifier;
    private DetectedTestRunBehaviour behaviour = DetectedTestRunBehaviour.PENDING;
    private Description currentDescription;
    private Object currentTestInstance;
    private String testClassName;

    private Object latestTestInstance;
    private Method latestMethod;
    private static final Object[] unsupportedMethodArgs = {};

    private final Map<Object, List<Method>> methodsPerInstance =
            new IdentityHashMap<Object, List<Method>>() {
        @Override
        public List<Method> get(Object key) {
            if (!containsKey(key)) {
                put(key, new LinkedList<Method>());
            }
            return super.get(key);
        }
    };

    private final Map<Description, OngoingTestRun> ongoingTestRuns =
            new IdentityHashMap<Description, OngoingTestRun>();

    private class OngoingTestRun implements TestMethodResult {
        final Description testDescription;
        final Object testInstance;
        final Method testMethod;
        private Result result;

        OngoingTestRun(Description testDescription, Object testInstance) {
            this.testDescription = testDescription;
            this.testInstance = testInstance;
            this.testMethod = determineTestMethod(testDescription);
            pendingTestInstances.remove(testInstance);
            Class<?> testClass = testClass();
            new MockPolicyInitializerImpl(testClass).initialize(testClass.getClassLoader());
            powerMockTestNotifier.notifyBeforeTestMethod(
                    testInstance, testMethod, unsupportedMethodArgs);
            ongoingTestRuns.put(testDescription, this);            
        }

        final Class<?> testClass() {
            if (null == testClassName) {
                return testInstance.getClass();
            } else {
                try {
                    return Class.forName(testClassName, false,
                            testInstance.getClass().getClassLoader());
                } catch (ClassNotFoundException ex) {
                    return testInstance.getClass();
                }
            }
        }

        void report(Result result) {
            if (null != this.result && Result.SUCCESSFUL == result
                    || this.result == result) {
                /* Already notified - ignore this duplication */
                return;

            } else if (null != this.result) {
                new IllegalStateException(
                        "Will report an unexpected result-notification " + result
                        + " after previously received notification " + this.result)
                        .printStackTrace();
            }
            this.result = result;
            powerMockTestNotifier.notifyAfterTestMethod(
                    testInstance, testMethod, unsupportedMethodArgs, this);
        }

        @Override
        public Result getResult() {
            return this.result;
        }
    }

    public NotificationBuilder(Method[] testMethods,
            PowerMockTestNotifier notifier,
            List<?> pendingTestInstances) {
        this.testMethods = testMethods;
        this.pendingTestInstances = pendingTestInstances;
        this.powerMockTestNotifier = notifier;
    }

    private Method determineTestMethod(Description d) {
        Matcher matchMethodName = methodDisplayNameRgx
                .matcher(d.getDisplayName());
        matchMethodName.find();
        String methodName = matchMethodName.group();
        boolean latestTestMethodCanBeRepeated = false;
        for (Method m : testMethods) {
            if (m.getName().equals(methodName)) {
                if (m == latestMethod) {
                    latestTestMethodCanBeRepeated = true;
                } else {
                    return latestMethod = m;
                }
            }
        }
        if (latestTestMethodCanBeRepeated) {
            return latestMethod;
        } else {
            new IllegalArgumentException(
                    "Unable to determine method-name from description="
                    + d + "; - ignored").printStackTrace();
            return null;
        }
    }

    private Class<?> reloadParamType(
            Class<?> testClass, Class<?> typeToReload) {
        if (typeToReload.isPrimitive()
                || testClass.getClassLoader() == typeToReload.getClassLoader()) {
            return typeToReload;

        } else if (typeToReload.isArray()) {
            Class<?> newComponentType = reloadParamType(
                    testClass, typeToReload.getComponentType());
            if (newComponentType == typeToReload.getComponentType()) {
                return typeToReload;
            } else {
                return Array.newInstance(newComponentType, 0).getClass();
            }

        } else {
            try {
                return Class.forName(typeToReload.getName(),
                        true, testClass.getClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new Error(ex);
            }
        }
    }

    private Method reloadMethod(Class<?> testClass, Method m) {
        if (testClass.getClassLoader() == m.getDeclaringClass().getClassLoader()) {
            return m;
        } else if (!m.getDeclaringClass().getName()
                .equals(testClass.getName())) {
            return reloadMethod(testClass.getSuperclass(), m);
        }
        Class[] paramTypes = m.getParameterTypes();
        for (int i = 0; i < paramTypes.length; ++i) {
            paramTypes[i] = reloadParamType(testClass, paramTypes[i]);
        }
        try {
            return testClass.getDeclaredMethod(m.getName(), paramTypes);
        } catch (NoSuchMethodException ex) {
            throw new Error(ex);
        }
    }

    void testSuiteStarted(Class<?> testClass) {
        for (int i = 0; i < testMethods.length; ++i) {
            testMethods[i] = reloadMethod(testClass, testMethods[i]);
        }
        powerMockTestNotifier.notifyBeforeTestSuiteStarted(testClass, testMethods);
        this.testClassName = testClass.getName();
    }

    void testStartHasBeenFired(Description d) {
        OngoingTestRun oldTestRun = ongoingTestRuns.get(d);
        if (null != oldTestRun && null != oldTestRun.getResult()) {
            throw new IllegalStateException(
                    "Fired testrun is already running: " + d);
        }
        currentDescription = d;
        switch (behaviour) {
            case PENDING:
                behaviour = DetectedTestRunBehaviour.START_FIRES_FIRST;
            case START_FIRES_FIRST:
                return;
            case TEST_INSTANCE_CREATED_FIRST:
                if (currentTestInstance == latestTestInstance) {
                    behaviour = DetectedTestRunBehaviour.TEST_INSTANCES_ARE_REUSED;
                }
            case TEST_INSTANCES_ARE_REUSED:
                latestTestInstance = currentTestInstance;
                methodsPerInstance.get(currentTestInstance).add(
                        new OngoingTestRun(d, currentTestInstance).testMethod);
                return;
            case ALL_TESTINSTANCES_ARE_CREATED_FIRST:
                System.err.println(
                        "Notifications are not supported when all test-instances are created first!");
                return;
            default:
                throw new AssertionError();
        }
    }

    void testInstanceCreated(Object newTestInstance) {
        // Prevent double trigger on same instance
		if (currentTestInstance == newTestInstance) {
			return;
		}
        switch (behaviour) {
            case PENDING:
                behaviour = DetectedTestRunBehaviour.TEST_INSTANCE_CREATED_FIRST;
                currentTestInstance = newTestInstance;
                return ;
            case TEST_INSTANCE_CREATED_FIRST:
                if (methodsPerInstance.isEmpty()) {
                    behaviour = DetectedTestRunBehaviour.ALL_TESTINSTANCES_ARE_CREATED_FIRST;
                } else if (currentTestInstance == latestTestInstance) {
                    currentTestInstance = newTestInstance;
                } else {
                    behaviour = DetectedTestRunBehaviour.INCONSISTENT_BEHAVIOUR;
                }
                return;
            case ALL_TESTINSTANCES_ARE_CREATED_FIRST:
            case INCONSISTENT_BEHAVIOUR:
                System.err.println(
                        "Notifications are not supported for behaviour " + behaviour);
                return;
            case START_FIRES_FIRST:
                currentTestInstance = latestTestInstance = newTestInstance;
                latestMethod = determineTestMethod(currentDescription);
                methodsPerInstance.get(newTestInstance).add(
                        new OngoingTestRun(currentDescription, newTestInstance).testMethod);
                return;
            default:
                throw new AssertionError("Unknown behaviour: " + behaviour);
        }
    }

    void testIgnored(Description d) {
        if (!notify(d, Result.IGNORED)
                && DetectedTestRunBehaviour.TEST_INSTANCE_CREATED_FIRST == behaviour
                && currentTestInstance != latestTestInstance) {
            /*
             * Workaround for some bad behaviour in JUnit-4.4 default runner, 
             * which creates a test-instance first, even for a test that is ignored!!
             */
            currentTestInstance = latestTestInstance;
        }
    }

    void assumptionFailed(Description d) {
        notify(d, Result.IGNORED);
    }

    void failure(Failure f) {
        notify(f.getDescription(), Result.FAILED);
    }

    void testFinished(Description d) {
        notify(d, Result.SUCCESSFUL);
    }

    /**
     * @return true if notification concerns an ongoing testrun; otherwise false
     *         when there is no test launched for the specified description
     */
    private boolean notify(Description d, Result result) {
        OngoingTestRun testRun = ongoingTestRuns.get(d);
        if (null == testRun) {
//            System.err.println("Notification not enabled for " + d);
            return false;
        } else {
            testRun.report(result);
            return true;
        }
    }
}
