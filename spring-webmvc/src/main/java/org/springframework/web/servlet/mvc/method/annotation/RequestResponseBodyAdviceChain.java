/*
 * Copyright 2002-2018 the original author or authors.
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
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.ControllerAdviceBean;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Invokes {@link RequestBodyAdvice} and {@link ResponseBodyAdvice} where each
 * instance may be (and is most likely) wrapped with
 * {@link org.springframework.web.method.ControllerAdviceBean ControllerAdviceBean}.
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
class RequestResponseBodyAdviceChain implements RequestBodyAdvice, ResponseBodyAdvice<Object> {

	private final List<Object> requestBodyAdvice = new ArrayList<>(4);

	private final List<Object> responseBodyAdvice = new ArrayList<>(4);


	/**
	 * Create an instance from a list of objects that are either of type
	 * {@code ControllerAdviceBean} or {@code RequestBodyAdvice}.
	 */
	public RequestResponseBodyAdviceChain(@Nullable List<Object> requestResponseBodyAdvice) {
		this.requestBodyAdvice.addAll(getAdviceByType(requestResponseBodyAdvice, RequestBodyAdvice.class));
		this.responseBodyAdvice.addAll(getAdviceByType(requestResponseBodyAdvice, ResponseBodyAdvice.class));
	}

	@SuppressWarnings("unchecked")
	static <T> List<T> getAdviceByType(@Nullable List<Object> requestResponseBodyAdvice, Class<T> adviceType) {
		if (requestResponseBodyAdvice != null) {
			List<T> result = new ArrayList<>();
			for (Object advice : requestResponseBodyAdvice) {
				Class<?> beanType = (advice instanceof ControllerAdviceBean ?
						((ControllerAdviceBean) advice).getBeanType() : advice.getClass());
				if (beanType != null && adviceType.isAssignableFrom(beanType)) {
					result.add((T) advice);
				}
			}
			return result;
		}
		return Collections.emptyList();
	}


	@Override
	public boolean supports(MethodParameter param, Type type, Class<? extends HttpMessageConverter<?>> converterType) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public HttpInputMessage beforeBodyRead(HttpInputMessage request, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {

		for (RequestBodyAdvice advice : getMatchingAdvice(parameter, RequestBodyAdvice.class)) {
			if (advice.supports(parameter, targetType, converterType)) {
				request = advice.beforeBodyRead(request, parameter, targetType, converterType);
			}
		}
		return request;
	}

	@Override
	public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

		for (RequestBodyAdvice advice : getMatchingAdvice(parameter, RequestBodyAdvice.class)) {
			if (advice.supports(parameter, targetType, converterType)) {
				body = advice.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
			}
		}
		return body;
	}

	/**
	 * @param body ?????????
	 * @param returnType ???????????????
	 * @param contentType
	 * @param converterType
	 * @param request the current request
	 * @param response the current response
	 * @return
	 */
	@Override
	@Nullable
	public Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType contentType,
			Class<? extends HttpMessageConverter<?>> converterType,
			ServerHttpRequest request, ServerHttpResponse response) {

		// ?????????????????????ResponseBodyAdvice#beforeBodyWrite()????????????????????????????????????????????????
		return processBody(body, returnType, contentType, converterType, request, response);
	}

	@Override
	@Nullable
	public Object handleEmptyBody(@Nullable Object body, HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

		for (RequestBodyAdvice advice : getMatchingAdvice(parameter, RequestBodyAdvice.class)) {
			if (advice.supports(parameter, targetType, converterType)) {
				body = advice.handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
			}
		}
		return body;
	}


	/**
	 *
	 * @param body			?????????
	 * @param returnType    ???????????????
	 * @param contentType
	 * @param converterType
	 * @param request
	 * @param response
	 * @param <T>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	private <T> Object processBody(@Nullable Object body, MethodParameter returnType, MediaType contentType,
			Class<? extends HttpMessageConverter<?>> converterType,
			ServerHttpRequest request, ServerHttpResponse response) {
		/*

		1??????????????????ResponseBodyAdvice#beforeBodyWrite()???????????????????????????????????????????????????

		????????????????????????????????????????????????????????????????????????ResponseBodyAdvice#beforeBodyWrite()?????????????????????????????????????????????

		*/
		/**
		 * 1???spring mvc??????????????????ResponseBodyAdvice???JsonViewRequestBodyAdvice
		 */
		// ???????????????ResponseBodyAdvice?????????????????????
		for (ResponseBodyAdvice<?> advice : getMatchingAdvice(returnType, ResponseBodyAdvice.class)) {
			// ??????ResponseBodyAdvice#supports()???????????????ResponseBodyAdvice??????????????????????????????????????????
			if (advice.supports(returnType, converterType)) {
				// ??????ResponseBodyAdvice#beforeBodyWrite()????????????????????????????????????????????????
				body = ((ResponseBodyAdvice<T>) advice).beforeBodyWrite((T) body, returnType,
						contentType, converterType, request, response);
			}
		}
		return body;
	}

	/**
	 * @param parameter		???????????????
	 * @param adviceType	ResponseBodyAdvice
	 * @param <A>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <A> List<A> getMatchingAdvice/* ????????????????????? */(MethodParameter parameter, Class<? extends A> adviceType) {
		// ??????Class????????????????????????RequestBodyAdvice????????????????????????ResponseBodyAdvice?????????
		List<Object> availableAdvice = getAdvice(adviceType);
		if (CollectionUtils.isEmpty(availableAdvice)) {
			return Collections.emptyList();
		}
		List<A> result = new ArrayList<>(availableAdvice.size());
		for (Object advice : availableAdvice) {
			if (advice instanceof ControllerAdviceBean) {
				ControllerAdviceBean adviceBean = (ControllerAdviceBean) advice;
				if (!adviceBean.isApplicableToBeanType/* ?????????Bean?????? */(parameter.getContainingClass/* ??????????????? */())) {
					continue;
				}
				advice = adviceBean.resolveBean();
			}
			if (adviceType.isAssignableFrom(advice.getClass())) {
				result.add((A) advice);
			}
		}
		return result;
	}

	/**
	 * ??????Class????????????????????????RequestBodyAdvice????????????????????????ResponseBodyAdvice?????????
	 *
	 * @param adviceType
	 * @return
	 */
	private List<Object> getAdvice(Class<?> adviceType) {
		if (RequestBodyAdvice.class == adviceType) {
			return this.requestBodyAdvice;
		}
		else if (ResponseBodyAdvice.class == adviceType) {
			return this.responseBodyAdvice;
		}
		else {
			throw new IllegalArgumentException("Unexpected adviceType: " + adviceType);
		}
	}

}
