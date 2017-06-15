/*
 * This file is part of FastClasspathScanner.
 * 
 * Author: Harith Elrufaie
 * 
 * Hosted at: https://github.com/lukehutch/fast-classpath-scanner
 * 
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Harith Elrufaie
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.lukehutch.fastclasspathscanner.classloaderhandler;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import io.github.lukehutch.fastclasspathscanner.scanner.ClasspathFinder;
import io.github.lukehutch.fastclasspathscanner.utils.LogNode;
import io.github.lukehutch.fastclasspathscanner.utils.ReflectionUtils;

/**
 * Custom Class Loader Handler for OSGi Felix ClassLoader.
 *
 * The handler adds the bundle jar and all assocaited Bundle-Claspath jars into the {@link ClasspathFinder} scan
 * classpath.
 *
 * @author elrufaie
 */
public class FelixClassLoaderHandler implements ClassLoaderHandler {

    private final String JAR_FILE_PREFIX = "jar:";

    private final String JAR_FILE_DELIM = "!/";

    private static final String BY_COMMA = ",";

    @Override
    public boolean handle(final ClassLoader classLoader, final ClasspathFinder classpathFinder, final LogNode log)
            throws Exception {
        final List<ClassLoader> classLoaders = Arrays.asList(classLoader);
        for (Class<?> c = classLoader.getClass(); c != null; c = c.getSuperclass()) {
            if ("org.apache.felix.framework.BundleWiringImpl$BundleClassLoaderJava5".equals(c.getName()) ||
                "org.apache.felix.framework.BundleWiringImpl$BundleClassLoader".equals(c.getName())) {
                // Type: BundleImpl
                final Object bundle = ReflectionUtils.invokeMethod(classLoader, "getBundle");
                // Bundles are backed by a BundleArchive
                final Object bundleArchive = ReflectionUtils.invokeMethod(bundle, "getArchive");
                // Which contain one or more BundleArchiveRevision (we want the current one)
                final Object bundleArchiveRevision = ReflectionUtils.invokeMethod(bundleArchive,"getCurrentRevision");
                // Get the contents (JarContent)
                final Object bundleContent = ReflectionUtils.invokeMethod(bundleArchiveRevision,"getContent");
                // Which we add to our element list
                classpathFinder.addClasspathElement(getContentLocation(bundleContent), classLoaders, log);

                // Now deal with any embedded content
                final List embeddedContent = (List)ReflectionUtils.invokeMethod(bundleArchiveRevision,"getContentPath");
                if (embeddedContent != null) {
                    for (Object content : embeddedContent) {
                        classpathFinder.addClasspathElement(getContentLocation(content), classLoaders, log);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private String getContentLocation(Object content) throws Exception {
        final File file = (File)ReflectionUtils.invokeMethod(content,"getFile");
        return file != null ? file.toURI().toString() : null;
    }
}