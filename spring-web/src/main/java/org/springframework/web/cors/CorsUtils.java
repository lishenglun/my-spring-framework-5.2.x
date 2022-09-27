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

package org.springframework.web.cors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility class for CORS request handling based on the
 * <a href="https://www.w3.org/TR/cors/">CORS W3C recommendation</a>.
 *
 * @author Sebastien Deleuze
 * @since 4.2
 */
public abstract class CorsUtils {

	/**
	 * Returns {@code true} if the request is a valid CORS one by checking {@code Origin}
	 * header presence and ensuring that origins are different.
	 */
	public static boolean isCorsRequest(HttpServletRequest request) {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (origin == null) {
			return false;
		}
		UriComponents originUrl = UriComponentsBuilder.fromOriginHeader(origin).build();
		String scheme = request.getScheme();
		String host = request.getServerName();
		int port = request.getServerPort();
		return !(ObjectUtils.nullSafeEquals(scheme, originUrl.getScheme()) &&
				ObjectUtils.nullSafeEquals(host, originUrl.getHost()) &&
				getPort(scheme, port) == getPort(originUrl.getScheme(), originUrl.getPort()));

	}

	private static int getPort(@Nullable String scheme, int port) {
		if (port == -1) {
			if ("http".equals(scheme) || "ws".equals(scheme)) {
				port = 80;
			}
			else if ("https".equals(scheme) || "wss".equals(scheme)) {
				port = 443;
			}
		}
		return port;
	}

	/**
	 * 判断是不是一个预检请求。
	 *
	 * 题外：什么是预检请求，参考：http://t.zoukankan.com/goloving-p-14928094.html —— 《浅析SpringSecurity对跨域非简单请求的Prefight预检请求的处理：requestMatchers(CorsUtils::isPreFlightRequest).permitAll()、及简单请求与非简单请求的理解》
	 *
	 * Returns {@code true} if the request is a valid CORS pre-flight one by checking {code OPTIONS} method with
	 * {@code Origin} and {@code Access-Control-Request-Method} headers presence.
	 *
	 * 如果请求是有效的 CORS 飞行前请求，则返回 {@code true}，方法是检查带有 {@code Origin} 和 {@code Access-Control-Request-Method} 标头的 {code OPTIONS} 方法。
	 */
	public static boolean isPreFlightRequest(HttpServletRequest request) {
		// 请求方法是OPTIONS && 包含Origin请求头 && 包含Access-Control-Request-Method请求头
		return (HttpMethod.OPTIONS.matches(request.getMethod()) &&
				request.getHeader(HttpHeaders.ORIGIN/* Origin */) != null &&
				request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD/* Access-Control-Request-Method *//* 访问控制请求方法 */) != null);
	}

}
