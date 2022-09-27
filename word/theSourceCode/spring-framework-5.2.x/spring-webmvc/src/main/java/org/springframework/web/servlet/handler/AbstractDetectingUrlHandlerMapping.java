/*
 * Copyright 2002-2012 the original author or authors.
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
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.ObjectUtils;

/**
 * 将容器所有的bean都拿出来，按一定规则注册到父类的map中
 * 此实现类也是通过重写initApplicationContext方法来注册handler，内部调用了detectHandlers方法
 *
 * Abstract implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface, detecting URL mappings for handler beans through introspection of all
 * defined beans in the application context.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #determineUrlsForHandler
 */
public abstract class AbstractDetectingUrlHandlerMapping extends AbstractUrlHandlerMapping {

	private boolean detectHandlersInAncestorContexts = false;


	/**
	 * 是否只扫描可访问的handler
	 *
	 * Set whether to detect handler beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only handler beans in the current ApplicationContext
	 * will be detected, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 */
	public void setDetectHandlersInAncestorContexts(boolean detectHandlersInAncestorContexts) {
		this.detectHandlersInAncestorContexts = detectHandlersInAncestorContexts;
	}


	/**
	 * Calls the {@link #detectHandlers()} method in addition to the
	 * superclass's initialization.
	 */
	@Override
	public void initApplicationContext() throws ApplicationContextException {
		/* 1、注册处理器 */
		super.initApplicationContext();
		/**
		 * Controller在检测的时候有两个分支：一个是通过url，一个是通过Method。当前检测处理器的时候是属于哪一个分支？
		 * 答：属于根据url的方式来进行匹配的（看当前类名就知道了）
		 */
		/* 2、注册处理器 */
		detectHandlers();
	}

	/**
	 * 根据配置的detectHandlersInAncestorContexts参数从springmvc容器或者父容器中找到所有bean的beanName，然后使用determineUrlsForHandler方法
	 * 对每个beanName解析出对应的urls，如果解析结果不为空，则解析出urls和beanName注册到父类的map中，
	 *
	 * Register all handlers found in the current ApplicationContext.
	 * <p>The actual URL determination for a handler is up to the concrete
	 * {@link #determineUrlsForHandler(String)} implementation. A bean for
	 * which no such URLs could be determined is simply not considered a handler.
	 * @throws org.springframework.beans.BeansException if the handler couldn't be registered
	 * @see #determineUrlsForHandler(String)
	 */
	protected void detectHandlers() throws BeansException {
		// 题外：这个ApplicationContext是属于spring mvc容器的
		ApplicationContext applicationContext = obtainApplicationContext();

		/* 1、获取所有容器中所有bean的名字 */

		// 获取所有容器中所有bean的名字
		String[] beanNames = (this.detectHandlersInAncestorContexts ?
				// 注意：这里获取的是Object类型，代表把容器中存在的所有对象都获取过来
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class) :
				applicationContext.getBeanNamesForType(Object.class));

		/*

		2、解析每一个beanName的和它的别名，看beanName和别名是不是以"/"开头，如果是，就证明是一个url。
		获取该beanName和别名中的所有url；如果可以得出url，就证明该ban是一个handler，就把它注册到handlerMap中

		*/

		// Take any bean name that we can determine URLs for. —— 取任何我们可以确定URL的beanName
		// 解析每一个beanName，得出url；如果可以得出url，就证明它是一个handler，就把它注册到handlerMap中
		for (String beanName : beanNames) {
			/**
			 * 1、例如：BeanNameUrlHandlerMapping的解析逻辑：
			 * >>> （1）看下beanName是不是以"/"开头，是的话，就代表是一个url，则添加到urls中；
			 * >>> （2）再接着获取所有的别名，判断别名是不是以"/"开头，是的话，就代表是一个url，也把它添加到urls中。
			 * >>> 收集该beanName对应的所有url
			 */
			/* 2.1、解析beanName，得出url */
			String[] urls = determineUrlsForHandler/* 确定处理程序的网址 */(beanName);

			/* 2.2、如果可以解析得到url，则证明该bean是一个Handler，于是注册URL和对应Handler的映射关系到handlerMap中 */
			// 如果该bean存在url，则证明该bean是一个处理器，添加该处理器(handler)
			if (!ObjectUtils.isEmpty(urls)) {
				// URL paths found: Let's consider it a handler. —— 找到的URL路径：让我们认为它是一个处理程序
				// 注册URL和对应的handler到handlerMap中
				registerHandler(urls, beanName);
			}
		}

		if ((logger.isDebugEnabled() && !getHandlerMap().isEmpty()) || logger.isTraceEnabled()) {
			logger.debug("Detected " + getHandlerMap().size() + " mappings in " + formatMappingName());
		}
	}


	/**
	 * Determine the URLs for the given handler bean.
	 * @param beanName the name of the candidate bean
	 * @return the URLs determined for the bean, or an empty array if none
	 */
	protected abstract String[] determineUrlsForHandler(String beanName);

}
