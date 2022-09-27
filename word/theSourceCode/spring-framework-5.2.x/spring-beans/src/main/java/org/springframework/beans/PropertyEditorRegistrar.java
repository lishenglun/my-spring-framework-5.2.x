/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.beans;

/**
 * 属性编辑器的注册器：用于注册注册自定义编辑器（key："类型"，value：处理类型的"属性编辑器"）
 *
 * Interface for strategies that register custom
 * {@link java.beans.PropertyEditor property editors} with a
 * {@link org.springframework.beans.PropertyEditorRegistry property editor registry}.
 *
 * <p>This is particularly useful when you need to use the same set of
 * property editors in several different situations: write a corresponding
 * registrar and reuse that in each case.
 *
 * @author Juergen Hoeller
 * @since 1.2.6
 * @see PropertyEditorRegistry
 * @see java.beans.PropertyEditor
 */
public interface PropertyEditorRegistrar/* 属性编辑器注册器 */ {

	/*

	⚠️PropertyEditorRegistrar和PropertyEditorRegistry的区别：
	>>> registrar：注册器，书写将属性编辑器放入注册表的逻辑。例如：AddressPropertyEditorRegistrar
	>>> registry：注册表，实际存放属性编辑器的地方，定义了对属性编辑器集合的CRUD操作。例如：BeanWrapperImpl

	题外：Registrar翻译为注册商，Registry翻译为注册表！

	 */

	/**
	 * 注册自定义编辑器
	 *
	 * 题外：这里其实相当于是一个访问者模式
	 *
	 * Register custom {@link java.beans.PropertyEditor PropertyEditors} with
	 * the given {@code PropertyEditorRegistry}.
	 * <p>The passed-in registry will usually be a {@link BeanWrapper} or a
	 * {@link org.springframework.validation.DataBinder DataBinder}.
	 * <p>It is expected that implementations will create brand new
	 * {@code PropertyEditors} instances for each invocation of this
	 * method (since {@code PropertyEditors} are not threadsafe).
	 * @param registry the {@code PropertyEditorRegistry} to register the
	 * custom {@code PropertyEditors} with
	 */
	void registerCustomEditors(PropertyEditorRegistry/* 属性编辑器注册表：具体存储属性编辑器的对象。例如：BeanWrapperImpl */ registry);

}
