/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.servlet.mvc.method;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerMethodMappingNamingStrategy;

/**
 * A {@link org.springframework.web.servlet.handler.HandlerMethodMappingNamingStrategy
 * HandlerMethodMappingNamingStrategy} for {@code RequestMappingInfo}-based handler
 * method mappings.
 *
 * If the {@code RequestMappingInfo} name attribute is set, its value is used.
 * Otherwise the name is based on the capital letters of the class name,
 * followed by "#" as a separator, and the method name. For example "TC#getFoo"
 * for a class named TestController with method getFoo.
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 */
public class RequestMappingInfoHandlerMethodMappingNamingStrategy
		implements HandlerMethodMappingNamingStrategy<RequestMappingInfo> {

	/**
	 * HandlerMethod的类和方法的分割符
	 *
	 * Separator between the type and method-level parts of a HandlerMethod mapping name.
	 *
	 * HandlerMethod映射名称的类型和方法级别部分之间的分隔符。
	 * */
	public static final String SEPARATOR = "#";


	@Override
	public String getName(HandlerMethod handlerMethod, RequestMappingInfo mapping) {
		/* 1、如果RequestMappingInfo中存在name，就使用RequestMappingInfo中的name属性值 */

		if (mapping.getName() != null) {
			return mapping.getName();
		}

		/*

		2、如果RequestMappingInfo中不存在name属性值，那么就生成一个name = 类名称中的大写字母作为类名，拼接上#号，再拼接上方法名。
		例如：UserListController中的getUser()方法，得到的是ULC#getUser

		*/

		StringBuilder sb = new StringBuilder();

		// 获取类的简单类名，例如UserListController类得到的就是：UserListController
		String simpleTypeName = handlerMethod.getBeanType().getSimpleName();
		for (int i = 0; i < simpleTypeName.length(); i++) {
			// 提取类名中的大写字母作为类名，例如simpleTypeName = UserListController，得到的就是ULC。
			if (Character.isUpperCase(simpleTypeName.charAt(i))) {
				sb.append(simpleTypeName.charAt(i));
			}
		}
		/**
		 * 1、handlerMethod.getMethod().getName()：获取方法的名称。
		 * 例如：UserListController中有个方法是getUser()，得到的就是getUser
		 */
		// 拼接上方法名
		sb.append(SEPARATOR/* # */).append(handlerMethod.getMethod().getName());

		return sb.toString();
	}

}
