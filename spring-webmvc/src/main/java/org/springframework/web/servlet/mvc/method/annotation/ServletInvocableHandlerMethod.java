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
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.View;
import org.springframework.web.util.NestedServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

/**
 * 继承InvocableHandlerMethod，能够通过已经注册的HandlerMethodReturnValueHandler来处理我们的返回值，并且支持通过方法级别的@ResponseStatus注解来设置响应状态
 * （1）可以通过"返回值处理器"处理返回值
 * （2）可以通过"@ResponseStatus"设置响应状态码
 *
 * Extends {@link InvocableHandlerMethod} with the ability to handle return
 * values through a registered {@link HandlerMethodReturnValueHandler} and
 * also supports setting the response status based on a method-level
 * {@code @ResponseStatus} annotation.
 *
 * link InvocableHandlerMethod} 以通过注册的 {@link HandlerMethodReturnValueHandler} 处理返回值的能力，
 * 并且还支持基于方法级 {@code @ResponseStatus} 注解设置响应状态。
 *
 * <p>A {@code null} return value (including void) may be interpreted as the
 * end of request processing in combination with a {@code @ResponseStatus}
 * annotation, a not-modified check condition
 * (see {@link ServletWebRequest#checkNotModified(long)}), or
 * a method argument that provides access to the response stream.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {

	private static final Method CALLABLE_METHOD = ClassUtils.getMethod(Callable.class, "call");

	/** 返回结果处理器组合对象 */
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;


	/**
	 * Creates an instance from the given handler and method.
	 */
	public ServletInvocableHandlerMethod(Object handler, Method method) {
		super(handler, method);
	}

	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public ServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}


	/**
	 * Register {@link HandlerMethodReturnValueHandler} instances to use to
	 * handle return values.
	 */
	public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
		this.returnValueHandlers = returnValueHandlers;
	}


	/**
	 * Invoke the method and handle the return value through one of the
	 * configured {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}.
	 * @param webRequest the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type (not resolved)
	 */
	public void invokeAndHandle(ServletWebRequest webRequest, ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {

		/* 1、调用执行请求的方法，得到返回结果 */

		// 执行请求 —— 调用执行请求的方法，得到返回结果
		Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);

		/* 2、设置响应状态码 */
		// 往response中设置@ResponseStatus注解中设置的响应状态码，如果没配，那么响应状态码就是null
		// 注意：@ResponseStatus的解析并不是在这里完成的，而是在HandlerMethod构造方法里面完成的，这里只是设置它里面的值而已
		setResponseStatus(webRequest);

		/* 3、处理返回值 */

		// 判断返回值是否为空
		if (returnValue == null) {
			// 请求是否被修改 || 响应状态码是否不为空 || 当前请求是否处理完毕
			if (isRequestNotModified(webRequest)/*  */ || getResponseStatus() != null /*  */ || mavContainer.isRequestHandled()) {
				// 【请求被修改 || 有响应状态码(@ResponseStatus中设置的响应状态码) || 请求处理完毕】三个条件有一个成立，则设置请求处理完成并返回
				disableContentCachingIfNecessary/* 必要时禁用内容缓存 */(webRequest);
				// 设置请求已经处理过了
				mavContainer.setRequestHandled(true);
				return;
			}
		}
		// 如果返回值不为null，但是HandlerMethod上存在@ResponseStatus，@ResponseStatus中存在原因，则假设是报错了。
		// 于是设置请求处理完成，并return，不再往下进行处理。后面也不会进行视图的渲染和回显。
		// 注意：由于请求是否完成的标识设置为true，后续在获取ModelAndView的时候，是null，所以spring mvc不会渲染视图和回显了，
		// >>> 而是由tomcat根据上面setResponseStatus(webRequest)中往response中设置@ResponseStatus中设置的响应状态码和原因，进行回显；
		// >>> tomcat会根据响应状态码和原因，返回对应的页面到前端，例如：如果是500，则会有一个错误页面，并显示@ResponseStatus中设置的错误原因；如果是200，则是一个空白页面。
		else if (StringUtils.hasText(getResponseStatusReason())) {
			mavContainer.setRequestHandled(true);
			return;
		}

		// 设置请求还未处理完成，后面还需要继续处理，例如：渲染视图和回显视图
		mavContainer.setRequestHandled(false);
		Assert.state(this.returnValueHandlers != null, "No return value handlers");

		/* 3.1、使用"返回值处理器"处理"返回值" */
		try {
			// 使用"返回值处理器"处理"返回值"
			this.returnValueHandlers.handleReturnValue(
					returnValue, getReturnValueType(returnValue)/* 获取返回值类型 */, mavContainer, webRequest);
		}
		catch (Exception ex) {
			if (logger.isTraceEnabled()) {
				logger.trace(formatErrorForReturnValue(returnValue), ex);
			}
			throw ex;
		}
	}

	/**
	 * Set the response status according to the {@link ResponseStatus} annotation.
	 */
	private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
		/* 1、获取@ResponseStatus中的状态码 */
		// 注意：@ResponseStatus的解析并不是在这里完成的，而是在HandlerMethod构造方法里面完成的
		HttpStatus status = getResponseStatus();

		/* 2、如果状态码为空，就不做任何设置 */

		if (status == null) {
			return;
		}

		/* 3、如果状态码不为空，就设置响应的状态码 */

		HttpServletResponse response = webRequest.getResponse();
		if (response != null) {
			// 获取@ResponseStatus中的原因
			String reason = getResponseStatusReason();
			/* 3.1、有原因 ，则设置状态码和原因 */
			if (StringUtils.hasText(reason)) {
				response.sendError(status.value(), reason);
			}
			/* 3.2、没有原因，就只设置状态码 */
			else {
				response.setStatus(status.value());
			}
		}

		/* 4、往request作用域里面设置一个响应状态码 */
		/* 在我们的View对象里面，有一个responseStatus，我要把这个响应状态码也设置进去 */

		// To be picked up by RedirectView
		// 为了RedirectView，所以进行设置
		webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE/* .responseStatus */, status);
	}

	/**
	 * Does the given request qualify as "not modified"? —— 给定的请求是否符合“未修改”的条件？
	 * @see ServletWebRequest#checkNotModified(long)
	 * @see ServletWebRequest#checkNotModified(String)
	 */
	private boolean isRequestNotModified(ServletWebRequest webRequest) {
		// 判断当前请求没有被修改
		return webRequest.isNotModified();
	}

	private void disableContentCachingIfNecessary(ServletWebRequest webRequest) {
		if (isRequestNotModified(webRequest)) {
			HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
			Assert.notNull(response, "Expected HttpServletResponse");
			if (StringUtils.hasText(response.getHeader(HttpHeaders.ETAG))) {
				HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
				Assert.notNull(request, "Expected HttpServletRequest");
			}
		}
	}

	private String formatErrorForReturnValue(@Nullable Object returnValue) {
		return "Error handling return value=[" + returnValue + "]" +
				(returnValue != null ? ", type=" + returnValue.getClass().getName() : "") +
				" in " + toString();
	}

	/**
	 * Create a nested ServletInvocableHandlerMethod subclass that returns the
	 * the given value (or raises an Exception if the value is one) rather than
	 * actually invoking the controller method. This is useful when processing
	 * async return values (e.g. Callable, DeferredResult, ListenableFuture).
	 */
	ServletInvocableHandlerMethod wrapConcurrentResult(Object result) {
		return new ConcurrentResultHandlerMethod(result, new ConcurrentResultMethodParameter(result));
	}


	/**
	 * A nested subclass of {@code ServletInvocableHandlerMethod} that uses a
	 * simple {@link Callable} instead of the original controller as the handler in
	 * order to return the fixed (concurrent) result value given to it. Effectively
	 * "resumes" processing with the asynchronously produced return value.
	 */
	private class ConcurrentResultHandlerMethod extends ServletInvocableHandlerMethod {

		private final MethodParameter returnType;

		public ConcurrentResultHandlerMethod(final Object result, ConcurrentResultMethodParameter returnType) {
			super((Callable<Object>) () -> {
				if (result instanceof Exception) {
					throw (Exception) result;
				}
				else if (result instanceof Throwable) {
					throw new NestedServletException("Async processing failed", (Throwable) result);
				}
				return result;
			}, CALLABLE_METHOD);

			if (ServletInvocableHandlerMethod.this.returnValueHandlers != null) {
				setHandlerMethodReturnValueHandlers(ServletInvocableHandlerMethod.this.returnValueHandlers);
			}
			this.returnType = returnType;
		}

		/**
		 * Bridge to actual controller type-level annotations.
		 */
		@Override
		public Class<?> getBeanType() {
			return ServletInvocableHandlerMethod.this.getBeanType();
		}

		/**
		 * Bridge to actual return value or generic type within the declared
		 * async return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
		 */
		@Override
		public MethodParameter getReturnValueType(@Nullable Object returnValue) {
			return this.returnType;
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.hasMethodAnnotation(annotationType);
		}
	}


	/**
	 * MethodParameter subclass based on the actual return value type or if
	 * that's null falling back on the generic type within the declared async
	 * return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
	 */
	private class ConcurrentResultMethodParameter extends HandlerMethodParameter {

		@Nullable
		private final Object returnValue;

		private final ResolvableType returnType;

		public ConcurrentResultMethodParameter(Object returnValue) {
			super(-1);
			this.returnValue = returnValue;
			this.returnType = (returnValue instanceof ReactiveTypeHandler.CollectedValuesList ?
					((ReactiveTypeHandler.CollectedValuesList) returnValue).getReturnType() :
					ResolvableType.forType(super.getGenericParameterType()).getGeneric());
		}

		public ConcurrentResultMethodParameter(ConcurrentResultMethodParameter original) {
			super(original);
			this.returnValue = original.returnValue;
			this.returnType = original.returnType;
		}

		@Override
		public Class<?> getParameterType() {
			if (this.returnValue != null) {
				return this.returnValue.getClass();
			}
			if (!ResolvableType.NONE.equals(this.returnType)) {
				return this.returnType.toClass();
			}
			return super.getParameterType();
		}

		@Override
		public Type getGenericParameterType() {
			return this.returnType.getType();
		}

		@Override
		public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
			// Ensure @ResponseBody-style handling for values collected from a reactive type
			// even if actual return type is ResponseEntity<Flux<T>>
			return (super.hasMethodAnnotation(annotationType) ||
					(annotationType == ResponseBody.class &&
							this.returnValue instanceof ReactiveTypeHandler.CollectedValuesList));
		}

		@Override
		public ConcurrentResultMethodParameter clone() {
			return new ConcurrentResultMethodParameter(this);
		}
	}

}
