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

package org.springframework.web.method.annotation;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析namedValue类型的参数（有name的参数，如cookie，requestParam，requestHeader）的基类，
 * 主要功能有：
 * 1、获取name
 * 2、resolveDefaultValue,handleMissingValue,handlerNullValue
 * 3、调用模板方法resolveName,handleResolvedValue具体解析
 *
 * Abstract base class for resolving method arguments from a named value.
 * Request parameters, request headers, and path variables are examples of named
 * values. Each may have a name, a required flag, and a default value.
 *
 * <p>Subclasses define how to do the following:
 * <ul>
 * <li>Obtain named value information for a method parameter
 * <li>Resolve names into argument values
 * <li>Handle missing argument values when argument values are required
 * <li>Optionally handle a resolved value
 * </ul>
 *
 * <p>A default value string can contain ${...} placeholders and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 *
 * <p>A {@link WebDataBinder} is created to apply type conversion to the resolved
 * argument value if it doesn't match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Nullable
	private final ConfigurableBeanFactory configurableBeanFactory;

	@Nullable
	private final BeanExpressionContext/* Bean表达式上下文 */ expressionContext;

	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);


	public AbstractNamedValueMethodArgumentResolver() {
		this.configurableBeanFactory = null;
		this.expressionContext = null;
	}

	/**
	 * Create a new {@link AbstractNamedValueMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 */
	public AbstractNamedValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		this.expressionContext =
				(beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null);
	}

	/**
	 * 解析方法参数值（单个）
	 */
	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		// 根据参数获取"名称值信息"对象
		// 题外：参数处理的过程中，需要注意的一件事：每次在处理具体参数的时候，会把我们的参数封装成一个具体的对象，叫NamedValueInfo
		// >>> 在后续的处理过程中，用的都是这个NamedValueInfo
		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);
		// 如果方法参数是Optional类型，就返回Optional中嵌套的实际的方法参数
		MethodParameter nestedParameter = parameter.nestedIfOptional/* 嵌套如果可选 */();

		/* 1、获取参数名称 */
		// 通过"名称值信息"对象，获取参数名称
		Object resolvedName = resolveStringValue(namedValueInfo.name);
		if (resolvedName == null) {
			throw new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]");
		}

		// ⚠️以下是具体解析参数值

		/* 2、根据参数名称，去request里面，获取参数值 */
		// 根据参数名称，去request里面，获取参数值
		Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);

		/* 3、如果从request中未能获取到参数值，就看有没有默认值，有默认值就使用默认值 */
		// 如果没有从request中获取到参数值
		if (arg == null) {
			// 如果"名称值信息"对象中存在默认值，则使用默认值
			if (namedValueInfo.defaultValue != null) {
				arg = resolveStringValue/* 解析字符串值 */(namedValueInfo.defaultValue);
			}
			// 如果没有默认值，而且"名称值信息"对象中设置required为true，也就是说必须存在参数值，则进行缺失值处理，一般是报错！
			else if (namedValueInfo.required && !nestedParameter.isOptional()) {
				// 内部一般是报错
				handleMissingValue/* 处理缺失值 */(namedValueInfo.name, nestedParameter, webRequest);
			}
			// 处理空值：值为空，但参数类型是Boolean类型，就返回false；如果是基本数据类型，就报错
			arg = handleNullValue/* 处理空值 */(namedValueInfo.name, arg, nestedParameter.getNestedParameterType());
		}
		// 如果从request中获取到的参数值为空字符串，并且"名称值信息"对象中存在默认值，则使用默认值
		else if ("".equals(arg) && namedValueInfo.defaultValue != null) {
			arg = resolveStringValue/* 解析字符串值 */(namedValueInfo.defaultValue);
		}

		/* 4、如果binderFactory不为空，则用它创建binder并转换解析出的参数 */
		// 如果binderFactory不为空，则用它创建binder并转换解析出的参数
		if (binderFactory != null) {
			/*

			（1）为当前参数创建一个WebDataBinder对象，然后从当前Controller中的和全局的@InnitBinder方法中，筛选出适用的@InnitBinder方法，进行执行，来初始化WebDataBinder

			题外：@InnitBinder方法中初始化WebDataBinder可以做的事情，例如：往WebDataBinder中的类型转换器里面，注册属性编辑器
			题外：筛选出适用的@InnitBinder方法的规则：如果@InitBinder没有配置value属性值，或者@InitBinder中配置的value属性值包含当前"参数名称"，则代表适用。

			 */
			// binder=ServletRequestDataBinderFactory，
			// ServletRequestDataBinderFactory extends InitBinderDataBinderFactory，所以是走⚠️DefaultDataBinderFactory#createBinder()
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name/* 参数名称 */);

			/*

			（2）调用WebDataBinder转换参数值（类型转换器里面包含了属性编辑器）
			类型转换器会根据"参数类型"去找到对应的我们自定义的属性编辑器，然后用属性编辑器对参数值进行转换，最终返回转换后的数据，例如将：String类型的日期，转换成Data类型的日期！

			*/
			try {
				/**
				 * 1、WebDataBinder
				 *
				 * WebDataBinder extends DataBinder，间接实现PropertyEditorRegistry、TypeConverter
				 */
				// 调用WebDataBinder转换当前参数值
				// 题外：题外：之所以要转换，是因为，http提交过来的请求，无论是url还是表单方式提交过来的数据都是字符串，字符串是没办法进行直接使用的，所以需要把这些字符串转成我想要的类型
				arg = binder.convertIfNecessary(arg/* 参数值 */, parameter.getParameterType()/* 参数类型 */, parameter/* 单个方法参数 */);
			}
			catch (ConversionNotSupportedException ex) {
				throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			catch (TypeMismatchException ex) {
				throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
		}

		/* 5、对解析出的参数进行后置处理（一般是空实现） */
		handleResolvedValue/* 处理以及解析过的值 */(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		return arg;
	}

	/**
	 * Obtain the named value for the given method parameter.
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);
		if (namedValueInfo == null) {
			namedValueInfo = createNamedValueInfo(parameter);
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter. Implementations typically
	 * retrieve the method annotation by means of {@link MethodParameter#getParameterAnnotation(Class)}.
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	/**
	 * Create a new NamedValueInfo based on the given NamedValueInfo with sanitized values.
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;
		if (info.name.isEmpty()) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument type [" + parameter.getNestedParameterType().getName() +
						"] not available, and parameter name information not found in class file either.");
			}
		}
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * Resolve the given annotation-specified value,
	 * potentially containing placeholders and expressions.
	 *
	 * 解析给定的注解指定值，可能包含占位符和表达式。
	 */
	@Nullable
	private Object resolveStringValue(String value) {
		if (this.configurableBeanFactory == null) {
			return value;
		}
		// 看下有没有字符串解析器，有的话就用字符串值解析器，解析字符串
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		// 获取bean表达式解析器
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null || this.expressionContext == null) {
			return value;
		}
		// 用bean表达式解析器去解析值
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Resolve the given parameter type and value name into an argument value.
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return the resolved argument (may be {@code null})
	 * @throws Exception in case of errors
	 */
	@Nullable
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param request the current request
	 * @since 4.3
	 */
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter);
	}

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 */
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new ServletRequestBindingException("Missing argument '" + name +
				"' for method parameter of type " + parameter.getNestedParameterType().getSimpleName());
	}

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or an exception for other primitives.
	 *
	 * {@code null} 会导致 {@code boolean} 的 {@code false} 值或其他原语的异常。
	 */
	@Nullable
	private Object handleNullValue/* 处理空值 */(String name, @Nullable Object value, Class<?> paramType) {
		// 如果值为空
		if (value == null) {
			// 判断是不是Boolean类型，是的话就返回false值
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			// 如果不是Boolean类型，就判断是不是基本数据类型，是的话就报错
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer} (may be {@code null})
	 * @param webRequest the current request
	 */
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}


	/**
	 * Represents the information about a named value, including name, whether it's required and a default value.
	 *
	 * 表示有关命名值的信息，包括：名称、是否需要和默认值。
	 *
	 */
	protected static class NamedValueInfo {

		// 参数名
		private final String name;

		// 是否必须存在参数值
		private final boolean required;

		// 默认值
		@Nullable
		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, @Nullable String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
