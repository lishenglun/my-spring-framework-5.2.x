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
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;

/**
 * 可以修改用@ResponseBody返回的结果值。
 *
 * 例如：我定义了一个Controller方法，用@ResponseBody进行修饰，然后在调用完这个Controller方法的时候，返回一个结果值。
 * 我就可以定义一个实现了ResponseBodyAdvice接口的类(参考：ResponseInfoControllerAdvice)，然后用@ControllerAdvice修饰，让其被识别到。
 * 因为方法上带了@ResponseBody，所以到时候，在用返回值处理器，处理该方法返回值的时候，用的处理器是RequestResponseBodyMethodProcessor，
 * 在RequestResponseBodyMethodProcessor内部的逻辑中，会调用我们实现了ResponseBodyAdvice接口的类，先调用它的supports()方法，判断是否要处理@ResponseBody方法的返回值，
 * 如果要处理，则调用beforeBodyWrite()方法，处理返回值，返回一个处理后的结果。比如：@ResponseBody方法返回一个User对象，我最终通过我们实现的ResponseBodyAdvice接口类，
 * 把User对象，进行一个包装，返回一个ResponseInfo对象，参考：ResponseInfoControllerAdvice
 *
 * Allows customizing the response after the execution of an {@code @ResponseBody}
 * or a {@code ResponseEntity} controller method but before the body is written
 * with an {@code HttpMessageConverter}.
 *
 * 允许在执行 {@code @ResponseBody} 或 {@code ResponseEntity} 控制器方法之后但，在使用 {@code HttpMessageConverter} 编写正文之前自定义响应。
 *
 * <p>Implementations may be registered directly with
 * {@code RequestMappingHandlerAdapter} and {@code ExceptionHandlerExceptionResolver}
 * or more likely annotated with {@code @ControllerAdvice} in which case they
 * will be auto-detected by both.
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 * @param <T> the body type
 */
public interface ResponseBodyAdvice<T> {

	/**
	 * 判断是否要执行beforeBodyWrite()方法，true为执行，false不执行
	 *
	 * Whether this component supports the given controller method return type
	 * and the selected {@code HttpMessageConverter} type.
	 * @param returnType the return type					返回值类型
	 * @param converterType the selected converter type		Http消息转换器
	 * @return {@code true} if {@link #beforeBodyWrite} should be invoked;
	 * {@code false} otherwise
	 */
	boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType);

	/**
	 * 对response()处理的执行方法
	 *
	 * Invoked after an {@code HttpMessageConverter} is selected and just before
	 * its write method is invoked.
	 *
	 * 在选择 {@code HttpMessageConverter} 之后调用它的 write 方法之前调用。
	 *
	 * @param body the body to be written
	 * @param returnType the return type of the controller method
	 * @param selectedContentType the content type selected through content negotiation
	 * @param selectedConverterType the converter type selected to write to the response
	 * @param request the current request
	 * @param response the current response
	 * @return the body that was passed in or a modified (possibly new) instance
	 */
	@Nullable
	T beforeBodyWrite(@Nullable T body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType,
			ServerHttpRequest request, ServerHttpResponse response);

}
