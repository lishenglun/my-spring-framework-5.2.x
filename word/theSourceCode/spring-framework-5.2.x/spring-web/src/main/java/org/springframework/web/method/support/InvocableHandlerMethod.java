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

package org.springframework.web.method.support;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Extension of {@link HandlerMethod} that invokes the underlying method with
 * argument values resolved from the current HTTP request through a list of
 * {@link HandlerMethodArgumentResolver}. —— {@link HandlerMethod} 的扩展，它使用从当前 HTTP 请求通过 {@link HandlerMethodArgumentResolver} 列表解析的参数值调用底层方法。
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class InvocableHandlerMethod extends HandlerMethod {

	private static final Object[] EMPTY_ARGS = new Object[0];

	// WebDataBinderFactory类型，可以创建WebDataBinder，用于参数解析器ArgumentResolver中
	@Nullable
	private WebDataBinderFactory dataBinderFactory;

	// 用于解析参数
	private HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();

	// 获取参数名，用于MethodParameter中
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	/**
	 * Create an instance from a bean instance and a method.
	 */
	public InvocableHandlerMethod(Object bean, Method method) {
		super(bean, method);
	}

	/**
	 * Construct a new handler method with the given bean instance, method name and parameters.
	 * @param bean the object bean
	 * @param methodName the method name
	 * @param parameterTypes the method parameter types
	 * @throws NoSuchMethodException when the method cannot be found
	 */
	public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes)
			throws NoSuchMethodException {

		super(bean, methodName, parameterTypes);
	}


	/**
	 * Set the {@link WebDataBinderFactory} to be passed to argument resolvers allowing them to create
	 * a {@link WebDataBinder} for data binding and type conversion purposes.
	 * @param dataBinderFactory the data binder factory.
	 */
	public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
		this.dataBinderFactory = dataBinderFactory;
	}

	/**
	 * Set {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers} to use to use for resolving method argument values.
	 */
	public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
		this.resolvers = argumentResolvers;
	}

	/**
	 * Set the ParameterNameDiscoverer for resolving parameter names when needed
	 * (e.g. default request attribute name).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}


	/**
	 * 执行方法
	 *
	 * Invoke the method after resolving its argument values in the context of the given request.
	 * <p>Argument values are commonly resolved through
	 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 * The {@code providedArgs} parameter however may supply argument values to be used directly,
	 * i.e. without argument resolution. Examples of provided argument values include a
	 * {@link WebDataBinder}, a {@link SessionStatus}, or a thrown exception instance.
	 * Provided argument values are checked before argument resolvers.
	 * <p>Delegates to {@link #getMethodArgumentValues} and calls {@link #doInvoke} with the
	 * resolved arguments.
	 * @param request the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type, not resolved
	 * @return the raw value returned by the invoked method
	 * @throws Exception raised if no suitable argument resolver can be found,
	 * or if the method raised an exception
	 * @see #getMethodArgumentValues
	 * @see #doInvoke
	 */
	@Nullable
	public Object invokeForRequest(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {

		// 感悟：所有的方法，不管用什么注解修饰的，在调用执行的时候，都要经过这2个核心步骤来进行处理。这就它的核心标准流程
		// 题外：执行@ModelAttribute、@InitBinder、控制器方法(Controller方法)的具体方法，都会调这里。

		/* 1、准备方法需要的参数值 */
		Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
		if (logger.isTraceEnabled()) {
			logger.trace("Arguments: " + Arrays.toString(args));
		}

		/* 2、调用具体的方法 */
		// 注意：内部会捕获请求调用的异常
		return doInvoke(args);
	}

	/**
	 * Get the method argument values for the current request, checking the provided
	 * argument values and falling back to the configured argument resolvers.
	 * <p>The resulting array will be passed into {@link #doInvoke}.
	 * @since 5.1.2
	 */
	protected Object[] getMethodArgumentValues(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs/* 提供的参数值 */) throws Exception {

		/* 1、获取方法所有的参数 */

		MethodParameter[] parameters = getMethodParameters();

		/* 2、如果方法不存在参数，就返回空值 */

		// 判断方法是否有参数
		if (ObjectUtils.isEmpty(parameters)) {
			// 方法没有参数，就返回空值
			return EMPTY_ARGS;
		}

		/* 3、如果方法存在参数，就挨个解析所有的参数，得到对应的参数值 */
		// 用于保存解析出参数的值
		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			// 方法参数对象
			MethodParameter parameter = parameters[i];
			// 给方法参数对象，设置参数名称发现器
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);

			/* 4、如果有提供的参数值，就使用提供的参数值 */

			// 如果对应类型的参数已经在providedArgs中提供了，则直接设置到parameter
			args[i] = findProvidedArgument(parameter, providedArgs/* 提供的参数值 */);
			if (args[i] != null) {
				continue;
			}

			/* 5、如果没有提供的参数值，就遍历所有的参数解析器，找到能够解析该参数的参数解析器；然后用参数解析器去解析参数，得到参数值 */

			// 5.1、先遍历所有的参数解析器，判断是否有对应的"参数解析器"支持解析"该方法参数"
			// >>> 如果不存在"给定方法参数"对应的"参数解析器"就报错！
			if (!this.resolvers.supportsParameter(parameter)) {

				throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"/* 没有合适的解析器 */));
			}
			try {
				/**
				 * 题外：parameter, mavContainer, request, this.dataBinderFactory：把所有可能包含参数的地方传入进去
				 */
				// 5.2、如果存在"参数解析器"支持解析"该方法参数"，就使用该"参数解析器"去解析"参数"，得到参数值
				args[i] = this.resolvers.resolveArgument(parameter, mavContainer, request, this.dataBinderFactory);
			}
			catch (Exception ex) {
				// Leave stack trace for later, exception may actually be resolved and handled...
				// 上面的翻译：留下堆栈跟踪，以供以后使用，实际上可能会解决和处理异常...
				if (logger.isDebugEnabled()) {
					String exMsg = ex.getMessage();
					if (exMsg != null && !exMsg.contains(parameter.getExecutable().toGenericString())) {
						logger.debug(formatArgumentError(parameter, exMsg));
					}
				}
				throw ex;
			}
		}

		/* 6、最后是返回方法参数值 */

		return args;
	}

	/**
	 * Invoke the handler method with the given argument values.
	 */
	@Nullable
	protected Object doInvoke(Object... args) throws Exception {
		// 强制方法可调用（强制把方法变为一个可访问的方法）
		ReflectionUtils.makeAccessible(getBridgedMethod());
		try {
			// 实际执行请求对应的方法（我们接收请求和处理请求的方法）
			return getBridgedMethod().invoke(getBean(), args);
		}
		catch (IllegalArgumentException/* 非法参数异常 */ ex) {
			assertTargetBean(getBridgedMethod(), getBean(), args);
			String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
			throw new IllegalStateException(formatInvokeError(text, args), ex);
		}
		catch (InvocationTargetException/* 调用目标异常 */ ex) {
			// Unwrap for HandlerExceptionResolvers ... —— 为HandlerExceptionResolvers解包 ...

			// 获取抛出的具体异常
			Throwable targetException = ex.getTargetException();
			// 判断到底是哪种类型异常
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else if (targetException instanceof Error) {
				throw (Error) targetException;
			}
			else if (targetException instanceof Exception) {
				throw (Exception) targetException;
			}
			else {
				throw new IllegalStateException(formatInvokeError("Invocation failure", args), targetException);
			}
		}
	}

}
