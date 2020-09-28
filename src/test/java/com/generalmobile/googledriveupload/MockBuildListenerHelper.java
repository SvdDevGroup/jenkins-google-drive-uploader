/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.common.base.Joiner;
import hudson.model.BuildListener;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockBuildListenerHelper {

    private static final transient Logger logger = LoggerFactory.getLogger(MockBuildListenerHelper.class);
    ByteArrayOutputStream loggerOutput = new ByteArrayOutputStream();
    Queue<Object[]> errors = new LinkedList<>();

    public BuildListener createMockBuildListener() {
        BuildListener mockBuildListener = mock(BuildListener.class);
        when(mockBuildListener.getLogger()).thenReturn(createDelegationPrintStream());
        when(mockBuildListener.error(any())).thenAnswer(errorMockAnswer());
        return mockBuildListener;
    }

    private PrintStream createDelegationPrintStream() {
        return new PrintStream(new OutputStream() {
            private StringBuffer mem = new StringBuffer();

            @Override
            public void write(final int b) {
                loggerOutput.write(b);
                if ((char) b == '\n') {
                    logger.info(mem.toString());
                    mem = new StringBuffer();
                    return;
                }
                mem = mem.append((char) b);
            }
        });
    }

    private Answer<PrintWriter> errorMockAnswer() {
        return new Answer<PrintWriter>() {
            @Override
            public PrintWriter answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                errors.add(args);
                return mock(PrintWriter.class);
            }
        };
    }

    public void assertLoggingEqual(final String... expectedlogLines) {
        assertEquals(Joiner.on("\n").join(expectedlogLines) + "\n", loggerOutput.toString());
    }

    public void assertNoErrors() {
        assertTrue(errors.isEmpty());
    }

    public void assertErrors(final String... expectedErrors) {
        assertEquals(Joiner.on("\n").join(expectedErrors), Joiner.on("\n").join(errors.remove()));
    }
}
