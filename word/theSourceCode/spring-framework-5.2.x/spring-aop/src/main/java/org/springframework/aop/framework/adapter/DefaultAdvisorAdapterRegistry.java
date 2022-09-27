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

package org.springframework.aop.framework.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	// 持有AdvisorAdapter的List, 这个List中的adapter是与实现Spring AOP的advice增强功能相对应的
	// 题外：可由DefaultAdvisorAdapterRegistry#registerAdvisorAdapter()添加适配器
	// 题外：这里的适配器是为了扩展操作！
	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
	 * 创建一个新的DefaultAdvisorAdapterRegistry，注册众所周知的适配器。
	 */
	public DefaultAdvisorAdapterRegistry() {
		/* 注册某些advice对应的adapter */
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}

	/**
	 * 包装Advice为一个Advisor（Advisor不需要包装）
	 * <p>
	 * 题外：⚠️由于Spring中涉及过多的拦截器、增强器、增强方法，等方式来对逻辑进行增强，所以非常有必要统一封装成Advisor来进行代理的创建
	 *
	 * @param adviceObject 要包装的对象
	 * @return
	 * @throws UnknownAdviceTypeException
	 */
	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		// （1）如果要包装的对象本身就是Advisor类型，那么直接类型转换为Advisor进行返回，不需要包装
		if (adviceObject instanceof Advisor) {
			return (Advisor) adviceObject;
		}

		// （2）如果要包装的对象，不是Advisor，也不是Advice类型，则抛出异常
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}

		// 要包装的对象是Advice类型
		Advice advice = (Advice) adviceObject;
		// （3）要包装的对象是Advice类型，并且是MethodInterceptor类型，则直接创建一个DefaultPointcutAdvisor对象，包装这个"要包装的Advice对象"
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter. —— 众所周知，它甚至不需要适配器
			return new DefaultPointcutAdvisor(advice);
		}

		// （4）如果要包装的对象是Advice类型，但是不是MethodInterceptor类型，则遍历Advisor适配器，判断是否有对应的Advisor适配器支持Advice，如果有的话，就创建一个DefaultPointcutAdvisor对象，包装这个"要包装的Advice对象"
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported. —— 检查它是否受支持。
			if (adapter.supportsAdvice(advice)) {
				return new DefaultPointcutAdvisor(advice);
			}
		}

		// （5）如果要包装的对象是Advice类型，也不是MethodInterceptor类型，也没有适配器支持Advice，则报错！
		throw new UnknownAdviceTypeException(advice);
	}

	/**
	 * 获取Advisor中Advice对应的所有MethodInterceptor
	 * (1)如果Advice本身就是一个MethodInterceptor，就添加到interceptors中
	 * (2)找到支持返回当前Advice对应的MethodInterceptor的所有适配器，有的话，就从适配器当中获取当前Advice对应的MethodInterceptor，放入到interceptors中
	 */
	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		// 存储Advisor中Advice对应的所有MethodInterceptor
		// 题外：之所以是个集合，是因为，可以通过适配器添加多个Advice对应的MethodInterceptor
		List<MethodInterceptor> interceptors = new ArrayList<>(3);

		// 从Advisor中获取Advice，也就是具体的通知
		Advice advice = advisor.getAdvice();

		/* 1、如果Advice本身就是一个MethodInterceptor，就添加到interceptors中 */
		// Advice本身就是一个MethodInterceptor，就添加到interceptors中
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}

		/*

		2、遍历适配器，判断是否有支持返回当前Advice对应的MethodInterceptor的适配器，
		如果有的话，就通过适配器获取当前Advice对应的MethodInterceptor，放入到interceptors中

		*/
		// 题外：这里的适配器是为了扩展操作
		// 题外：默认适配器(参考DefaultAdvisorAdapterRegistry构造方法)：AfterReturnAdviceInterceptor、MethodBeforeAdviceInterceptor、ThrowsAdviceInterceptor
		for (AdvisorAdapter adapter : this.adapters) {
			// 判断是否有支持返回当前Advice对应的MethodInterceptor的适配器
			if (adapter.supportsAdvice(advice)) {
				// 通过适配器获取当前Advisor中Advice对应的MethodInterceptor
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}

		// 没有一个Advice对应的所有MethodInterceptor，则报错
		// 因为最终是要通过MethodInterceptor来执行逻辑的，所以没有就报错！
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}

		// 转换为数组，然后返回
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	// 新增的Advisor适配器
	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
