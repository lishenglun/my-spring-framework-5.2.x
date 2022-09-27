/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.cors;

import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;

/**
 * 跨域配置接口
 *
 * Interface to be implemented by classes (usually HTTP request handlers) that
 * provides a {@link CorsConfiguration} instance based on the provided request.
 *
 * 由基于提供的请求提供 {@link CorsConfiguration} 实例的类（通常是 HTTP 请求处理程序）实现的接口。
 *
 * @author Sebastien Deleuze
 * @since 4.2
 */
public interface CorsConfigurationSource {

	/**
	 * 根据传入的请求返回一个跨域的配置
	 *
	 * Return a {@link CorsConfiguration} based on the incoming request.
	 *
	 * 根据传入的请求返回一个 {@link CorsConfiguration}。
	 *
	 * @return the associated {@link CorsConfiguration}, or {@code null} if none
	 */
	@Nullable
	CorsConfiguration getCorsConfiguration(HttpServletRequest request);

}
