/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 1、@Indexed：解决@ComponentScan的性能问题，提升检索效率，加快系统启动速度。
 *
 * 随着业务模块越来越多，越来越复杂，尤其在比较大的单体架构里面，java文件很多。系统启动的时候，基于我们扫描的路径，扫描加载的东西很多，我们需要去很多目录里面寻找加载java类，
 * 这里面检索和遍历的效率，随着java文件越来越多，效率就会越来越低，对系统启动的影响越来越大，所以@ComponentScan在启动时存在性能问题
 *
 * 我们程序有2个阶段，编译和运行。在编译的时候，我们的代码已经确定了，哪些类中有注解修饰，已经知道了，所以@Indexed就是在编译的时候，帮我们把已经有对应注解修饰的java类都收集起来，存储在一个文件里面去；
 * 当系统启动的时候，只需要加载这一个索引文件，去这一个文件里面找到要加载的java类的全类路径限定名即可，这样一来就把启动时的性能影响转接到编译时，
 * 通过一次IO，就找到了要注入容器中的这些Java类的全路径名，把扫描的过程给省略掉了，增加了系统启动速度
 *
 * 根据这个索引文件就可以找到所有要载入容器中的Java类。@Component里面内置了@Indexed，所以我们去添加组件注解的话，在spring5.0之后，编译的时候默认都会识别到要被扫描的Java文件，要生效只需要加上spring-context-indexer依赖即可
 *
 * <p>
 *
 * Indicate that the annotated element represents a stereotype for the index. —— 指示带注解的元素表示索引的构造型。
 *
 * <p>The {@code CandidateComponentsIndex} is an alternative to classpath
 * scanning that uses a metadata file generated at compilation time. The
 * index allows retrieving the candidate components (i.e. fully qualified
 * name) based on a stereotype. This annotation instructs the generator to
 * index the element on which the annotated element is present or if it
 * implements or extends from the annotated element. The stereotype is the
 * fully qualified name of the annotated element.
 *
 * <p>Consider the default {@link Component} annotation that is meta-annotated
 * with this annotation. If a component is annotated with {@link Component},
 * an entry for that component will be added to the index using the
 * {@code org.springframework.stereotype.Component} stereotype.
 *
 * <p>This annotation is also honored on meta-annotations. Consider this
 * custom annotation:
 * <pre class="code">
 * package com.example;
 *
 * &#064;Target(ElementType.TYPE)
 * &#064;Retention(RetentionPolicy.RUNTIME)
 * &#064;Documented
 * &#064;Indexed
 * &#064;Service
 * public @interface PrivilegedService { ... }
 * </pre>
 *
 * If the above annotation is present on a type, it will be indexed with two
 * stereotypes: {@code org.springframework.stereotype.Component} and
 * {@code com.example.PrivilegedService}. While {@link Service} isn't directly
 * annotated with {@code Indexed}, it is meta-annotated with {@link Component}.
 *
 * <p>It is also possible to index all implementations of a certain interface or
 * all the subclasses of a given class by adding {@code @Indexed} on it.
 *
 * Consider this base interface:
 * <pre class="code">
 * package com.example;
 *
 * &#064;Indexed
 * public interface AdminService { ... }
 * </pre>
 *
 * Now, consider an implementation of this {@code AdminService} somewhere:
 * <pre class="code">
 * package com.example.foo;
 *
 * import com.example.AdminService;
 *
 * public class ConfigurationAdminService implements AdminService { ... }
 * </pre>
 *
 * Because this class implements an interface that is indexed, it will be
 * automatically included with the {@code com.example.AdminService} stereotype.
 * If there are more {@code @Indexed} interfaces and/or superclasses in the
 * hierarchy, the class will map to all their stereotypes.
 *
 * @author Stephane Nicoll
 * @since 5.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Indexed {
}
