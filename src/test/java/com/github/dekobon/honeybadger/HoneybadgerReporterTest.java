package com.github.dekobon.honeybadger;

import com.google.common.collect.ImmutableList;
import org.apache.http.HttpHeaders;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import static com.github.dekobon.honeybadger.HoneybadgerReporter.*;
import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HoneybadgerReporterTest {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final HoneybadgerReporter reporter;

    public HoneybadgerReporterTest() {
        if (System.getProperty(HONEYBADGER_API_KEY_SYS_PROP_KEY) == null) {
            throw new IllegalArgumentException(HONEYBADGER_API_KEY_SYS_PROP_KEY +
            " system property must be specified");
        }

        System.setProperty("ENV", "test");
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort", "8888");
        System.setProperty(HONEYBADGER_URL_SYS_PROP_KEY,
                "http://api.honeybadger.io/v1/notices");

        System.setProperty(HONEYBADGER_EXCLUDED_CLASSES_SYS_PROP_KEY,
                String.format("%s,%s",
                    UnsupportedOperationException.class.getName(),
                    IllegalArgumentException.class.getName()));

        reporter = new HoneybadgerReporter();
    }

    @Test
    public void willReportErrorWithRequest() throws Exception {
        MDC.put("testValue", "something");

        Throwable cause = new RuntimeException("I'm the cause");
        Throwable t = new RuntimeException("Test exception " +
                System.currentTimeMillis(), cause);
        HashMap<String, String> params = new HashMap<>();
        params.put("url", "http://foo.com");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("http://www.foobar.com");
        when(request.getMethod()).thenReturn("GET");
        when(request.getServerName()).thenReturn("tatsumaki.local");
        when(request.getServerPort()).thenReturn(80);
        when(request.getContentType()).thenReturn("application/json; charset=UTF-8");

        when(request.getHeaderNames()).thenReturn(
                EnumerationWrapper.of(
                        HttpHeaders.REFERER,
                        HttpHeaders.USER_AGENT,
                        HttpHeaders.ACCEPT));

        when(request.getHeader(HttpHeaders.REFERER)).thenReturn("Tester");
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("User Agent");
        when(request.getHeader(HttpHeaders.ACCEPT)).thenReturn("application/json");

        Part part = mock(Part.class);
        when(part.getName()).thenReturn("testpart");
        when(part.toString()).thenReturn("value");

        when(request.getParts()).thenReturn(of(part));

        UUID id = reporter.reportError(t, request);

        assertNotNull("Didn't send error correctly to Honeybadger API", id);

        logger.info("Created error with id: {}", id);
    }

    @Test
    public void willSuppressExcludedExceptionClasses() {
        final Exception error = new UnsupportedOperationException(
                "I should be suppressed");
        final UUID id = reporter.reportError(error);

        assertNull("A suppressed error was actually added", id);
    }

    static class EnumerationWrapper<E> implements Enumeration<E> {
        final Iterator<E> itr;

        EnumerationWrapper(Iterator<E> backing) {
            this.itr = backing;
        }

        EnumerationWrapper(Iterable<E> backing) {
            this.itr = backing.iterator();
        }

        public static <E> EnumerationWrapper<E> of(E... items) {
            return new EnumerationWrapper<>(ImmutableList.copyOf(items).iterator());
        }

        @Override
        public boolean hasMoreElements() {
            return itr.hasNext();
        }

        @Override
        public E nextElement() {
            return itr.next();
        }
    }
}