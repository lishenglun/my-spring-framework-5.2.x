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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.RequestToViewNameTranslator;

/**
 * 处理void和String类型返回值，如果返回值为空则直接返回，否则将返回值同mavContainer的setViewName方法设置到期View中，并判断返回值是不是redirect类型，
 * 如果是则设置MavContainer的redirectModelScenario为true
 *
 * Handles return values of types {@code void} and {@code String} interpreting them
 * as view name reference. As of 4.2, it also handles general {@code CharSequence}
 * types, e.g. {@code StringBuilder} or Groovy's {@code GString}, as view names.
 *
 * <p>A {@code null} return value, either due to a {@code void} return type or
 * as the actual return value is left as-is allowing the configured
 * {@link RequestToViewNameTranslator} to select a view name by convention.
 *
 * <p>A String return value can be interpreted in more than one ways depending on
 * the presence of annotations like {@code @ModelAttribute} or {@code @ResponseBody}.
 * Therefore this handler should be configured after the handlers that support these
 * annotations.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ViewNameMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	/**
	 * 重定向的表达式的数组
	 */
	@Nullable
	private String[] redirectPatterns;


	/**
	 * Configure one more simple patterns (as described in {@link PatternMatchUtils#simpleMatch})
	 * to use in order to recognize custom redirect prefixes in addition to "redirect:".
	 * <p>Note that simply configuring this property will not make a custom redirect prefix work.
	 * There must be a custom View that recognizes the prefix as well.
	 * @since 4.1
	 */
	public void setRedirectPatterns(@Nullable String... redirectPatterns) {
		this.redirectPatterns = redirectPatterns;
	}

	/**
	 * The configured redirect patterns, if any.
	 */
	@Nullable
	public String[] getRedirectPatterns() {
		return this.redirectPatterns;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		Class<?> paramType = returnType.getParameterType();
		// 注意：String是CharSequence类型，字符串是一个字符序列
		return (void.class == paramType || CharSequence.class.isAssignableFrom(paramType));
	}

	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		/*

		1、如果返回值是String类型，则将返回值作为视图名称，设置到ModelAndView中，后面会根据视图名称找到对应的视图(页面)进行回显(返回)；
		并通过返回值判断是不是一个重定向请求，如果是重定向请求，则设置ModelAndView里面的重定向模型场景属性为true，代表将来要应用重定向模型

		*/
		// 如果返回值是String类型
		if (returnValue instanceof CharSequence) {
			// （1）将返回值作为视图名称，设置到ModelAndView中。后面会根据视图名称找到对应的视图(页面)进行回显(返回)。
			String viewName = returnValue.toString();
			mavContainer.setViewName(viewName);

			// （2）通过返回值判断是不是一个重定向请求，如果是重定向请求，则设置ModelAndView里面的重定向模型场景属性为true
			if (isRedirectViewName(viewName)) {
				mavContainer.setRedirectModelScenario/* 设置重定向模型场景 */(true);
			}
		}
		/*

		2、如果返回值不是String类型，就抛出异常

		 */
		// 如果返回值不是String类型，而且非void ，则抛出UnsupportedOperationException异常
		else if (returnValue != null) {
			// should not happen —— 不应该发生
			// ⚠️当前返回值处理器，只能处理String类型，如果是非String类型，但是进来了，是没法进行处理的，所以抛出异常
			throw new UnsupportedOperationException("Unexpected return type: "/* 意外的返回类型 */ +
					returnType.getParameterType().getName() + " in method: " + returnType.getMethod());
		}
	}

	/**
	 * Whether the given view name is a redirect view reference.
	 * The default implementation checks the configured redirect patterns and
	 * also if the view name starts with the "redirect:" prefix.
	 * @param viewName the view name to check, never {@code null}
	 * @return "true" if the given view name is recognized as a redirect view
	 * reference; "false" otherwise.
	 */
	protected boolean isRedirectViewName(String viewName) {
		// 符合 redirectPatterns 表达式，或者以 redirect: 开头
		return (PatternMatchUtils.simpleMatch(this.redirectPatterns, viewName) || viewName.startsWith("redirect:"));
	}

}
