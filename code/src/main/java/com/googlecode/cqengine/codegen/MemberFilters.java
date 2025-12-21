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
package com.googlecode.cqengine.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Provides some general purpose {@link MemberFilter}s which can be used with {@link AttributeSourceGenerator} or
 * {@link AttributeBytecodeGenerator}.
 */
public class MemberFilters {

    enum GetterPrefix { get, is, has }

    /**
     * A filter which matches all members (both fields and methods).
     */
    public static final MemberFilter ALL_MEMBERS = member -> true;

    /**
     * A filter which matches all fields.
     */
    public static final MemberFilter FIELDS_ONLY = member -> member instanceof Field;

    /**
     * A filter which matches all methods.
     */
    public static final MemberFilter METHODS_ONLY = member -> member instanceof Method;

    /**
     * A filter which matches all methods which start with "get", "is" and "has" and where the following character
     * is in uppercase.
     */
    public static final MemberFilter GETTER_METHODS_ONLY = member -> {
        if (member instanceof Method) {
            for (GetterPrefix prefix : GetterPrefix.values()) {
                if (hasGetterPrefix(member.getName(), prefix.name())) {
                    return true;
                }
            }
        }
        return false;
    };

    static boolean hasGetterPrefix(String memberName, String prefix) {
        int prefixLength = prefix.length();
        return memberName.length() > prefixLength
                && memberName.startsWith(prefix)
                && Character.isUpperCase(memberName.charAt(prefixLength));
    }

    /**
     * Private constructor, not used.
     */
    MemberFilters() {
    }
}
