/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.opensolaris.opengrok.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.Ctags;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.analysis.Scopes.Scope;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.TestRepository;

/**
 *
 * @author Tomas Kotal
 */
public class JavaAnalyzerFactoryTest {

    private static Ctags ctags;
    private static TestRepository repository;
    private static FileAnalyzer analyzer;

    private static StreamSource getStreamSource(final String fname) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new FileInputStream(fname);
            }
        };
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        ctags = new Ctags();
        ctags.setBinary(RuntimeEnvironment.getInstance().getCtags());

        repository = new TestRepository();
        repository.create(JavaAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/index/source.zip"));

        JavaAnalyzerFactory analFact = new JavaAnalyzerFactory();
        analyzer = analFact.getAnalyzer();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.validateExuberantCtags()) {
            analyzer.setCtags(new Ctags());
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        ctags.close();
        ctags = null;
    }

    /**
     * Test of writeXref method, of class CAnalyzerFactory.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testScopeAnalyzer() throws Exception {
        String path = repository.getSourceRoot() + "/java/Sample.java";
        File f = new File(path);
        if (!(f.canRead() && f.isFile())) {
            fail("java testfile " + f + " not found");
        }

        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, path,
                string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.setScopesEnabled(true);
        analyzer.analyze(doc, getStreamSource(path), xrefOut);

        IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
        assertNotNull(scopesField);
        Scopes scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
        Scope globalScope = scopes.getScope(-1);
        assertEquals(5, scopes.size()); // foo, bar, main

        for (int i = 0; i < 74; ++i) {
            if (i >= 29 && i <= 31) {
                assertEquals("Sample", scopes.getScope(i).getName());
                assertEquals("Sample", scopes.getScope(i).getNamespace());
            } else if (i >= 33 && i <= 41) {
                assertEquals("Method", scopes.getScope(i).getName());
                assertEquals("Sample", scopes.getScope(i).getNamespace());
            } else if (i == 43) {
                assertEquals("AbstractMethod", scopes.getScope(i).getName());
                assertEquals("Sample", scopes.getScope(i).getNamespace());
            } else if (i >= 47 && i <= 56) {
                assertEquals("InnerMethod", scopes.getScope(i).getName());
                assertEquals("Sample.InnerClass", scopes.getScope(i).getNamespace());
            } else if (i >= 60 && i <= 72) {
                assertEquals("main", scopes.getScope(i).getName());
                assertEquals("Sample", scopes.getScope(i).getNamespace());
            } else {
                assertEquals(scopes.getScope(i), globalScope);
                assertNull(scopes.getScope(i).getNamespace());
            }
        }
    }

}
