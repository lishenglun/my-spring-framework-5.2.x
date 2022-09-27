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

/**
 * Singleton to publish a shared DefaultAdvisorAdapterRegistry instance.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @see DefaultAdvisorAdapterRegistry
 */
public final class GlobalAdvisorAdapterRegistry {

	private GlobalAdvisorAdapterRegistry() {
	}

	/**
	 * 单例模式的使用，使用静态类遍历来保持一个唯一实例
	 *
	 * Keep track of a single instance so we can return it to classes that request it. —— 跟踪单个实例，以便我们可以将其返回给请求它的类。
	 */
	// 注意：⚠️里面会注册某些advice对应的adapter
	private static AdvisorAdapterRegistry instance = new DefaultAdvisorAdapterRegistry();

	/**
	 * 返回单例的DefaultAdvisorAdapterRegistry对象
	 *
	 * Return the singleton {@link DefaultAdvisorAdapterRegistry} instance. —— 返回单例{@link DefaultAdvisorAdapterRegistry} 实例。
	 */
	public static AdvisorAdapterRegistry getInstance() {
		return instance;
	}

	/**
	 * 重新创建一个新的DefaultAdvisorAdapterRegistry
	 *
	 * Reset the singleton {@link DefaultAdvisorAdapterRegistry}, removing any
	 * {@link AdvisorAdapterRegistry#registerAdvisorAdapter(AdvisorAdapter) registered}
	 * adapters.
	 */
	static void reset() {
		instance = new DefaultAdvisorAdapterRegistry();
	}

}
