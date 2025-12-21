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

import java.lang.reflect.Member;

/**
 * A filter which determines the subset of the members of a class (fields and methods) for which
 * attributes should be generated.
 * <p/>
 * This can be supplied to {@link AttributeSourceGenerator} or {@link AttributeBytecodeGenerator}.
 *
 * @see MemberFilters
 */
public interface MemberFilter {

    boolean accept(Member member);
}
