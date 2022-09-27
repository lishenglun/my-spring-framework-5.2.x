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

package org.springframework.web.servlet.handler;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerExecutionChain;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * 根据url来进行匹配的handler抽象类，将url和对应的handler保存在一个map中，在getHandlerInternal方法中使用url从map中获取handler
 *
 * Abstract base class for URL-mapped {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Provides infrastructure for mapping handlers to URLs and configurable
 * URL lookup. For information on the latter, see "alwaysUseFullPath" property.
 *
 * <p>Supports direct matches, e.g. a registered "/test" matches "/test", and
 * various Ant-style pattern matches, e.g. a registered "/t*" pattern matches
 * both "/test" and "/team", "/test/*" matches all paths in the "/test" directory,
 * "/test/**" matches all paths below "/test". For details, see the
 * {@link org.springframework.util.AntPathMatcher AntPathMatcher} javadoc.
 *
 * <p>Will search all path patterns to find the most exact match for the
 * current request path. The most exact match is defined as the longest
 * path pattern that matches the current request path.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 16.04.2003
 */
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {

	/**
	 * 根路径（"/"）的处理器
	 */
	@Nullable
	private Object rootHandler;

	/**
	 * 使用后置的 / 匹配
	 */
	private boolean useTrailingSlashMatch = false;

	/**
	 * 是否延迟加载处理器，默认关闭
	 */
	private boolean lazyInitHandlers = false;

	/**
	 * 路径和处理器的映射
	 *
	 * key：url（以"/"开头的beanName或者别名，作为url）
	 * value：Handler
	 */
	private final Map<String, Object> handlerMap = new LinkedHashMap<>();


	/**
	 * Set the root handler for this handler mapping, that is,
	 * the handler to be registered for the root path ("/").
	 * <p>Default is {@code null}, indicating no root handler.
	 */
	public void setRootHandler(@Nullable Object rootHandler) {
		this.rootHandler = rootHandler;
	}

	/**
	 * Return the root handler for this handler mapping (registered for "/"),
	 * or {@code null} if none.
	 */
	@Nullable
	public Object getRootHandler() {
		return this.rootHandler;
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 * If enabled a URL pattern such as "/users" also matches to "/users/".
	 * <p>The default value is {@code false}.
	 */
	public void setUseTrailingSlashMatch(boolean useTrailingSlashMatch) {
		this.useTrailingSlashMatch = useTrailingSlashMatch;
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 */
	public boolean useTrailingSlashMatch() {
		return this.useTrailingSlashMatch;
	}

	/**
	 * Set whether to lazily initialize handlers. Only applicable to
	 * singleton handlers, as prototypes are always lazily initialized.
	 * Default is "false", as eager initialization allows for more efficiency
	 * through referencing the controller objects directly.
	 * <p>If you want to allow your controllers to be lazily initialized,
	 * make them "lazy-init" and set this flag to true. Just making them
	 * "lazy-init" will not work, as they are initialized through the
	 * references from the handler mapping in this case.
	 */
	public void setLazyInitHandlers(boolean lazyInitHandlers) {
		this.lazyInitHandlers = lazyInitHandlers;
	}

	/**
	 * 获取请求路径，根据请求路径去handlerMap中查找对应的Handler，
	 * 如果没找到，就去看下当前路径是不是根路径（"/"），如果是的，就获取一个RootDefault(根处理器)；如果不是，就获取一个默认的处理器；
	 * 无论如何，只要获取到了Handler，都会用这个Handler去创建一个HandlerExecutionChain进行返回，里面会包含handler、和PathExposingHandlerInterceptor
	 *
	 * Look up a handler for the URL path of the given request. —— 查找给定请求的 URL 路径的处理程序。
	 * @param request current HTTP request
	 * @return the handler instance, or {@code null} if none found
	 */
	@Override
	@Nullable
	protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
		/* 1、获取请求路径 */

		/**
		 * 题外：是用于匹配的URL有效路径，例如：项目访问路径是/springmvc，http请求是：http://127.0.0.1:8080/springmvc/userlist
		 * 那么获取到的请求路径就是/userlist，只有这个路径才能去匹配到对应的Controller
		 */
		// 获取请求路径
		String lookupPath/* 查找路径 */ = getUrlPathHelper().getLookupPathForRequest(request);
		request.setAttribute(LOOKUP_PATH/* org.springframework.web.servlet.HandlerMapping.lookupPath */, lookupPath);

		/* 2、根据请求路径，从handlerMap中获取Handler。如果获取到了，就创建一个HandlerExecutionChain进行返回，里面会包含handler、和PathExposingHandlerInterceptor。 */

		// ⚠️根据请求路径找寻handler
		// 注意：此处有可能不是直接从map中获取，因为很多handler都有通配符的写法，甚至有多个匹配项，此时需要做好选择
		Object handler = lookupHandler(lookupPath, request);

		/*

		3、如果根据请求路径，无法从handlerMap中获取handler，则判断一下当前的请求路径是不是根据路径("/")，如果是的话，就使用根处理器(RootHandler)；
		如果不是根路径，获取不到根处理器；则获取默认处理器（DefaultHandler）。
		如果获取到了根处理器或者默认处理器，则用处理器，创建一个HandlerExecutionChain，里面包含了handler、和PathExposingHandlerInterceptor。

		 */
		// 如果找不到处理器，则使用rootHandler或defaultHandler处理器
		if (handler == null) {
			// We need to care for the default handler directly, since we need to
			// expose the PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE for it as well.
			// 上面的翻译：我们需要直接关注默认处理程序，因为我们也需要为它公开PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE。
			// 题外：PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE：处理程序映射属性中的路径

			Object rawHandler = null;

			/* 3.1、如果是根路径("/")，则使用rootHandler根处理器 */
			if ("/".equals(lookupPath)) {
				// 如果请求的路径仅仅是“/”,那么使用根处理器（RootHandler）
				rawHandler = getRootHandler();
			}

			/* 3.2、如果没有找到handler，也不是根路径，则尝试获取默认处理器（DefaultHandler） */
			// 注意：默认处理器不一定能获取得到！
			if (rawHandler == null) {
				rawHandler = getDefaultHandler();
			}

			/* 3.3、如果获取到了根处理器或者是默认处理器，则️创建一个HandlerExecutionChain，里面包含了handler、和PathExposingHandlerInterceptor */
			if (rawHandler != null) {
				// Bean name or resolved handler? —— beanName或已解析的handler？

				// 如果找到的Handler是String类型，则从容器中找到该beanName对应的Controller bean
				if (rawHandler instanceof String) {
					String handlerName = (String) rawHandler;
					rawHandler = obtainApplicationContext().getBean(handlerName);
				}
				// 空方法，校验处理器。目前暂无子类实现该方法
				validateHandler(rawHandler, request);
				// ⚠️创建HandlerExecutionChain，里面包含了handler、和PathExposingHandlerInterceptor
				handler = buildPathExposingHandler(rawHandler, lookupPath, lookupPath, null);
			}
		}
		return handler;
	}

	/**
	 * Look up a handler instance for the given URL path.
	 * <p>Supports direct matches, e.g. a registered "/test" matches "/test",
	 * and various Ant-style pattern matches, e.g. a registered "/t*" matches
	 * both "/test" and "/team". For details, see the AntPathMatcher class.
	 * <p>Looks for the most exact pattern, where most exact is defined as
	 * the longest path pattern.
	 * @param urlPath the URL the bean is mapped to
	 * @param request current HTTP request (to expose the path within the mapping to)
	 * @return the associated handler instance, or {@code null} if not found
	 * @see #exposePathWithinMapping
	 * @see org.springframework.util.AntPathMatcher
	 */
	@Nullable
	protected Object lookupHandler(String urlPath, HttpServletRequest request) throws Exception {
		/*

		一、根据请求路径，从handlerMap中获取Handler。如果获取到了，就创建一个HandlerExecutionChain进行返回，里面包含了handler、和PathExposingHandlerInterceptor。

		 */

		/*

		1、直接匹配：直接根据请求路径，从handlerMap中获取Handler。
		如果获取到了，就创建一个HandlerExecutionChain进行返回，里面会包含handler、和PathExposingHandlerInterceptor。

		*/
		// Direct match? —— 直接匹配？
		// 根据url查找handler
		Object handler = this.handlerMap.get(urlPath);
		if (handler != null) {
			// Bean name or resolved handler? —— Bean名称或已解析的处理程序？
			// 如果找到的处理器是String类型，则从容器中找到该beanName对应的Bean作为处理器
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}

			// 校验处理器。空方法，目前暂无子类实现该方法
			validateHandler(handler, request);

			// ⚠️创建HandlerExecutionChain，里面包含了handler、和PathExposingHandlerInterceptor
			return buildPathExposingHandler/* 构建路径暴露Handler */(handler, urlPath, urlPath, null);
		}

		/*

		2、通过表达式进行匹配。也就是handler可能是通配符的写法，所以表达式匹配。此时会匹配到多个，然后从匹配到的多个中选取其中一个！
		不过需要注意的是，也是用请求路径，从handlerMap中匹配Handler，只是handler可能是通配符的写法，看是否能匹配得上。
		如果获取到了匹配的Handler，也是创建一个HandlerExecutionChain进行返回，里面会包含handler、和PathExposingHandlerInterceptor。

		题外：具体通过AntPathMatcher进行匹配！

		 */
		// Pattern match? —— 模式匹配？
		// 模式匹配，指的是：handler都有通配符的写法，甚至有多个匹配项，此时需要做好选择

		// 通过表达式进行匹配，具体通过AntPathMatcher实现
		List<String> matchingPatterns = new ArrayList<>();
		// 情况二，Pattern匹配合适的，并添加到 matchingPatterns 中
		for (String registeredPattern : this.handlerMap.keySet()) {
			// AntPathMatcher#match()
			if (getPathMatcher().match(registeredPattern, urlPath/* 请求路径 */)) {
				// 路径通过Pattern匹配成功
				matchingPatterns.add(registeredPattern);
			}
			else if (useTrailingSlashMatch()/* 使用尾随斜线匹配 */) {
				if (!registeredPattern.endsWith("/") && getPathMatcher().match(registeredPattern + "/", urlPath)) {
					matchingPatterns.add(registeredPattern + "/");
				}
			}
		}

		// 获得首个匹配（最优）的结果
		// 一个请求可以匹配到多个处理器，所以在进行验证的时候，要看一下，哪个是最符合我规则的！
		String bestMatch/* 最佳匹配 */ = null;
		// 获取比较器，通过比较器来进行比较
		Comparator<String> patternComparator = getPathMatcher().getPatternComparator(urlPath);
		if (!matchingPatterns.isEmpty()) {
			// 排序
			matchingPatterns.sort(patternComparator);
			if (logger.isTraceEnabled() && matchingPatterns.size() > 1) {
				logger.trace("Matching patterns " + matchingPatterns);
			}
			bestMatch = matchingPatterns.get(0);
		}

		if (bestMatch != null) {
			// 获得bestMatch对应的处理器
			handler = this.handlerMap.get(bestMatch);
			if (handler == null) {
				if (bestMatch.endsWith("/")) {
					handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
				}
				// 如果获得不到，抛出IllegalStateException异常
				if (handler == null) {
					throw new IllegalStateException(
							"Could not find handler for best pattern match [" + bestMatch + "]");
				}
			}
			// Bean name or resolved handler? —— beanName或已经解析的handler？
			// 如果找到的处理器是String类型，则从容器中找到该beanName对应的Bean作为处理器
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			// 校验处理器。空方法，目前暂无子类实现该方法
			validateHandler(handler, request);
			// 获得最匹配的路径
			String pathWithinMapping = getPathMatcher().extractPathWithinPattern(bestMatch, urlPath);

			// There might be multiple 'best patterns', let's make sure we have the correct URI template variables
			// for all of them
			// 上面的翻译：可能有多个“最佳模式”，让我们确保我们拥有所有这些模式的正确URI模板变量

			// 之前通过sort方法进行排序，然后拿第一个作为bestPatternMatch的，不过有可能有多个pattern的顺序相同，也就是sort方法返回0，这里就是处理这种情况
			Map<String, String> uriTemplateVariables = new LinkedHashMap<>();
			for (String matchingPattern : matchingPatterns) {
				if (patternComparator.compare(bestMatch, matchingPattern) == 0) {
					Map<String, String> vars = getPathMatcher().extractUriTemplateVariables(matchingPattern, urlPath);
					Map<String, String> decodedVars = getUrlPathHelper().decodePathVariables(request, vars);
					uriTemplateVariables.putAll(decodedVars);
				}
			}
			if (logger.isTraceEnabled() && uriTemplateVariables.size() > 0) {
				logger.trace("URI variables " + uriTemplateVariables);
			}

			// ⚠️创建HandlerExecutionChain，里面包含了handler、和PathExposingHandlerInterceptor
			return buildPathExposingHandler(handler, bestMatch, pathWithinMapping, uriTemplateVariables/* uri模板变量 */);
		}

		// No handler found...
		// 没有找到handler
		return null;
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param handler the handler object to validate
	 * @param request current HTTP request
	 * @throws Exception if validation failed
	 */
	protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
	}

	/**
	 * Build a handler object for the given raw handler, exposing the actual
	 * handler, the {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}, as well as
	 * the {@link #URI_TEMPLATE_VARIABLES_ATTRIBUTE} before executing the handler.
	 * <p>The default implementation builds a {@link HandlerExecutionChain}
	 * with a special interceptor that exposes the path attribute and uri template variables
	 * @param rawHandler the raw handler to expose
	 * @param pathWithinMapping the path to expose before executing the handler
	 * @param uriTemplateVariables the URI template variables, can be {@code null} if no variables found
	 * @return the final handler object
	 */
	protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
			String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables/* uri模板变量 */) {

		// 创建HandlerExecutionChain对象
		HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);

		// 添加PathExposingHandlerInterceptor拦截器，到chain中
		chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));

		if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
			// 添加UriTemplateVariablesHandlerInterceptor拦截器，到chain中
			chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
		}

		// 返回一个HandlerExecutionChain
		return chain;
	}

	/**
	 * Expose the path within the current mapping as request attribute.
	 * @param pathWithinMapping the path within the current mapping
	 * @param request the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
	 */
	protected void exposePathWithinMapping(String bestMatchingPattern, String pathWithinMapping,
			HttpServletRequest request) {

		request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatchingPattern);
		request.setAttribute(PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, pathWithinMapping);
	}

	/**
	 * Expose the URI templates variables as request attribute.
	 * @param uriTemplateVariables the URI template variables
	 * @param request the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
	 */
	protected void exposeUriTemplateVariables(Map<String, String> uriTemplateVariables, HttpServletRequest request) {
		request.setAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
	}

	@Override
	@Nullable
	public RequestMatchResult match(HttpServletRequest request, String pattern) {
		// 获得请求路径
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request, LOOKUP_PATH);
		// 模式匹配，若匹配，则返回 RequestMatchResult 对象
		if (getPathMatcher().match(pattern, lookupPath)) {
			return new RequestMatchResult(pattern, lookupPath, getPathMatcher());
		}
		else if (useTrailingSlashMatch()) {
			if (!pattern.endsWith("/") && getPathMatcher().match(pattern + "/", lookupPath)) {
				return new RequestMatchResult(pattern + "/", lookupPath, getPathMatcher());
			}
		}
		return null;
	}

	/**
	 * 注册url和bean的map，注册多个string的url到一个处理器中
	 *
	 * Register the specified handler for the given URL paths.
	 * @param urlPaths the URLs that the bean should be mapped to
	 * @param beanName the name of the handler bean
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
		Assert.notNull(urlPaths, "URL path array must not be null");
		/*

		1、遍历当前bean获取到的所有url，然后注册url和对应handler的映射关系

		 */
		// 遍历当前bean获取到的所有url，然后注册url和对应handler的映射关系
		for (String urlPath : urlPaths) {
			// 注册URL和对应的handler
			registerHandler(urlPath, beanName);
		}
	}

	/**
	 * 注册url和bean的map，将具体的handler注入到url对应的map中
	 *
	 * Register the specified handler for the given URL path.
	 * @param urlPath the URL the bean should be mapped to	—— bean应该映射到的URL
	 * @param handler the handler instance or handler bean name String —— 处理程序实例，或处理程序beanName(String)
	 * (a bean name will automatically be resolved into the corresponding handler bean)
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
		Assert.notNull(urlPath, "URL path must not be null");
		Assert.notNull(handler, "Handler object must not be null");
		Object resolvedHandler = handler;

		/* 1、根据beanName，从容器中获取handler */

		// Eagerly resolve handler if referencing singleton via name. —— 如果通过名称引用单例，则急切解析处理程序。

		// 如果处理器不是延迟加载的，并且handler是String类型，则从spring mvc容器中获取handler（注意：会走父容器）
		if (!this.lazyInitHandlers/* 是否延迟加载处理器(默认false，否，也就是立即加载) */ && handler instanceof String) {
			String handlerName = (String) handler;
			ApplicationContext applicationContext = obtainApplicationContext();
			// 判断是不是单例的
			if (applicationContext.isSingleton(handlerName)) {
				// 获取处理器
				resolvedHandler = applicationContext.getBean(handlerName);
			}
		}

		/* 2、根据url，从缓存中获取handler */

		Object mappedHandler = this.handlerMap.get(urlPath);

		/* 3、如果缓存中存在handler，并且与容器中新获取的handler不一样，则报错！如果一样，则不报错，也不需要再次添加。 */
		// 判断缓存中，是否已经存在URL对应的handler
		if (mappedHandler != null) {
			// 如果已经存在URL对应的handler，但是存在的handler不等于新解析到的handler，就抛出异常
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						// 无法将这个handler映射到URL路径，因为这个URL已经有对应的handler映射
						"Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
						"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		}
		/* 4、如果缓存中不存在handler */
		else {
			/* 4.1、如果URL是"/"根路径，则设置为根处理器 */
			// 如果URL是"/"根路径，则设置为根处理器 - rootHandler
			if (urlPath.equals("/")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Root mapping to " + getHandlerDescription(handler));
				}
				// 设置根处理器
				setRootHandler(resolvedHandler);
			}
			/* 4.2、如果URL是"/*"默认路径，则设置为默认处理器 */
			// 如果是/*路径，则设置为默认处理器
			else if (urlPath.equals("/*")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Default mapping to " + getHandlerDescription(handler));
				}
				// 设置默认处理器
				setDefaultHandler(resolvedHandler);
			}
			/* 4.3、其它URL，则将URL和handler绑定到handlerMap中 */
			else {
				// 其余的路径绑定关系则存入handlerMap中
				this.handlerMap.put(urlPath, resolvedHandler);
				if (logger.isTraceEnabled()) {
					logger.trace("Mapped [" + urlPath + "] onto " + getHandlerDescription(handler));
				}
			}
		}
	}

	private String getHandlerDescription(Object handler) {
		return (handler instanceof String ? "'" + handler + "'" : handler.toString());
	}


	/**
	 * Return the registered handlers as an unmodifiable Map, with the registered path
	 * as key and the handler object (or handler bean name in case of a lazy-init handler)
	 * as value.
	 * @see #getDefaultHandler()
	 */
	public final Map<String, Object> getHandlerMap() {
		return Collections.unmodifiableMap(this.handlerMap);
	}

	/**
	 * Indicates whether this handler mapping support type-level mappings. Default to {@code false}.
	 */
	protected boolean supportsTypeLevelMappings() {
		return false;
	}


	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE} attribute.
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class PathExposingHandlerInterceptor extends HandlerInterceptorAdapter {

		/** 最佳匹配的路径 */
		private final String bestMatchingPattern;

		/** 被匹配的路径 */
		private final String pathWithinMapping;

		public PathExposingHandlerInterceptor(String bestMatchingPattern, String pathWithinMapping) {
			this.bestMatchingPattern = bestMatchingPattern;
			this.pathWithinMapping = pathWithinMapping;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			/* 往请求里面设置一些属性值 */
			exposePathWithinMapping(this.bestMatchingPattern, this.pathWithinMapping, request);
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
			request.setAttribute(INTROSPECT_TYPE_LEVEL_MAPPING, supportsTypeLevelMappings());
			return true;
		}

	}

	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} attribute.
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class UriTemplateVariablesHandlerInterceptor extends HandlerInterceptorAdapter {

		private final Map<String, String> uriTemplateVariables;

		public UriTemplateVariablesHandlerInterceptor(Map<String, String> uriTemplateVariables) {
			this.uriTemplateVariables = uriTemplateVariables;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			/* 往请求里面设置一些属性值 */
			exposeUriTemplateVariables(this.uriTemplateVariables, request);
			return true;
		}
	}

}
