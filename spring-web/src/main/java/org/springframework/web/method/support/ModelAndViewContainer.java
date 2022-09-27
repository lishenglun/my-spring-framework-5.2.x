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

package org.springframework.web.method.support;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.SimpleSessionStatus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 在控制器的调用过程中， 记录视图和model信息
 *
 *
 * 在一个controller方法的执行过程中，记录一下经过HandlerMethodArgumentResolver、HandlerMethodReturnValueHandler处理之后的model和view
 *
 * 题外：model：数据模型。保存的是前后端交互时返回的数据结果值
 * 题外：view：视图。返回给前端的页面是哪个
 *
 * Records model and view related decisions made by
 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers} and
 * {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers} during the course of invocation of
 * a controller method.
 *
 * 记录控制器方法调用过程中{@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}
 * 和{@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}做出的模型和视图相关决策。
 *
 * <p>The {@link #setRequestHandled} flag can be used to indicate the request
 * has been handled directly and view resolution is not required.
 *
 * <p>A default {@link Model} is automatically created at instantiation.
 * An alternate model instance may be provided via {@link #setRedirectModel}
 * for use in a redirect scenario. When {@link #setRedirectModelScenario} is set
 * to {@code true} signalling a redirect scenario, the {@link #getModel()}
 * returns the redirect model instead of the default model.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ModelAndViewContainer {

	// 重定向时，是否忽略默认的模型
	// （1）为false，代表不忽略，也就是重定向时，采用默认的模型
	// （2）为true，代表忽略，则在处理器返回redirect视图时，一定不适用defaultModel
	private boolean ignoreDefaultModelOnRedirect/* 重定向时，忽略默认的模型 */ = false;

	/**
	 * 1、在ViewNameMethodReturnValueHandler中，是把String类型的返回值，作为视图名称，设置到这个变量中
	 */
	// 视图对象，object类型，可以是实际视图，也可以是string类型的逻辑视图
	@Nullable
	private Object view;

	/**
	 * 我们在进行方法的调用完成之后，最终可能会返回一些具体的数据结果值，我们写这些数据结果值的时候，你会返回什么类型，
	 * 可以返回ModelAndView、Model、HashMap，不管返回的是这3种类型中的哪一个，最终我们会把这3个对象，都进行类型的转换，
	 * 转换成BindingAwareModelMap。我们最终在返回model模型的时候，用的都是BindingAwareModelMap。所以该对象在整个处理过程中非常重要
	 */
	// 默认使用的model对象,使用的情况最多
	private final ModelMap defaultModel = new BindingAwareModelMap();

	// redirect类型的model
	// 重定向时使用的model
	@Nullable
	private ModelMap redirectModel/* 重定向模型 */;

	// 处理器，是否返回redirect视图的标志
	// false：代表不是返回重定向model
	private boolean redirectModelScenario/* 重定向模型场景 */ = false;

	// 用于设置sessionAttribute使用完的标志
	@Nullable
	private HttpStatus status;

	private final Set<String> noBinding = new HashSet<>(4);

	// 禁止数据绑定的参数名单
	private final Set<String> bindingDisabled = new HashSet<>(4);

	private final SessionStatus sessionStatus = new SimpleSessionStatus();

	// 请求是否已经处理完毕的标识。
	// false：没有处理完成，还需要继续处理；
	// true：已经处理完成，不需要继续处理
	private boolean requestHandled = false;


	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively controller methods
	 * can declare an argument of type {@code RedirectAttributes} and use
	 * it to provide attributes to prepare the redirect URL.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false}.
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Set a view name to be resolved by the DispatcherServlet via a ViewResolver.
	 * Will override any pre-existing view name or View.
	 */
	public void setViewName(@Nullable String viewName) {
		this.view = viewName;
	}

	/**
	 * Return the view name to be resolved by the DispatcherServlet via a
	 * ViewResolver, or {@code null} if a View object is set.
	 */
	@Nullable
	public String getViewName() {
		return (this.view instanceof String ? (String) this.view : null);
	}

	/**
	 * Set a View object to be used by the DispatcherServlet.
	 * Will override any pre-existing view name or View.
	 */
	public void setView(@Nullable Object view) {
		this.view = view;
	}

	/**
	 * Return the View object, or {@code null} if we using a view name
	 * to be resolved by the DispatcherServlet via a ViewResolver.
	 */
	@Nullable
	public Object getView() {
		return this.view;
	}

	/**
	 * 判断当前的view是不是一个viewName（之所以方法名称叫"是不是视图引用"，因为viewName就是一个视图引用）
	 *
	 * Whether the view is a view reference specified via a name to be
	 * resolved by the DispatcherServlet via a ViewResolver.
	 *
	 * 视图是否是通过名称指定的视图引用，该名称将由 DispatcherServlet 通过 ViewResolver 解析。
	 *
	 */
	public boolean isViewReference() {
		// view是String类型，则当前view代表叫viewName
		return (this.view instanceof String);
	}

	/**
	 * 判断是使用默认模型，还是重定向模型。
	 *
	 * 返回要使用的视图，要么是默认的model，要么是传递redirect参数的model
	 * 根据useDefaultModel()来做判断
	 *
	 * Return the model to use -- either the "default" or the "redirect" model.
	 * The default model is used if {@code redirectModelScenario=false} or
	 * there is no redirect model (i.e. RedirectAttributes was not declared as
	 * a method argument) and {@code ignoreDefaultModelOnRedirect=false}.
	 */
	public ModelMap getModel() {
		/* 1、判断是使用默认模型，还是重定向模型 */

		/*

		1.1、返回默认模型
		（1）处理器返回的不是redirect视图，则为true
		（2）处理器返回的是redirect视图，但是redirectModel为空，并且重定向时，可以采用默认的模型，则为true

		*/
		if (useDefaultModel()) {
			// 返回默认模型
			return this.defaultModel;
		}
		/*

		1.2、返回重定向模型
		（1）处理器返回redirect视图，并且redirectModel不为null，
		（2）处理器返回的是redirect视图，并且ignoreDefaultModelOnRedirect也是true

		 */
		else {
			if (this.redirectModel == null) {
				this.redirectModel = new ModelMap();
			}
			// 返回重定向模型
			return this.redirectModel;
		}
	}


	/**
	 * 如果redirectModelScenario为false，也就是返回的不是redirect视图的时候一定返回的是defaultModel,
	 * 如果返回redirect视图的情况下需要根据redirectModel和ignoreDefaultModelOnRedirect的情况进一步判断，
	 * 如果redirectModel不为空和ignoreDefaultModelOnRedirect设置为true，
	 * 这两个条件中有一个亨利则返回redirectModel否则返回的就是defaultModel
	 *
	 * Whether to use the default model or the redirect model. —— 是使用默认模型model，还是重定向模型model。
	 */
	private boolean useDefaultModel() {
		/**
		 * 1、redirectModelScenario
		 * false：代表不是返回重定向model
		 * true：返回重定向model
		 *
		 * 2、ignoreDefaultModelOnRedirect：重定向时，是否忽略默认的模型
		 * false：代表不忽略，也就是重定向时，采用默认的模型
		 * true：代表忽略，则在处理器返回redirect视图时，一定不适用defaultModel
		 */
		// 不是返回重定向model || (重定向模型为null && 重定向时，不忽略默认的模型，也就是可以采用默认的模型)
		return (!this.redirectModelScenario || (this.redirectModel == null && !this.ignoreDefaultModelOnRedirect));
	}

	/**
	 * Return the "default" model created at instantiation.
	 * <p>In general it is recommended to use {@link #getModel()} instead which
	 * returns either the "default" model (template rendering) or the "redirect"
	 * model (redirect URL preparation). Use of this method may be needed for
	 * advanced cases when access to the "default" model is needed regardless,
	 * e.g. to save model attributes specified via {@code @SessionAttributes}.
	 * @return the default model (never {@code null})
	 * @since 4.1.4
	 */
	public ModelMap getDefaultModel() {
		return this.defaultModel;
	}

	/**
	 * Provide a separate model instance to use in a redirect scenario.
	 * <p>The provided additional model however is not used unless
	 * {@link #setRedirectModelScenario} gets set to {@code true}
	 * to signal an actual redirect scenario.
	 */
	public void setRedirectModel(ModelMap redirectModel) {
		this.redirectModel = redirectModel;
	}

	/**
	 * Whether the controller has returned a redirect instruction, e.g. a
	 * "redirect:" prefixed view name, a RedirectView instance, etc.
	 */
	public void setRedirectModelScenario(boolean redirectModelScenario) {
		this.redirectModelScenario = redirectModelScenario;
	}

	/**
	 * Provide an HTTP status that will be passed on to with the
	 * {@code ModelAndView} used for view rendering purposes.
	 * @since 4.3
	 */
	public void setStatus(@Nullable HttpStatus status) {
		this.status = status;
	}

	/**
	 * Return the configured HTTP status, if any.
	 * @since 4.3
	 */
	@Nullable
	public HttpStatus getStatus() {
		return this.status;
	}

	/**
	 * Programmatically register an attribute for which data binding should not occur,
	 * not even for a subsequent {@code @ModelAttribute} declaration.
	 * @param attributeName the name of the attribute
	 * @since 4.3
	 */
	public void setBindingDisabled(String attributeName) {
		this.bindingDisabled.add(attributeName);
	}

	/**
	 * Whether binding is disabled for the given model attribute.
	 * @since 4.3
	 */
	public boolean isBindingDisabled(String name) {
		return (this.bindingDisabled.contains(name) || this.noBinding.contains(name));
	}

	/**
	 * Register whether data binding should occur for a corresponding model attribute,
	 * corresponding to an {@code @ModelAttribute(binding=true/false)} declaration.
	 * <p>Note: While this flag will be taken into account by {@link #isBindingDisabled},
	 * a hard {@link #setBindingDisabled} declaration will always override it.
	 * @param attributeName the name of the attribute
	 * @since 4.3.13
	 */
	public void setBinding(String attributeName, boolean enabled) {
		if (!enabled) {
			this.noBinding.add(attributeName);
		}
		else {
			this.noBinding.remove(attributeName);
		}
	}

	/**
	 * Return the {@link SessionStatus} instance to use that can be used to
	 * signal that session processing is complete.
	 *
	 * 返回要使用的 {@link SessionStatus} 实例，该实例可用于表示会话处理已完成。
	 */
	public SessionStatus getSessionStatus() {
		return this.sessionStatus;
	}

	/**
	 * Whether the request has been handled fully within the handler, e.g.
	 * {@code @ResponseBody} method, and therefore view resolution is not
	 * necessary. This flag can also be set when controller methods declare an
	 * argument of type {@code ServletResponse} or {@code OutputStream}).
	 * <p>The default value is {@code false}.
	 *
	 * 请求是否已在处理程序中完全处理，例如{@code @ResponseBody} 方法，因此不需要视图解析。
	 * 当控制器方法声明类型为 {@code ServletResponse} 或 {@code OutputStream} 的参数时，也可以设置此标志。
	 * <p>默认值为 {@code false}。
	 */
	public void setRequestHandled(boolean requestHandled) {
		this.requestHandled = requestHandled;
	}

	/**
	 * Whether the request has been handled fully within the handler.
	 *
	 * 请求是否已在处理程序中完全处理。
	 */
	public boolean isRequestHandled() {
		return this.requestHandled;
	}

	/**
	 * Add the supplied attribute to the underlying model.
	 * A shortcut for {@code getModel().addAttribute(String, Object)}.
	 */
	public ModelAndViewContainer addAttribute(String name, @Nullable Object value) {
		getModel().addAttribute(name, value);
		return this;
	}

	/**
	 * Add the supplied attribute to the underlying model.
	 * A shortcut for {@code getModel().addAttribute(Object)}.
	 */
	public ModelAndViewContainer addAttribute(Object value) {
		getModel().addAttribute(value);
		return this;
	}

	/**
	 * Copy all attributes to the underlying model.
	 * A shortcut for {@code getModel().addAllAttributes(Map)}.
	 */
	public ModelAndViewContainer addAllAttributes(@Nullable Map<String, ?> attributes) {
		getModel().addAllAttributes(attributes);
		return this;
	}

	/**
	 * Copy attributes in the supplied {@code Map} with existing objects of
	 * the same name taking precedence (i.e. not getting replaced).
	 * A shortcut for {@code getModel().mergeAttributes(Map<String, ?>)}.
	 */
	public ModelAndViewContainer mergeAttributes(@Nullable Map<String, ?> attributes) {
		getModel().mergeAttributes(attributes);
		return this;
	}

	/**
	 * Remove the given attributes from the model.
	 */
	public ModelAndViewContainer removeAttributes(@Nullable Map<String, ?> attributes) {
		if (attributes != null) {
			for (String key : attributes.keySet()) {
				getModel().remove(key);
			}
		}
		return this;
	}

	/**
	 * Whether the underlying model contains the given attribute name.
	 * A shortcut for {@code getModel().containsAttribute(String)}.
	 */
	public boolean containsAttribute(String name) {
		return getModel().containsAttribute(name);
	}


	/**
	 * Return diagnostic information.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ModelAndViewContainer: ");
		if (!isRequestHandled()) {
			if (isViewReference()) {
				sb.append("reference to view with name '").append(this.view).append("'");
			}
			else {
				sb.append("View is [").append(this.view).append(']');
			}
			if (useDefaultModel()) {
				sb.append("; default model ");
			}
			else {
				sb.append("; redirect model ");
			}
			sb.append(getModel());
		}
		else {
			sb.append("Request handled directly");
		}
		return sb.toString();
	}

}
