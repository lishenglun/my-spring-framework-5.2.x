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

package org.springframework.web.servlet.mvc.annotation;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 处理异常类上面加了@ResponseStatus的异常类型
 *
 * A {@link org.springframework.web.servlet.HandlerExceptionResolver
 * HandlerExceptionResolver} that uses the {@link ResponseStatus @ResponseStatus}
 * annotation to map exceptions to HTTP status codes.
 *
 * 一个 {@link org.springframework.web.servlet.HandlerExceptionResolver HandlerExceptionResolver}
 * 使用 {@link ResponseStatus @ResponseStatus} 注解，将异常映射到 HTTP 状态代码。
 *
 * <p>This exception resolver is enabled by default in the
 * {@link org.springframework.web.servlet.DispatcherServlet DispatcherServlet}
 * and the MVC Java config and the MVC namespace.
 *
 * <p>As of 4.2 this resolver also looks recursively for {@code @ResponseStatus}
 * present on cause exceptions, and as of 4.2.2 this resolver supports
 * attribute overrides for {@code @ResponseStatus} in custom composed annotations.
 *
 * <p>As of 5.0 this resolver also supports {@link ResponseStatusException}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @since 3.0
 * @see ResponseStatus
 * @see ResponseStatusException
 */
public class ResponseStatusExceptionResolver extends AbstractHandlerExceptionResolver implements MessageSourceAware {

	@Nullable
	private MessageSource messageSource;


	@Override
	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}


	@Override
	@Nullable
	protected ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		try {
			/*

			1、如果异常类实现了ResponseStatusException接口，则转换为ResponseStatusException，提取对应的信息，往response里面设置对应的状态码和原因，
			然后创建空的ModelAndView对象进行返回

			*/
			// 情况一，如果异常是 ResponseStatusException 类型，进行解析并设置到响应
			if (ex instanceof ResponseStatusException) {
				return resolveResponseStatusException((ResponseStatusException) ex, request, response, handler);
			}

			/*

			2、如果异常类上有@ResponseStatus注解，则提取异常类上@ResponseStatus中的信息，往response里面设置对应的状态码和原因，
			然后创建空的ModelAndView对象进行返回

			 */
			// 情况二，如果有 @ResponseStatus 注解，进行解析并设置到响应
			// 查找当前的异常类上面是否加了@ResponseStatus
			ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
			if (status != null) {
				// 解析@ResponseStatus，设置响应状态码和错误提示原因；以及创建和返回一个空壳的错误视图对象ModelAndView

				// ⚠️往response里面设置对应的状态码和原因，然后创建空的ModelAndView对象进行返回
				return resolveResponseStatus(status, request, response, handler, ex);
			}
			/*

			3、使用异常的原因，再走一次【1、2】的逻辑

			 */
			if (ex.getCause() instanceof Exception) {
				return doResolveException(request, response, handler, (Exception) ex.getCause());
			}
		}
		catch (Exception resolveEx) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failure while trying to resolve exception [" + ex.getClass().getName() + "]", resolveEx);
			}
		}

		/* 4、当前异常解析器无法处理当前类型，就返回null */
		return null;
	}

	/**
	 * Template method that handles the {@link ResponseStatus @ResponseStatus} annotation.
	 * <p>The default implementation delegates to {@link #applyStatusAndReason}
	 * with the status code and reason from the annotation.
	 * @param responseStatus the {@code @ResponseStatus} annotation
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the
	 * time of the exception, e.g. if multipart resolution failed
	 * @param ex the exception
	 * @return an empty ModelAndView, i.e. exception resolved
	 */
	protected ModelAndView resolveResponseStatus(ResponseStatus responseStatus, HttpServletRequest request,
			HttpServletResponse response, @Nullable Object handler, Exception ex) throws Exception {

		// 状态码
		int statusCode = responseStatus.code().value();
		// 原因
		String reason = responseStatus.reason();

		// ⚠️往response里面设置对应的状态码和原因，然后创建空的ModelAndView对象进行返回
		return applyStatusAndReason(statusCode, reason, response);
	}

	/**
	 * Template method that handles an {@link ResponseStatusException}.
	 * <p>The default implementation applies the headers from
	 * {@link ResponseStatusException#getResponseHeaders()} and delegates to
	 * {@link #applyStatusAndReason} with the status code and reason from the
	 * exception.
	 * @param ex the exception
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the
	 * time of the exception, e.g. if multipart resolution failed
	 * @return an empty ModelAndView, i.e. exception resolved
	 * @since 5.0
	 */
	protected ModelAndView resolveResponseStatusException(ResponseStatusException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws Exception {

		// 获取响应头，设置到当前response中
		ex.getResponseHeaders().forEach((name, values) ->
				values.forEach(value -> response.addHeader(name, value)));

		// 状态码
		int statusCode = ex.getStatus().value();
		// 响应的提示原因
		String reason = ex.getReason();

		// ⚠️往response里面设置对应的状态码和原因，然后创建空的ModelAndView对象进行返回
		return applyStatusAndReason(statusCode, reason, response);
	}

	/**
	 * 往response里面设置对应的状态码和原因，然后创建空的ModelAndView对象进行返回
	 *
	 * Apply the resolved status code and reason to the response.
	 * <p>The default implementation sends a response error using
	 * {@link HttpServletResponse#sendError(int)} or
	 * {@link HttpServletResponse#sendError(int, String)} if there is a reason
	 * and then returns an empty ModelAndView.
	 * @param statusCode the HTTP status code
	 * @param reason the associated reason (may be {@code null} or empty)
	 * @param response current HTTP response
	 * @since 5.0
	 */
	protected ModelAndView applyStatusAndReason(int statusCode, @Nullable String reason, HttpServletResponse response)
			throws IOException {

		/* 1、如果没有错误提示原因，则只设置响应状态码 */
		if (!StringUtils.hasLength(reason)) {
			response.sendError(statusCode);
		}

		/* 2、如果有错误提示原因，则设置响应状态码和错误提示原因 */
		else {
			// 进一步解析错误提示，如果有messageSource的情况下
			String resolvedReason = (this.messageSource != null ?
					this.messageSource.getMessage(reason, null, reason, LocaleContextHolder.getLocale()) :
					reason);
			// 设置响应状态码和错误提示原因
			response.sendError(statusCode, resolvedReason);
		}

		/*

		3、创建空壳ModelAndView对象返回。
		题外：这点很重要，只要当前处理器返回了错误视图对象，那么就不会走下一个异常解析器进行解析了，而是会应用当前错误视图对象，进行返回！

		*/
		return new ModelAndView();
	}

}
