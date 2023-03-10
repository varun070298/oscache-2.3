/*
 * Copyright (c) 2002-2003 by OpenSymphony
 * All rights reserved.
 */
package com.opensymphony.oscache.web.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * CacheServletResponse is a serialized representation of a response
 *
 * @author  <a href="mailto:sergek@lokitech.com">Serge Knystautas</a>
 * @version $Revision: 1.6 $
 */
public class CacheHttpServletResponseWrapper extends HttpServletResponseWrapper {
    private final Log log = LogFactory.getLog(this.getClass());

    /**
     * We cache the printWriter so we can maintain a single instance
     * of it no matter how many times it is requested.
     */
    private PrintWriter cachedWriter = null;
    private ResponseContent result = null;
    private SplitServletOutputStream cacheOut = null;
    private boolean fragment = false;
    private int status = SC_OK;
    private long expires = CacheFilter.EXPIRES_ON;
    private long lastModified = CacheFilter.LAST_MODIFIED_INITIAL;

    /**
     * Constructor
     *
     * @param response The servlet response
     */
    public CacheHttpServletResponseWrapper(HttpServletResponse response) {
        this(response, false, Long.MAX_VALUE, CacheFilter.EXPIRES_ON, CacheFilter.LAST_MODIFIED_INITIAL);
    }

    /**
     * Constructor
     *
     * @param response The servlet response
     * @param fragment true if the repsonse indicates that it is a fragement of a page
     * @param time the refresh time in millis
     * @param lastModified defines if last modified header will be send, @see CacheFilter
     * @param expires defines if expires header will be send, @see CacheFilter
     */
    public CacheHttpServletResponseWrapper(HttpServletResponse response, boolean fragment, long time, long lastModified, long expires) {
        super(response);
        result = new ResponseContent();
        this.fragment = fragment;
        this.expires = expires;
        this.lastModified = lastModified;
        
        // only set inital values for last modified and expires, when a complete page is cached
        if (!fragment) {
            // setting a default last modified value based on object creation and remove the millis
            if (lastModified == CacheFilter.LAST_MODIFIED_INITIAL) {
                long current = System.currentTimeMillis() / 1000;
                result.setLastModified(current * 1000);
                super.setDateHeader(CacheFilter.HEADER_LAST_MODIFIED, result.getLastModified());
            }
            // setting the expires value
            if (expires == CacheFilter.EXPIRES_TIME) {
                result.setExpires(result.getLastModified() + time);
                super.setDateHeader(CacheFilter.HEADER_EXPIRES, result.getExpires());
            }
        }
    }

    /**
     * Get a response content
     *
     * @return The content
     */
    public ResponseContent getContent() {
        //Create the byte array
        result.commit();

        //Return the result from this response
        return result;
    }

    /**
     * Set the content type
     *
     * @param value The content type
     */
    public void setContentType(String value) {
        if (log.isDebugEnabled()) {
            log.debug("ContentType: " + value);
        }

        super.setContentType(value);
        result.setContentType(value);
    }

    /**
     * Set the date of a header
     *
     * @param name The header name
     * @param value The date
     */
    public void setDateHeader(String name, long value) {
        if (log.isDebugEnabled()) {
            log.debug("dateheader: " + name + ": " + value);
        }

        // only set the last modified value, if a complete page is cached
        if ((lastModified != CacheFilter.LAST_MODIFIED_OFF) && (CacheFilter.HEADER_LAST_MODIFIED.equalsIgnoreCase(name))) {
            if (!fragment) {
                result.setLastModified(value);
            } // TODO should we return now by fragments to avoid putting the header to the response?
        } 

        // implement RFC 2616 14.21 Expires (without max-age)
        if ((expires != CacheFilter.EXPIRES_OFF) && (CacheFilter.HEADER_EXPIRES.equalsIgnoreCase(name))) {
            if (!fragment) {
                result.setExpires(value);
            } // TODO should we return now by fragments to avoid putting the header to the response?
        } 

        super.setDateHeader(name, value);
    }

    /**
     * Add the date of a header
     *
     * @param name The header name
     * @param value The date
     */
    public void addDateHeader(String name, long value) {
        if (log.isDebugEnabled()) {
            log.debug("dateheader: " + name + ": " + value);
        }

        // only set the last modified value, if a complete page is cached
        if ((lastModified != CacheFilter.LAST_MODIFIED_OFF) && (CacheFilter.HEADER_LAST_MODIFIED.equalsIgnoreCase(name))) {
            if (!fragment) {
                result.setLastModified(value);
            } // TODO should we return now by fragments to avoid putting the header to the response?
        } 

        // implement RFC 2616 14.21 Expires (without max-age)
        if ((expires != CacheFilter.EXPIRES_OFF) && (CacheFilter.HEADER_EXPIRES.equalsIgnoreCase(name))) {
            if (!fragment) {
                result.setExpires(value);
            } // TODO should we return now by fragments to avoid putting the header to the response?
        } 

        super.addDateHeader(name, value);
    }

    /**
     * Set a header field
     *
     * @param name The header name
     * @param value The header value
     */
    public void setHeader(String name, String value) {
        if (log.isDebugEnabled()) {
            log.debug("header: " + name + ": " + value);
        }

        if (CacheFilter.HEADER_CONTENT_TYPE.equalsIgnoreCase(name)) {
            result.setContentType(value);
        }

        if (CacheFilter.HEADER_CONTENT_ENCODING.equalsIgnoreCase(name)) {
            result.setContentEncoding(value);
        }

        super.setHeader(name, value);
    }

    /**
     * Add a header field
     *
     * @param name The header name
     * @param value The header value
     */
    public void addHeader(String name, String value) {
        if (log.isDebugEnabled()) {
            log.debug("header: " + name + ": " + value);
        }

        if (CacheFilter.HEADER_CONTENT_TYPE.equalsIgnoreCase(name)) {
            result.setContentType(value);
        }

        if (CacheFilter.HEADER_CONTENT_ENCODING.equalsIgnoreCase(name)) {
            result.setContentEncoding(value);
        }

        super.addHeader(name, value);
    }

    /**
     * Set the int value of the header
     *
     * @param name The header name
     * @param value The int value
     */
    public void setIntHeader(String name, int value) {
        if (log.isDebugEnabled()) {
            log.debug("intheader: " + name + ": " + value);
        }

        super.setIntHeader(name, value);
    }

    /**
     * We override this so we can catch the response status. Only
     * responses with a status of 200 (<code>SC_OK</code>) will
     * be cached.
     */
    public void setStatus(int status) {
        super.setStatus(status);
        this.status = status;
    }

    /**
     * We override this so we can catch the response status. Only
     * responses with a status of 200 (<code>SC_OK</code>) will
     * be cached.
     */
    public void sendError(int status, String string) throws IOException {
        super.sendError(status, string);
        this.status = status;
    }

    /**
     * We override this so we can catch the response status. Only
     * responses with a status of 200 (<code>SC_OK</code>) will
     * be cached.
     */
    public void sendError(int status) throws IOException {
        super.sendError(status);
        this.status = status;
    }

    /**
     * We override this so we can catch the response status. Only
     * responses with a status of 200 (<code>SC_OK</code>) will
     * be cached.
     */
    public void setStatus(int status, String string) {
        super.setStatus(status, string);
        this.status = status;
    }

    /**
     * We override this so we can catch the response status. Only
     * responses with a status of 200 (<code>SC_OK</code>) will
     * be cached.
     */
    public void sendRedirect(String location) throws IOException {
        this.status = SC_MOVED_TEMPORARILY;
        super.sendRedirect(location);
    }

    /**
     * Retrieves the captured HttpResponse status.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Set the locale
     *
     * @param value The locale
     */
    public void setLocale(Locale value) {
        super.setLocale(value);
        result.setLocale(value);
    }

    /**
     * Get an output stream
     *
     * @throws IOException
     */
    public ServletOutputStream getOutputStream() throws IOException {
        // Pass this faked servlet output stream that captures what is sent
        if (cacheOut == null) {
            cacheOut = new SplitServletOutputStream(result.getOutputStream(), super.getOutputStream());
        }

        return cacheOut;
    }

    /**
     * Get a print writer
     *
     * @throws IOException
     */
    public PrintWriter getWriter() throws IOException {
        if (cachedWriter == null) {
            String encoding = getCharacterEncoding();
            if (encoding != null) {
                cachedWriter = new PrintWriter(new OutputStreamWriter(getOutputStream(), encoding));
            } else { // using the default character encoding
                cachedWriter = new PrintWriter(new OutputStreamWriter(getOutputStream()));
            }
        }

        return cachedWriter;
    }

    public void flushBuffer() throws IOException {
        super.flushBuffer();

        if (cacheOut != null) {
            cacheOut.flush();
        }

        if (cachedWriter != null) {
            cachedWriter.flush();
        }
    }

    /**
     * @see javax.servlet.ServletResponseWrapper#isCommitted()
     */
    public boolean isCommitted() {
        return super.isCommitted() || (result.getOutputStream() == null);
    }

    /**
     * @see javax.servlet.ServletResponseWrapper#reset()
     */
    public void reset() {
        if (!isCommitted()) {
            super.reset();
            cachedWriter = null;
            result = new ResponseContent();
            cacheOut = null;
            fragment = false;
            status = SC_OK;
            expires = CacheFilter.EXPIRES_ON;
            lastModified = CacheFilter.LAST_MODIFIED_INITIAL;
        } else {
            throw new IllegalStateException("Can't reset CacheHttpServletResponseWrapper, because it's already committed!");
        }
    }

    /**
     * @see javax.servlet.ServletResponseWrapper#resetBuffer()
     */
    public void resetBuffer() {
        if (!isCommitted()) {
            super.resetBuffer();
            cachedWriter = null;
            result = new ResponseContent();
            cacheOut = null;
            fragment = false;
            // The resetBuffer method clears content in the buffer if the
            // response is not committed without clearing the headers and status code.
        } else {
            throw new IllegalStateException("Can't reset buffer CacheHttpServletResponseWrapper, because it's already committed!");
        }
    }
}
