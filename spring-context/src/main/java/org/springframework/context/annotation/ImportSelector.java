/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;

import java.util.function.Predicate;

/**
 * ImportSelector被设计成和@Import同样的效果，但是实现了ImportSelector的类可以根据条件，决定导入哪些配置，比@Import更加灵活和动态。
 *
 * 此接口是spring中导入外部配置的核心接口，根据给定的条件（通常是一个或多个注解）判断要导入哪个配置类
 * 如果该接口的实现类同时实现了一些Aware接口，那么在调用selectImports()之前先调用上述接口中的回调方法，
 * 如果需要在所有的@Configuration处理完再导入，可以实现DeferredImportSelector接口
 *
 * 就是我可以进行相应的选择，我导入哪些对应的外部资源
 *
 * Interface to be implemented by types that determine which @{@link Configuration}
 * class(es) should be imported based on a given selection criteria, usually one or
 * more annotation attributes.
 *
 * 由类型实现的接口，这些类型根据给定的选择标准（通常是一个或多个注释属性）确定应该导入哪个 @{@link Configuration} 类。
 *
 * <p>An {@link ImportSelector} may implement any of the following
 * {@link org.springframework.beans.factory.Aware Aware} interfaces,
 * and their respective methods will be called prior to {@link #selectImports}:
 * <ul>
 * <li>{@link org.springframework.context.EnvironmentAware EnvironmentAware}</li>
 * <li>{@link org.springframework.beans.factory.BeanFactoryAware BeanFactoryAware}</li>
 * <li>{@link org.springframework.beans.factory.BeanClassLoaderAware BeanClassLoaderAware}</li>
 * <li>{@link org.springframework.context.ResourceLoaderAware ResourceLoaderAware}</li>
 * </ul>
 *
 * <p>Alternatively, the class may provide a single constructor with one or more of
 * the following supported parameter types:
 * <ul>
 * <li>{@link org.springframework.core.env.Environment Environment}</li>
 * <li>{@link org.springframework.beans.factory.BeanFactory BeanFactory}</li>
 * <li>{@link java.lang.ClassLoader ClassLoader}</li>
 * <li>{@link org.springframework.core.io.ResourceLoader ResourceLoader}</li>
 * </ul>
 *
 * <p>{@code ImportSelector} implementations are usually processed in the same way
 * as regular {@code @Import} annotations, however, it is also possible to defer
 * selection of imports until all {@code @Configuration} classes have been processed
 * (see {@link DeferredImportSelector} for details).
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see DeferredImportSelector
 * @see Import
 * @see ImportBeanDefinitionRegistrar
 * @see Configuration
 */
public interface ImportSelector {

	/**
	 * Select and return the names of which class(es) should be imported based on
	 * the {@link AnnotationMetadata} of the importing @{@link Configuration} class.
	 * 根据导入 @{@link Configuration} 类的 {@link AnnotationMetadata} 选择并返回应导入的类的名称。
	 *
	 * @return the class names, or an empty array if none —— 类名，如果没有则返回一个空数组
	 * @param importingClassMetadata	标注@Import(A.class)注解的配置类的注解元数据，
	 * _________________________________例如：
	 * _________________________________@Import(A.class)
	 * _________________________________public class MyImportConfiguration{
	 * _________________________________}
	 * _________________________________MyImportConfiguration是标注@Import(A.class)注解的配置类
	 * _________________________________那么importingClassMetadata = MyImportConfiguration这个配置类的注解元数据
	 */
	String[] selectImports(AnnotationMetadata importingClassMetadata);

	/**
	 * 返回排除的类，是一个类过滤器。但是这个方法被default注解了，可见Spring公司也知道，这个基本没啥人用。
	 *
	 * Return a predicate for excluding classes from the import candidates, to be
	 * transitively applied to all classes found through this selector's imports.
	 * <p>If this predicate returns {@code true} for a given fully-qualified
	 * class name, said class will not be considered as an imported configuration
	 * class, bypassing class file loading as well as metadata introspection.
	 *
	 * 返回一个从导入候选中排除类的谓词，以传递地应用于通过此选择器的导入找到的所有类。
	 * <p>如果此谓词为给定的完全限定类名返回 {@code true}，
	 * 则该类将不会被视为导入的配置类，从而绕过类文件加载以及元数据自省。
	 *
	 * @return the filter predicate for fully-qualified candidate class names
	 * of transitively imported configuration classes, or {@code null} if none
	 *
	 * 传递导入配置类的完全限定候选类名称的过滤谓词，如果没有，则为 {@code null}
	 *
	 * @since 5.2.4
	 */
	@Nullable
	default Predicate<String> getExclusionFilter/* 获取排除过滤器 */() {
		return null;
	}

}
