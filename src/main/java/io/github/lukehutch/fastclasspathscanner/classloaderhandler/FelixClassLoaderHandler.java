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
                final Object m_wiring = ReflectionUtils.getFieldVal(classLoader, "m_wiring");
                // Type: Bundle
                final Object bundle = ReflectionUtils.invokeMethod(m_wiring, "getBundle");

                @SuppressWarnings("unchecked")
                final Map<String, Map<?, ?>> bundleHeaders = (Map<String, Map<?, ?>>) ReflectionUtils
                        .getFieldVal(bundle, "m_cachedHeaders");

                // Bundles are backed by a BundleArchive
                final Object bundleArchive = ReflectionUtils.getFieldVal(bundle, "m_archive");
                // Which contain one or more BundleArchiveRevision (we want the current one)
                final Object bundleArchiveRevision = ReflectionUtils.invokeMethod(bundleArchive,"getCurrentRevision");
                // Get the contents (JarContent)
                final Object bundleContent = ReflectionUtils.invokeMethod(bundleArchiveRevision,"getContent");
                // Finally get the underlying File object
                final File bundleArchiveFile = (File)ReflectionUtils.invokeMethod(bundleContent,"getFile");

                if (bundleHeaders != null && !bundleHeaders.isEmpty()) {
                    // Add bundleFile
                    final String bundleFile = bundleArchiveFile.toURI().toString();
                    classpathFinder.addClasspathElement(bundleFile, classLoaders, log);

                    // Should find one element only
                    final Iterator<Entry<String, Map<?, ?>>> it = bundleHeaders.entrySet().iterator();
                    if (it.hasNext()) {
                        final Entry<String, Map<?, ?>> pair = it.next();
                        final Map<?, ?> stringMap = pair.getValue();
                        // Type: String
                        final String classpath = (String) stringMap.get("Bundle-Classpath");
                        // If we have multiple jars in classpath, add them all
                        if (classpath != null) {
                            final String[] splitJars = classpath.split(BY_COMMA);
                            for (int i = 0; i < splitJars.length; i++) {
                                // Should be something like this:
                                // jar:file:/path/myBundleFile.jar!/v4-sdk-schema-1.4.0-develop.v12.jar
                                if (!splitJars[i].isEmpty()) {
                                    final String jarPath = JAR_FILE_PREFIX + bundleFile + JAR_FILE_DELIM
                                            + splitJars[i];
                                    classpathFinder.addClasspathElement(jarPath, classLoaders, log);
                                }
                            }
                        } else {
                            classpathFinder.addClasspathElement(bundleFile.replace("reference:", JAR_FILE_PREFIX),
                                    classLoaders, log);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
}