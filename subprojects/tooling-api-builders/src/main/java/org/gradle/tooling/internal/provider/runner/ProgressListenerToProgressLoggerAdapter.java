/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;

import java.util.ArrayDeque;
import java.util.Deque;

class ProgressListenerToProgressLoggerAdapter implements ProgressListener {
    private final ProgressLoggerFactory progressLoggerFactory;
    Deque<String> stack;
    Deque<ProgressLogger> loggerStack;

    public ProgressListenerToProgressLoggerAdapter(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
        stack = new ArrayDeque<String>();
        loggerStack = new ArrayDeque<ProgressLogger>();
    }

    @Override
    public void statusChanged(ProgressEvent event) {
        String description = event.getDescription();
        if(description.equals("")) {
            loggerStack.pop().completed();
            return;
        }
        if (stack.contains(description)) {
            loggerStack.pop().completed();
            stack.pop();
            return;
        }
        stack.push(description);
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(ProgressListenerToProgressLoggerAdapter.class);
        progressLogger.start(description, description);
        loggerStack.push(progressLogger);
    }
}
