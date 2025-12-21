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
package com.googlecode.cqengine.persistence.support.serialization;

/**
 * Interface implemented by serializers.
 * The serializer for a particular object can be configured via the {@link PersistenceConfig} annotation.
 * <p>
 *     Implementations of this interface are expected to provide a constructor which takes two arguments:
 *     (Class objectType, PersistenceConfig persistenceConfig).
 * </p>
 *
 * @author npgall
 */
public interface PojoSerializer<O> {

    byte[] serialize(O object);

    O deserialize(byte[] bytes);
}
