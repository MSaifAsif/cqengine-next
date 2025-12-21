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

import java.lang.annotation.*;

/**
 * An annotation which can be added to POJO classes to customize persistence behavior.
 *
 * @author npgall
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PersistenceConfig {

    /**
     * The {@link PojoSerializer} implementation to use.
     * <p>
     *     The default is {@link KryoSerializer}. Note that the behaviour of that serializer
     *     is highly customizable via annotations itself, including the ability to configure
     *     it to use Java's built-in serialization. See
     *     <a href="https://github.com/EsotericSoftware/kryo">Kryo</a> for details.
     * </p>
     */
    Class<? extends PojoSerializer> serializer() default KryoSerializer.class;

    /**
     * If true, causes CQEngine to persist the name of the class with every object,
     * to allow the collection to contain a mix of object types within an inheritance hierarchy.
     *
     * If false, causes CQEngine to skip persisting the name of the class and to assume all objects
     * in the collection will be instances of the same class.
     * <p>
     *     The default value is false, which is commonly applicable and gives better performance
     *     and reduces the size of the serialized collection. However it will cause exceptions if
     *     different types of objects are added to the same collection, in which case applications
     *     can change this setting.
     * </p>
     */
    boolean polymorphic() default false;

    PersistenceConfig DEFAULT_CONFIG = new PersistenceConfig() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return PersistenceConfig.class;
        }

        @Override
        public Class<? extends PojoSerializer> serializer() {
            return KryoSerializer.class;
        }

        @Override
        public boolean polymorphic() {
            return false;
        }
    };
}
