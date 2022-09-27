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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Maximum number of suppressed exceptions to preserve. */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/*

	一级缓存（IOC容器、单例对象缓存）
	用于保存beanName和创建bean实例之间的关系


	⚠️存放的是成品对象

	*/
	/** Cache of singleton objects: bean name to bean instance. - 单例对象的高速缓存：bean名称到bean实例 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/*

	三级缓存
	用于保存BeanName和创建bean的工厂之间的关系

	⚠️存放的是ObjectFactory类型的lambd表达式，用于能生成代理对象

	*/
	/** Cache of singleton factories: bean name to ObjectFactory. - 单例工厂的缓存：Bean名称为ObjectFactory。*/
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/** Cache of early singleton objects: bean name to bean instance. - 早期单例对象的高速缓存：Bean名称到Bean实例。 */
	/*

	二级缓存
	保存BeanName和创建bean实例之间的关系，与singletonFactories的不同之处在于，当一个单例bean被放到这里之后，那么当bean还在创建过程中
	就可以通过getBean方法获取到，可以方便进行循环依赖的检测

	⚠️存放的是半成品对象

	 */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

	/**
	 * 已注册的单例集合：用来保存当前所有已经注册的bean
	 * Set of registered singletons, containing the bean names in registration order. - 已注册的单例集合，按注册顺序包含Bean名称。 */
	// 已注册的单例集合：标记当前beanName的bean已经创建好了，并且放入了一级缓存
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * 正在创建过程中的beanName集合
	 *
	 * Names of beans that are currently in creation. - 当前正在创建的bean的名称。 */
	// 记录正在创建过程中的bean对象
	// 用集合来存储bean正在创建过程中的状态，类似标识位的东西
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Names of beans currently excluded from in creation checks. */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Collection of suppressed Exceptions, available for associating related causes. */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** Flag that indicates whether we're currently within destroySingletons. */
	private boolean singletonsCurrentlyInDestruction = false;

	/**
	 * Disposable bean instances: bean name to disposable instance.
	 *
	 * 一次性 bean 实例：一次性实例的 bean 名称。
	 *
	 * 在容器关闭时（ac.close()），会被调用
	 *
	 * */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name to Set of bean names that the bean contains. */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Map between dependent bean names: bean name to Set of dependent bean names. */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	// Bean依赖关系Map
	/** Map between depending bean names: bean name to Set of bean names for the bean's dependencies. */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	/**
	 * 注册单例到一级缓存中
	 *
	 * @param beanName the name of the bean
	 * @param singletonObject the existing singleton object
	 * @throws IllegalStateException
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				// 如果beanName对应的对象已经存在了，那么就抛出异常！
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			// 添加到一级缓存
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 将beanName和singletonObject的映射关系添加到该工厂的单例缓存中
	 *
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			/* 添加到一级缓存 */
			// 将映射关系添加到单例对象的高速缓存中（IOC容器，一级缓存）
			this.singletonObjects.put(beanName, singletonObject);

			/* 只有完整对象才能放入到一级缓存中，所以，由于完整对象放入到一级缓存中了，所以接下来把二级缓存，和三级缓存里面的东西移除掉！ */

			/* 从三级缓存中移除 */
			// 移除beanName在单例工厂缓存中的数据
			this.singletonFactories.remove(beanName);
			/* 从二级缓存中移除 */
			// 移除beanName在早期单例对象的高速缓存的数据
			this.earlySingletonObjects.remove(beanName);

			/* 告诉你当前对象已经创建完成了(已经注册了)，之后用的时候，就可以直接从容器中获取，而不需要每次都创建新的 */
			// 将beanName添加到添加到"已注册的单例集合"中，标记当前beanName的bean已经创建好了，并且放入了一级缓存
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 如果需要，添加给定的"单例对象工厂"来构建指定的单例对象
	 *
	 * 此方法在创建实例后调用，将ObjectFactory保存到singletonFactories集合中，可以通过getObject()调用
	 *
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		/* 这个方法相当于就干了一件事，将singletonFactory存入三级缓存中 */

		/*

		1、如果一级缓存中，不存在当前beanName，就放入到三级缓存中

		之所以要一级缓存中不存在当前beanName，是因为如果一级缓存中存在了，那么代表已经是一个成品对象了，没必要再走三级缓存的逻辑生成代理对象了。

		*/

		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			/**
			 * 1、earlySingletonObjects：早期单例对象的高速缓存：Bean名称到Bean实例。
			 * 3、singletonFactories：单例对象的工厂：将bean放到bean工厂当中
			 * 4、registeredSingletons：已注册的单例名称集合，Set<String>
			 *
			 * Spring Bean对象有三种状态：
			 * 		bean名称
			 * 		bean实例
			 * 		bean工厂
			 */
			// 一级缓存是否包含这个beanName的名称
			if (!this.singletonObjects.containsKey(beanName)) {
				/* 不包含才能进来 */

				// 放到单例工厂里面
				this.singletonFactories/* 三级缓存 */.put(beanName, singletonFactory);
				// 删除早期单例
				this.earlySingletonObjects/* 二级缓存 */.remove(beanName);
				// 添加到已注册
				this.registeredSingletons.add(beanName);
			}
		}
	}

	/**
	 * 自己写的代码：
	 *
	 * 测试：只添加到二级缓存，用二级缓存解决循环依赖！
	 *
	 * @param beanName
	 * @param bean
	 */
	//protected void addSingletonFactoryTest(String beanName, Object bean) {
	//	// 删除早期单例
	//	this.earlySingletonObjects/* 二级缓存 */.put(beanName,bean);
	//	// 添加到已注册
	//	this.registeredSingletons.add(beanName);
	//}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}


	/**
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 *
	 * 返回在给定名称下注册的（原始）单例对象。 <p>检查已经实例化的单例，并允许提前引用当前创建的单例（解决循环引用）。
	 *
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not —— 是否应创建早期引用
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference/* 是否应创建早期引用 */) {
		/*

		1、从一级缓存(IOC容器、单例对象缓存)中获取bean对象，如果不为空就直接返回，不再进行初始化工作
		⚠️题外：每次获取到的完整对象都是放在一级缓存的，所以优先从一级缓存判断

		*/
		// 从单例对象缓存中获取beanName对应的单例对象
		Object singletonObject = this.singletonObjects.get(beanName);
		// 如果单例对象缓存中没有 && 该beanName对应的单例bean正在创建过程中
		if (singletonObject == null /* Ioc容器当中没有获取到 */ && isSingletonCurrentlyInCreation(beanName) /* 判断当前beanName对应的bean对象是否正在创建过程中 */) {

			/*

			2、如果一级缓存中没有，并且当前这个beanName对应的bean正在创建过程中，我才能够从二级缓存中获取（⚠️并不是说一级缓存中没有，就能够直接从二级缓存中获取）

			*/

			/**
			 * ⚠️IOC容器当中没有，但是正在创建，就需要等待其创建完成
			 * 由于在创建bean的时候，会获取this.singletonObjects的锁，所以如果bean在创建过程中，这里是获取不到this.singletonObjects锁的，所以会进行阻塞
			 */
			synchronized (this.singletonObjects) {
				// 从二级缓存中获取早期单例bean(
				// 题外：之所称为早期单例对象，是因为二级缓存里面的对象，都是通过三级缓存中的提前曝光的ObjectFactory创建出来后放入的，还未完全填充完属性和未初始化等操作)
				// 题外：只有这一个地方获取，用于在AutowiredAnnotationBeanPostProcessor#inject()当中检查要注入的bean是否和字段类型一致
				singletonObject = this.earlySingletonObjects.get(beanName);

				/*

				3、二级缓存中不存在，并且当前beanName允许早期bean引用，就从三级缓存中获取当前beanName的ObjectFactory。
				如果存在就通过ObjectFactory#getObject()获取对象；然后放入放入二级缓存中，供后期其它人引用；并从三级缓存中删除当前beanName的数据，以及返回当前bean对象；

				*/

				/**
				 * allowEarlyReference：是否允许早期引用，默认为true —— 也就是判断一下，你是否允许，在你没有完全创建成一个完整对象的时候，我就能够引用它
				 */
				// 如果在早期单例对象缓存中也没有，并且允许创建早期单例对象引用
				if (singletonObject == null && allowEarlyReference) {

					/**
					 * beanName对应的singletonFactory，是在 doCreateBean() ——> addSingletonFactory() 当中设置的
					 * ⚠️是在原生bean创建完成之后,注入属性之前调用的。所以singletonFactory包含的是原生bean
					 */
					// 当某些方法需要提前初始化的时候则会调用addSingletonFactory方法将对应的ObjectFactory初始化策略存储在singletonFactories
					ObjectFactory<?> singletonFactory = /* 2🎈 */this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						// 如果存在单例对象工厂，则通过工厂创建一个单例对象
						singletonObject = singletonFactory.getObject();
						/**
						 * 1、⚠️注意：singletonObject是原生bean对象，或者是一个原生bean对象的代理对象。
						 * 无论是原生bean对象，还是原生bean对象的代理对象，都是一个未初始化完成的对象，也就是一个半成品对象。
						 * 此时该对象还未填充完毕属性，以及未初始化。
						 * 当初始化完成当前这个bean对象时，就会放入一级缓存中，然后把二级缓存中的对象给删除掉！
						 * 2、⚠️注意：二级缓存和三级缓存的对象不能同时存在
						 * 3、⚠️题外：二级缓存只有这一个地方进行put
						 */
						// 将singletonObject放入二级缓存
						this.earlySingletonObjects.put(beanName, singletonObject);
						/**
						 * DefaultSingletonBeanRegistry#addSingleton()
						 */
						// 从三级缓存中移除
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		// beanName第一次来的时候都是null
		return singletonObject;
	}


	/**
	 * 自己写的代码
	 */
	//protected Object getSingleton(String beanName, boolean allowEarlyReference) {
	//	Object singletonObject = this.singletonObjects.get(beanName);
	//	if (singletonObject == null /* Ioc容器当中没有获取到 */ && isSingletonCurrentlyInCreation(beanName) /* 判断当前beanName对应的bean对象是否正在创建过程中 */) {
	//		synchronized (this.singletonObjects) {
	//			singletonObject = this.earlySingletonObjects.get(beanName);
	//			return singletonObject;
	//		}
	//	}
	//	return singletonObject!=null? singletonObject:null;
	//}

	/**
	 * 自己写的代码：
	 * 不使用三级缓存，通过二级缓存，也能实现代理对象的相互依赖！
	 */
	// protected Object getSingleton(String beanName, boolean allowEarlyReference) {
	//	Object singletonObject = this.singletonObjects.get(beanName);
	//	if (singletonObject == null /* Ioc容器当中没有获取到 */
	//			&& isSingletonCurrentlyInCreation(beanName) /* 判断当前beanName对应的bean对象是否正在创建过程中 */) {
	//		synchronized (this.singletonObjects) {
	//			/* 从二级缓存中获取 */
	//			singletonObject = this.earlySingletonObjects.get(beanName);
	//			if(singletonObject!=null && allowEarlyReference){
	//				/* 判断是否要代理 */
	//				AbstractBeanFactory abstractBeanFactory = (AbstractBeanFactory) this;
	//				RootBeanDefinition mbd = abstractBeanFactory.getMergedLocalBeanDefinition(beanName);
	//				Object exposedObject/* 暴露对象 */ = singletonObject;
	//				// mbd的synthetic属性：设置此bd是否是"synthetic"。一般是指只有AOP相关的pointCut配置或者Advice配置才会将synthetic设置为true。
	//				// 如果mbd不是synthetic && 此工厂拥有InstantiationAwareBeanPostProcessor实例
	//				if (!mbd.isSynthetic() && abstractBeanFactory.hasInstantiationAwareBeanPostProcessors()) {
	//					// 如果这个if没有进来，那么返回的就是普通的对象；
	//					// 如果这个if进来了，那么返回的将是代理对象！
	//					for (BeanPostProcessor bp : abstractBeanFactory.getBeanPostProcessors()) {
	//						if (bp instanceof SmartInstantiationAwareBeanPostProcessor/* 智能实例化感知BeanPostProcessor */) {
	//							SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
	//							/**
	//							 * getEarlyBeanReference()：解决循环依赖问题，通过此方法提前暴露一个合格的对象
	//							 */
	//							// 让exposedObject经过每个SmartInstantiationAwareBeanPostProcessor的包装
	//							exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
	//
	//							// 放入二级缓存中
	//							this.earlySingletonObjects.put(beanName, singletonObject);
	//						}
	//					}
	//				}
	//				// 返回最终经过层次包装后的对象
	//				return exposedObject;
	//			}
	//		}
	//	}
	//	return singletonObject!=null? singletonObject:null;
	//}

	/**
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		// 全局变量需要同步
		// 题外：由于synchronized是可重入锁，所以，如果当前bean正在创建过程中，然后进来创建依赖的bean时，是可以获取到锁的！
		synchronized (this.singletonObjects) {
			// ⚠️先从一级缓存中获取对象。一级缓存中存在就直接返回！
			Object singletonObject = this.singletonObjects.get(beanName);
			// 如果对象不存在，才需要进行bean的实例化
			if (singletonObject == null) {
				// 判断当前单例对象是否正在被销毁，如果是的话就抛出异常
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							// 当该工厂的单例处于破坏状态时，不允许创建单例 bean
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							// （不要在销毁方法实现中从 BeanFactory 请求 bean！）
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 记录当前对象的加载状态，做个正在创建的标记
				// 将单例标记为正在创建过程中(标记当前对象正在创建过程中)
				// 😊比如：将beanName添加到singletonsCurrentlyCreation这样一个set集合中，表示beanName对应的bean正在创建中
				beforeSingletonCreation/* 单例创建之前 */(beanName);

				// 是否生成了新的单例对象的标识
				// 如果创建完了一个单例对象，那么就为true，为true的话，后续就会做单例对象创建完成后的一些操作，比如：放入ioc容器
				boolean newSingleton = false;
				boolean recordSuppressedExceptions/* 记录抑制的异常 */ = (this.suppressedExceptions == null);
				// 如果没有抑制异常记录
				if (recordSuppressedExceptions) {
					// 对抑制的异常列表进行实例化（LinkedHashSet）
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					/**
					 * ⚠️singletonFactory.getObject();调用的是外面的lambda表达式
					 *
					 * 这里面调用了「AbstractBeanFactory#doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)」,内部：
					 * 		实例化对象
					 * 		注入属性
					 * 		初始化bean(如果没加Aop，则依旧是原生对象)
					 */
					// 开始进行bean对象的创建
					singletonObject = singletonFactory.getObject();
					// 生成了新的单例对象就标记为true，代表生成了新的单例对象（新创建的单例对象）
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					// 上面的翻译：单例对象是否同时隐式出现 -> 如果是，则继续执行，因为异常指示该状态。

					// 尝试从单例对象缓存(一级缓存)中获取beanName的单例对象
					singletonObject = this.singletonObjects.get(beanName);
					// 如果获取失败，则抛出异常
					if (singletonObject == null) {
						throw ex;
					}
				}
				// 捕捉Bean创建异常
				catch (BeanCreationException ex) {
					// 如果没有抑制异常记录
					if (recordSuppressedExceptions) {
						// 遍历抑制的异常列表
						for (Exception suppressedException : this.suppressedExceptions) {
							// 将抑制的异常对象添加到bean创建异常中，这样做的，就是相当于'因XXX异常导致了Bean创建异常'的说法
							ex.addRelatedCause(suppressedException);
						}
					}
					// 抛出异常
					throw ex;
				}
				finally {
					// 如果没有抑制异常记录
					if (recordSuppressedExceptions/* 判断是否有对应的异常记录 */) {
						// 将抑制的异常列表设置为nul，因为suppressedExceptions是对应单个Bean的异常记录，置为null，可防止异常信息的混乱
						this.suppressedExceptions = null;
					}
					// 😊创建bean的过程结束了，所以从"正在创建过程中的bean集合"里面移除掉当前bean
					// 移除缓存中对该bean的正在加载状态的记录
					afterSingletonCreation(beanName);
				}

				// 判断当前对象，是不是新创建的单例对象，是的话就添加到一级缓存当中去，并且清空二三级缓存
				if (newSingleton /* 初始化完成了则为true */) {
					/**
					 * ⚠️⚠️⚠️内部代码如下：
					 * 	// 放入IOC容器。singletonObjects：IOC容器
					 * 	this.singletonObjects.put(beanName, singletonObject);
					 * 	this.singletonFactories.remove(beanName);
					 * 	this.earlySingletonObjects.remove(beanName);
					 * 	// 添加到已注册的单例集合。registeredSingletons：已注册的单例集合
					 * 	this.registeredSingletons.add(beanName);
					 */
					// 将beanName和singletonObject的映射关系添加到该工厂的单例缓存中
					addSingleton(beanName /* bean名称 */, singletonObject/* bean对象 */);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 * 从该工厂的单例缓存中删除具有给定名称的Bean，以便在创建失败时清除急于注册的单例。
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		/* bean初始化完成之后 */
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 返回beanName对应的单例bean是否正在创建中（在整个工厂内）
	 *
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		// singletonsCurrentlyInCreation.add(beanName)，
		// 在AbstractBeanFactory#doGetBean() ——> getSingleton(String beanName, ObjectFactory<?> singletonFactory) ——> beforeSingletonCreation()当中添加的

		// 从当前正在创建的bean名称set集合中判断beanName是否在集合中
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 创建单例之前的回调
	 *
	 * 判断inCreationCheckExclusions和singletonsCurrentlyInCreation集合中是否包含当前beanName，包含就抛出异常
	 *
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		/**
		 * singletonsCurrentlyInCreation作用：记录当前单例对象正在被创建中
		 */
		// 如果"当前在创建检查中的排除bean名列表"中不包含该beanName，且将beanName添加到"当前正在创建的bean名称列表"后，
		// 出现beanName已经存在"当前正在创建的bean名称列表"中，那么就报错
		if (!this.inCreationCheckExclusions/* 在创建检查排除 */.contains(beanName) &&
				!this.singletonsCurrentlyInCreation.add(beanName)/* 当前正在创建的bean的名称 */) {
			throw new BeanCurrentlyInCreationException/* 当前正在创建的Bean异常 */(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 注册beanName与dependentBeanName的依赖关系
	 *
	 * 也就是存储依赖者与被依赖者的关系，key为依赖者的bean名称，value为被依赖者的名称，
	 * 比如：Person中依赖Address，那么就是存储的key为person，value为address
	 *
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName/* address */, String dependentBeanName/* person */) {
		// 获取name的最终别名或者是全类名
		String canonicalName = canonicalName(beanName);

		// 使用存储bean名到该bean名所要依赖的bean名的Map作为锁，保证线程安全
		synchronized (this.dependentBeanMap) {
			// 获取canonicalName对应的用于存储依赖bean名的set集合，如果没有就创建一个LinkedHashSet，并与canonicalName绑定到dependentBeanMap中
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			// 如果dependentBeans已经添加过来了dependentBeanName，就结束该方法，不执行后面操作
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		// 使用Bean依赖关系Map作为锁，保证线程安全
		synchronized (this.dependenciesForBeanMap) {
			// 添加dependentBeanName依赖于canonicalName的映射关系到存储bean名到依赖于该bean名的bean名的Map中
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName/* person */, k -> new LinkedHashSet<>(8)/* address */);
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * 确定是否已为给定名称注册了依赖bean。
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			// 将当前是否在destroySingletons中的标志设置为true，表明正在destroySingletons
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			// 复制disposableBeans的key集合成一个String数组
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			// ⚠️销毁disposableBeanNames[i]。
			// 先销毁依赖于disposableBeanNames[i]的bean，然后再销毁bean
			destroySingleton(disposableBeanNames[i]);
		}

		// 将以下几个map的数据全部清空

		// 清空在包含的Bean名称之间映射：bean名称-Bean包含的Bean名称集合
		this.containedBeanMap.clear();
		// 清空在包含的Bean名称之间映射：bean名称-一组相关的Bean名称
		this.dependentBeanMap.clear();
		// 清空在包含的Bean名称之间映射：bean名称-bean依赖项的bean名称集
		this.dependenciesForBeanMap.clear();

		// 清楚此注册表中所有缓存的单例实例
		clearSingletonCache();
	}

	/**
	 * 清楚此注册表中所有缓存的单例实例
	 *
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		// ⚠️
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				// ⚠️DisposableBeanAdapter#destroy()
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
