/*
 * Copyright 2025 Saif Asif
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.cqengine.query.simple;

import static com.googlecode.cqengine.query.QueryFactory.isPrefixOf;
import static com.googlecode.cqengine.query.QueryFactory.noQueryOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SelfAttribute;


public class StringIsPrefixOfTest {

    Attribute<String, String> stringIdentity = new SelfAttribute<String>(String.class, "identity");
    
    @Test
    public void testMatchesSimpleAttribute() throws Exception {
        
        assertTrue(isPrefixOf(stringIdentity, "FOO").matches("F", noQueryOptions()));
        assertTrue(isPrefixOf(stringIdentity, "FOO").matches("FO", noQueryOptions()));
        assertTrue(isPrefixOf(stringIdentity, "FOO").matches("FOO", noQueryOptions()));
        
        assertFalse(isPrefixOf(stringIdentity, "FOO").matches("OO", noQueryOptions()));
        assertFalse(isPrefixOf(stringIdentity, "FOO").matches("BOO", noQueryOptions()));
        assertFalse(isPrefixOf(stringIdentity, "FOO").matches("FOOOD", noQueryOptions()));
        
    }

    
    @Test
    public void testGetValue() throws Exception {
        StringIsPrefixOf<String, String> query = new StringIsPrefixOf<>(stringIdentity, "FOO");
        assertEquals("FOO", query.getValue());
    }

}
