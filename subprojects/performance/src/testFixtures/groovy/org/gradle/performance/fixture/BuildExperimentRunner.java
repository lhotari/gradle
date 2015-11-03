/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.fixture;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BuildExperimentRunner {
    private final GCLoggingCollector gcCollector = new GCLoggingCollector();
    private final DataCollector dataCollector;
    private final GradleSessionProvider executerProvider;
    private final OperationTimer timer = new OperationTimer();

    public enum Phase {
        WARMUP,
        MEASUREMENT
    }

    public BuildExperimentRunner(GradleSessionProvider executerProvider) {
        this.executerProvider = executerProvider;
        MemoryInfoCollector memoryInfoCollector = new MemoryInfoCollector();
        memoryInfoCollector.setOutputFileName("build/totalMemoryUsed.txt");
        BuildEventTimestampCollector buildEventTimestampCollector = new BuildEventTimestampCollector("build/buildEventTimestamps.txt");
        dataCollector = new CompositeDataCollector(memoryInfoCollector, gcCollector, buildEventTimestampCollector);
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        File workingDirectory = experiment.getInvocation().getWorkingDirectory();
        final List<String> additionalJvmOpts = dataCollector.getAdditionalJvmOpts(workingDirectory);
        final List<String> additionalArgs = new ArrayList<String>(dataCollector.getAdditionalArgs(workingDirectory));
        additionalArgs.add("-PtestDisplayName=" + experiment.getDisplayName());

        GradleInvocationSpec buildSpec = experiment.getInvocation().withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
        GradleSession session = executerProvider.session(buildSpec);

        session.prepare();
        try {
            for (int i = 0; i < experiment.getWarmUpCount(); i++) {
                System.out.println();
                System.out.println(String.format("Warm-up #%s", i + 1));
                runOnce(session, experiment, new MeasuredOperationList(), Phase.WARMUP, i + 1, experiment.getWarmUpCount());
            }
            waitForMillis(experiment.getSleepAfterWarmUpMillis());
            for (int i = 0; i < experiment.getInvocationCount(); i++) {
                if (i > 0) {
                    waitForMillis(experiment.getSleepAfterTestRoundMillis());
                }
                System.out.println();
                System.out.println(String.format("Test run #%s", i + 1));
                runOnce(session, experiment, results, Phase.MEASUREMENT, i + 1, experiment.getInvocationCount());
            }
        } finally {
            session.cleanup();
        }
    }

    // the JIT compiler seems to wait for idle period before compiling
    private void waitForMillis(Long sleepTimeMillis) {
        if (sleepTimeMillis > 0L) {
            System.out.println();
            System.out.println(String.format("Waiting %d ms", sleepTimeMillis));
            try {
                Thread.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private void runOnce(final GradleSession session, final BuildExperimentSpec experiment, MeasuredOperationList results, final Phase phase, final int loopNumber, final int loopMax) {
        final BuildExperimentInvocationInfo invocationInfo = new BuildExperimentInvocationInfo() {
            @Override
            public BuildExperimentSpec getBuildExperimentSpec() {
                return experiment;
            }

            @Override
            public Phase getPhase() {
                return phase;
            }

            @Override
            public int getLoopNumber() {
                return loopNumber;
            }

            @Override
            public int getLoopMax() {
                return loopMax;
            }
        };

        final Runnable runner = session.runner(new GradleInvocationCustomizer() {
            @Override
            public GradleInvocationSpec customize(GradleInvocationSpec invocationSpec) {
                GradleInvocationSpec customizedInvocation = invocationSpec.withAdditionalArgs(createTestLoopInfoArguments(phase, loopNumber, loopMax));
                if (experiment.getListener() != null) {
                    GradleInvocationCustomizer customizer = experiment.getListener().createInvocationCustomizer(invocationInfo);
                    if (customizer != null) {
                        customizedInvocation = customizer.customize(customizedInvocation);
                    }
                }
                return customizedInvocation;
            }
        });

        if (experiment.getListener() != null) {
            experiment.getListener().beforeInvocation(invocationInfo);
        }

        MeasuredOperation operation = timer.measure(new Action<MeasuredOperation>() {
            @Override
            public void execute(MeasuredOperation measuredOperation) {
                runner.run();
            }
        });

        if (experiment.getListener() != null) {
            experiment.getListener().afterInvocation(invocationInfo, operation);
        }

        if (operation.getException() == null) {
            dataCollector.collect(session.getInvocation().getWorkingDirectory(), operation);
        }

        results.add(operation);
    }


    private List<String> createTestLoopInfoArguments(Phase phase, int loopNumber, int loopMax) {
        List<String> args = new ArrayList<String>(3);
        args.add("-PtestPhase=" + phase.toString().toLowerCase());
        args.add("-PtestLoopNumber=" + loopNumber);
        args.add("-PtestLoopMax=" + loopMax);
        return args;
    }
}
