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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * AbstractApplicationContext委托执行postProcessors()的工具类
 *
 * Delegate for AbstractApplicationContext's post-processor handling. —— AbstractApplicationContext的后处理器处理的委托。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate/* 后处理器的注册代表 */ {

	private PostProcessorRegistrationDelegate() {
	}


	/**
	 * 只是在spring的beanFactory初始化的过程中去做一些事情：把实现了BeanFactoryPostProcessor/BeanDefinitionRegistryPostProcessor接口的类调用一下
	 * 有程序员自定义的，也有spring自定义的实现
	 * <p>
	 * 题外：虽然该方法名称是叫invokeBeanFactoryPostProcessors()，但是它实际处理的接口有两个：BeanFactoryPostProcessor、BeanDefinitionRegistryPostProcessor
	 *
	 * @param beanFactory
	 * @param beanFactoryPostProcessors 自定义的
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any. - ⚠️如果有的话，首先调用BeanDefinitionRegistryPostProcessors。

		/*

		创建一个空集合，存储已经执行过的BDRPP beanName，避免后面重复执行这个BDRPP；以及避免重复执行BDRPP对应的BFPP
		>>> （1）因为BDRPP可以同时实现PriorityOrdered、Ordered接口，且PriorityOrdered extends Ordered，
		>>> 所以在执行完毕PriorityOrdered的BDRPP之后，再获取Order接口的BDRPP，依旧可以获取到已经执行过的PriorityOrdered接口的BDRPP，
		>>> 所以放入这个集合，就可以排除掉已经执行过的BDRPP，避免重复执行BDRPP
		>>> （2）因为BDRPP extends BFPP，在读取BFPP的时候，也会读取到对应的BDRPP的BFPP进行执行，但是BDRPP的BFPP已经执行过了，
		>>> 所以放入这个集合，避免重复执行BDRPP的BFPP

		 */
		Set<String> processedBeans = new HashSet<>();

		/*

		判断当前beanFactory是否是BeanDefinitionRegistry类型

		因为如果beanFactory不归属于BeanDefinitionRegistry类型，
		那么BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)就没法操作BeanDefinitionRegistry了，
		所以BDRPP是无效的，所以就不需要执行BDRPP，只需要执行BFPP即可

		 */
		// 题外：当前beanFactory=DefaultListableBeanFactory，实现了BeanDefinitionRegistry接口，所以为true
		// 题外：BeanDefinitionRegistry：对BeanDefinition进行CRUD操作的工具类
		// 题外：Registry翻译为注册的话我感觉不太对，因为所有的Registry表达的意思都是对前面这个对象进行一些CRUD操作
		if (beanFactory instanceof BeanDefinitionRegistry) {

			/* 如果beanFactory是BeanDefinitionRegistry类型 */

			// 类型转换
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

			/**
			 * 这里有一个注意点：有两个list，一个regularPostProcessors，一个registryProcessors
			 * registryProcessors中的 BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor，类型也属于BeanFactoryPostProcessor，为什么要分为两个list呢？
			 * 因为：BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor是在其基础上做了拓展，比只实现BeanFactoryPostProcessor多出一个方法，如果只是BeanFactoryPostProcessor集合，
			 * 		则无法调用BeanDefinitionRegistryPostProcessor的方法，所以分为两个list
			 */

			// 此处希望大家做一个区分，两个接口是不同的，BeanDefinitionRegistryPostProcessor是BeanFactoryPostProcessor的子集
			// BeanFactoryPostProcessor主要针对的操作对象是BeanFactory，而BeanDefinitionRegistryPostProcessor主要针对的操作对象是BeanDefinition

			/**
			 * 💡之所以定义两个集合：定义不同的集合分开之后，我能处理不同的对象。不过这两个对象，实际上都是为BeanFactoryPostProcessor服务的，
			 * 只不过registryProcessors的BeanDefinitionRegistryPostProcessor是执行其父类BeanFactoryPostProcessor
			 */

			/*

			存放外部的BeanFactoryPostProcessor（用户传进来的自定义的BFPP）（方法参数传入进来的）
			题外：放程序员自己定义的BeanFactoryPostProcessor类（也就是通过addBeanFactoryPostProcessor();方式添加进来的，当前这个bean对象不归属spring管理，是自己new的，spring识别不到）

			 */
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();

			/*

			存放所有的BeanDefinitionRegistryPostProcessor
			作用：执行所有BDRPP的BeanFactoryPostProcessor#postProcessBeanFactory()方法
			>>> 因为BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor，也要实现BeanFactoryPostProcessor#postProcessBeanFactory()
			>>> 所以这里专门存放一个集合，只用来执行实现了BDRPP的BeanFactoryPostProcessor#postProcessBeanFactory()方法

			 */
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			/* 一、执行BeanDefinitionRegistryPostProcessor */


			/**
			 * 这个循坏可省略，因为只有「annotationConfigApplicationContext.addBeanFactoryPostProcessor();」方法加入的才有，而这个方法基本不调用
			 */
			// 首先处理入参中的beanFactoryPostProcessors：遍历所有的beanFactoryPostProcessors，将BeanDefinitionRegistryPostProcessor和BeanFactoryPostProcessor区分开来
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				/*

				1、执行外部的(方法参数加入进来的)BeanDefinitionRegistryPostProcessor —— 用户传进来的自定义的BFPP

				外部的，也就是当前方法的beanFactoryPostProcessors参数里面的BeanDefinitionRegistryPostProcessor

				⚠️注意：只有用BeanFactory#addBeanFactoryPostProcessor()方式添加的BeanDefinitionRegistryPostProcessor，beanFactoryPostProcessors参数里面才有

				*/
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					// 如果是BeanDefinitionRegistryPostProcessor

					BeanDefinitionRegistryPostProcessor registryProcessor = (BeanDefinitionRegistryPostProcessor) postProcessor;
					// ⚠️⚠️⚠️️直接执行了BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					// 添加到BeanDefinitionRegistryPostProcessor集合的registryProcessors，用于后续执行它父接口的BeanFactoryPostProcessor#postProcessBeanFactory()
					/**
					 * 有一个注意点，就是，在下面的beanFactory.getBeanNamesForType()是获取不到当前的这个registryProcessor对象的，
					 * 因为这是通过addBeanFactoryPostProcessor(new BeanDefinitionRegistryPostProcessor());，new的方式添加进去的，是不存在对应的BeanDefinition的！
					 * 也是因为下面的beanFactory.getBeanNamesForType()是获取不到当前的这个registryProcessor对象的，所以要加入到registryProcessors集合当中！
					 */
					registryProcessors.add(registryProcessor);
				} else {
					// 否则，如果只是BeanFactoryPostProcessor，则添加到regularPostProcessors，用于后续统一执行外部的BFPP

					regularPostProcessors.add(postProcessor);
				}
			}

			/*

			2、执行内部的BeanDefinitionRegistryPostProcessor

			也就是非beanFactoryPostProcessors参数里面的，也就是非BeanFactory#addBeanFactoryPostProcessor();方式添加的BeanDefinitionRegistryPostProcessor

			*/

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 上面的翻译：不要在这里初始化FactoryBeans：我们需要保留所有未初始化的常规bean，以使bean工厂后处理器对其应用！
			// >>> 在实现PriorityOrdered，Ordered和其余优先级的BeanDefinitionRegistryPostProcessor之间分开。

			/* 存放当前将要执行的BDRPP（内部的，跟外部的没有关系） */
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			/* 2.1、先执行实现了PriorityOrdered的BeanDefinitionRegistryPostProcessor */

			/**
			 * getBeanNamesForType()：通过类型得到bean的名字
			 * 这里是通过BeanDefinitionRegistryPostProcessor类型得到其容器中的名称，
			 * 这里获取到的名称是org.springframework.context.annotation.internalConfigurationAnnotationProcessor
			 * internalConfigurationAnnotationProcessor代表了ConfigurationAnnotationProcessor的beanDefinition
			 */
			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered. - 首先，调用实现PriorityOrdered的BeanDefinitionRegistryPostProcessors。
			// 从当前容器获取到指定类型的bean名称(通过BeanDefinitionRegistryPostProcessor类型得到其容器中的名称)
			/**
			 * ⚠️如果有org.springframework.context.annotation.internalConfigurationAnnotationProcessor，那么就可以获取到它，
			 * >>> 因为internalConfigurationAnnotationProcessor代表的是{@link ConfigurationClassPostProcessor}的beanDefinition
			 * >>> ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,PriorityOrdered
			 * >>> 所以ConfigurationClassPostProcessor会放入currentRegistryProcessors并作为BeanDefinitionRegistryPostProcessor、BeanDefinitionPostProcessor进行执行！
			 */
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			// 1、ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor
			for (String ppName : postProcessorNames) {
				// 这里很关键的一点是，BeanDefinitionRegisterPostProcessor是有顺序的，所以spring在运行BeanDefinitionRegisterPostProcessor是有按照一定的顺序来执行的
				// 是先按照PriorityOrdered优先执行
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered/* 优先排序 */.class)) {
					// ⚠️获取名字对应的bean实例（所以在这里会创建对应的bean实例），添加到currentRegistryProcessors中
					/**
					 * 拿出spring自身实现了BeanDefinitionRegistryPostProcessor接口的类，放入currentRegistryProcessors
					 * 		也就是ConfigurationClassPostProcessor对象
					 *  ppName = org.springframework.context.annotation.internalConfigurationAnnotationProcessor
					 */
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 上面已经创建了BDRPP bean，且下面会执行；所以将要被执行的BDRPP对应的名称添加到processedBeans
					// 为了避免后面重复执行这个BDRPP；以及避免重复执行BFPP(因为BDRPP extends BFPP，在读取BFPP的时候，也会读取到对应的BDRPP)
					processedBeans.add(ppName);
				}
			}
			// 按照优先级进行排序操作（目前currentRegistryProcessors只有一条数据）
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到registryProcessors中，用于最后执行BeanFactoryPostProcessor#postProcessBeanFactory()
			// ⚠️题外：registryProcessors是自己定义的，currentRegistryProcessors是spring定义的，两个人进行合并
			registryProcessors.addAll(currentRegistryProcessors);
			// ⚠️遍历currentRegistryProcessors，执行️BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()
			// 题外：执行所有Spring自身的BeanDefinitionRegistryPostProcessors（目前只有ConfigurationAnnotationProcessor）
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 执行完毕之后，清空currentRegistryProcessors
			currentRegistryProcessors.clear();


			/* 2.2、执行实现了Ordered的BeanDefinitionRegistryPostProcessor */

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 调用所有实现Ordered接口的BeanDefinitionRegistryPostProcessors实现类
			// 找到所有实现BeanDefinitionRegistryPostProcessor接口的bean的beanName，此处需要重复查找的原因在于，
			// >>> 上面的invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);的执行过程中可能会新增其他的BeanDefinitionRegistryProcessors
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 检测是否实现了Ordered接口，并且还未创建过对应bean的
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					// 获取名字对应的bean实例，添加到currentRegistryProcessors中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将已经创建过的对应bean的BFPP添加到processedBeans，避免后续重复执行
					processedBeans.add(ppName);
				}
			}
			// 按照优先级进行排序操作
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);
			// ⚠️遍历currentRegistryProcessors，执行️BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 执行完毕之后，清空currentRegistryProcessors
			currentRegistryProcessors.clear();

			/* 2.3、执行没有实现任何排序接口的BeanDefinitionRegistryPostProcessor */

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear. - 最后，调用所有其他BeanDefinitionRegistryPostProcessor，直到没有其他的出现。
			// 最后调用所有剩下的BDRPP
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				// 找出所有实现BeanDefinitionRegistryPostProcessor接口的类
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					// 上面实现了PriorityOrdered、Ordered接口的BeanDefinitionRegistryPostProcessor都放入到了processedBeans中，
					// 所以如果一个BeanDefinitionRegistryPostProcessor不在processedBeans中，就是普通的BeanDefinitionRegistryPostProcessor了
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(
								// ⚠️获取我们自定义的继承了 BeanDefinitionRegistryPostProcessor 的对象
								beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class)
						);
						// 将已经创建过的对应bean的BFPP添加到processedBeans，避免后续重复执行
						processedBeans.add(ppName);
						// 设置为true，表示下面在执行invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry)的时候，
						// 可能会产生新的BeanDefinitionRegistryPostProcessor，所以需要设置为true，让其进入下一个while()，再执行一遍！
						reiterate = true;
					}
				}
				// ⚠️既然是执行没有实现任何排序接口的BDRPP，为什么这里还要排序呢？
				// 因为有可能在执行完invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry)后，
				// 新产生的BeanDefinitionRegistryPostProcessor有实现排序接口的，所以排序一下！
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				// 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			/**
			 * 回调所有的BeanFactoryPostProcessor
			 * 疑问点：registryProcessors中的类型是属于BeanDefinitionRegistryPostProcessor，为什么还要处理BeanDefinitionRegistryPostProcessor？
			 * 		解答：其实处理的不是BeanDefinitionRegistryPostProcessor的方法，由于BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor，
			 * 		     所以还有一个BeanFactoryPostProcessor的方法，也就是父类的方法需要回调处理，所以也属于BeanFactoryPostProcessor，所以需要执行registryProcessors
			 *
			 * 这里面还有一个重点⚠️，就是：创建cglib代理的逻辑在里面
			 */
			// Now, invoke the postProcessBeanFactory callback of all processors handled so far. - 现在，调用到目前为止已处理的所有处理器的postProcessBeanFactory回调。

			/* 二、执行BeanFactoryPostProcessor */

			/* 1.执行BeanDefinitionRegistryPostProcessor的BeanFactoryPostProcessor#postProcessBeanFactory() */
			/**
			 * ⚠️{@link ConfigurationClassPostProcessor}会在这里被执行！
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);

			/* 2、执行外部的BeanFactoryPostProcessor */

			// 执行自定义的
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		} else {
			/* 如果beanFactory不是BeanDefinitionRegistry类型 */

			// Invoke factory processors registered with the context instance. —— 调用向上下文实例注册的工厂处理器。

			/**
			 * 如果beanFactory不归属于BeanDefinitionRegistry类型，
			 * 那么BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)就没法操作BeanDefinitionRegistry了，
			 * 所以BeanDefinitionRegistryPostProcessor是无效的，所以就不需要执行BDRPP，只需要执行BFPP即可
			 */
			/* 执行外部的BeanFactoryPostProcessor */
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		/**
		 * 至此，入参beanFactoryPostProcessors和容器中的所有BeanDefinitionRegistryPostProcessor已经全部处理器完毕。
		 * 下面开始处理容器中所有的BeanFactoryPostProcessor
		 */

		/* 3、执行内部的BeanFactoryPostProcessor */

		/**
		 * 疑问1：为什么在每次执行BeanFactoryPostProcessor时，不需要像执行BeanDefinitionRegistryPostProcessor一样，
		 * 都执行一下beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class)，从里面获取BeanFactoryPostProcessor呢？
		 *
		 * 解答：BeanDefinitionRegistryPostProcessor内部可能会新增BeanDefinitionRegistryPostProcessor对应的BeDefinition，
		 * 但是BeanFactoryPostProcessor是无法新增BeanFactoryPostProcessor对应的BeanDefinition的！
		 * 也就是，在执行BeanFactoryPostProcessor的途中，是不会新增BeanFactoryPostProcessor的，所以不需要
		 */

		/**
		 * 疑问2：为什么priorityOrderedPostProcessors集合存储的是BeanFactoryPostProcessor，而orderedPostProcessorNames、nonOrderedPostProcessorNames集合存储的是String？
		 *
		 * 解答：无意义
		 */

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 找到所有实现了BeanFactoryPostProcessor接口的类
		// ⚠️⚠️⚠️postProcessorNames里面有一个是org.springframework.beans.factory.config.CustomEditorConfigurer#0
		/**
		 * 1、如果有<context:component-scan>标签的话，可以得到两个BeanFactoryPostProcessor
		 * >>> org.springframework.context.annotation.internalConfigurationAnnotationProcessor
		 * >>> org.springframework.context.event.internalEventListenerProcessor
		 * 其中，internalConfigurationAnnotationProcessor代表的是{@link ConfigurationClassPostProcessor}的BeanDefinition name
		 * 其中，internalEventListenerProcessor代表的是{@link EventListenerMethodProcessor}的BeanDefinition name，
		 * >>> >>>> EventListenerMethodProcessor implements BeanFactoryPostProcessor
		 *
		 * 题外：⚠️{@link ConfigurationClassPostProcessor}在上面已经执行完毕了，所以下面不会执行，
		 * >>> 只会执行{@link EventListenerMethodProcessor}，并且{@link EventListenerMethodProcessor}是作为普通的(也就是没有实现任何排序接口的)BeanFactoryPostProcessor存在
		 *
		 * 2、如果有<context:property-placeholder location=""/>标签的话，那么可以得到：org.springframework.context.support.PropertySourcesPlaceholderConfigurer#0
		 * PropertySourcesPlaceholderConfigurer：会在这里，通过PropertySourcesPlaceholderConfigurer，完成${}值替换用的
		 */
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 用于存放实现了PriorityOrdered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 用于存放实现了Ordered接口的BeanFactoryPostProcessor的beanName
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 用于存放普通的BeanFactoryPostProcessor的beanName（没有实现排序接口的）
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历postProcessorNames，将BeanFactoryPostProcessor按实现PriorityOrdered、实现Ordered接口、普通三种区分开
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
				// 跳过已经执行过的BeanFactoryPostProcessor
				// >>> 从容器中获取到的全部是内部BFPP，不是外部BPP，而且只有"内部的BDRPP对应的BFPP"，已经被执行了；
				// >>> 所以这里在执行内部BFPP时，防止的是"内部的BDRPP对应的BFPP"重复执行
			} else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)/* PriorityOrdered */) {
				// 添加实现了PriorityOrdered接口的BeanFactoryPostProcessor到priorityOrderedPostProcessors
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)/* Ordered */) {
				// 添加实现了Ordered接口的BeanFactoryPostProcessor到orderedPostProcessorNames
				orderedPostProcessorNames.add(ppName);
			} else {/* 无排序 */
				// 添加剩下的普通的BeanFactoryPostProcessor的beanName到nonOrderedPostProcessorNames
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		/* 3.1、先执行实现了PriorityOrdered接口的BeanFactoryPostProcessor */

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 对实现了PriorityOrdered接口的BeanFactoryPostProcessor进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 遍历实现了PriorityOrdered接口的BeanFactoryPostProcessors，执行postProcessBeanFactory()
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		/* 3.2、执行实现了Ordered接口的BeanFactoryPostProcessor */

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered. —— 接下来，调用实现 Ordered 的 BeanFactoryPostProcessors。
		// 创建存放实现了Ordered接口的BeanFactoryPostProcessor集合
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		// 遍历存放实现了Ordered接口的BeanFactoryPostProcessor名字的集合
		for (String postProcessorName : orderedPostProcessorNames) {
			// 将实现了Ordered接口的BeanFactoryPostProcessor bean添加到集合中
			orderedPostProcessors.add(
					/**
					 *
					 * 创建CustomEditorConfigurer Bean。在创建CustomEditorConfigurer Bean的过程中，
					 *
					 * 如果是配置文件中的方式一：那么将会创建AddressPropertyEditorRegistrar Bean，然后放入CustomEditorConfigurer.propertyEditorRegistrars属性中
					 *
					 * 如果是配置文件中的方式二：则会根据配置文件的内容把Address Class和AddressPropertyEditor Clas注入到CustomEditorConfigurer.customEditors属性中
					 * （Address Class为Key，AddressPropertyEditor Clas为Value），
					 * 这样CustomEditorConfigurer.customEditors中就有了"Address Class为Key，AddressPropertyEditor Clas为Value"的键值对
					 *
					 * 题外：配置文件：selfEditor.xml
					 *
					 */
					// ⚠️postProcessorName = org.springframework.beans.factory.config.CustomEditorConfigurer#0
					beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class)
			);
		}
		// 对实现了Ordered接口的BeanFactoryPostProcessor进行排序操作！
		sortPostProcessors(orderedPostProcessors, beanFactory); // 排序
		/**
		 * 调用 BeanFactoryPostProcessor bean
		 * 1、⚠️在这里面做了这么一件重要的事情，就是：调用了CustomEditorConfigurer#postProcessBeanFactory()，因为CustomEditorConfigurer implements BeanFactoryPostProcessor
		 * 在CustomEditorConfigurer#postProcessBeanFactory()当中的代码逻辑是，将customEditors的所有属性注入到了DefaultListableBeanFactory的customEditors当中！！！！
		 * 这样后面在创建每一个Bean的BeanWrapperImpl时，就可以从BeanFactory当中获取customEditors内容，注入到自己内部了！！！！
		 */
		// 遍历执行实现了Ordered接口的BeanFactoryPostProcessor#postProcessBeanFactory()
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		/* 3.3、执行普通的BeanFactoryPostProcessor */

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 最后，创建存放普通的BeanFactoryPostProcessor集合
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		// 遍历存放实现了普通BeanFactoryPostProcessor名字的集合
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 将实现了普通BeanFactoryPostProcessor bean添加到集合中
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 遍历执行普通的BeanFactoryPostProcessor#postProcessBeanFactory()
		/**
		 * 如果有<context:component-scan>标签的话，
		 * >>> 那么org.springframework.context.event.internalEventListenerProcessor所代表的{@link EventListenerMethodProcessor}将会在这里执行
		 * >>> EventListenerMethodProcessor implements BeanFactoryPostProcessor，并且没有实现任何排序接口
		 */
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		/* 三、清除元数据缓存（mergeBeanDefinition、allBeanNamesByType、singletonBeanNameByType）。因为后置处理器可能已经修改了原始元数据，例如：替换值中的占位符 */

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		// 上面的翻译：清除缓存的合并 bean 定义，因为后处理器可能已经修改了原始元数据，例如替换值中的占位符...
		// 清除元数据缓存（mergeBeanDefinition、allBeanNamesByType、singletonBeanNameByType）。因为后置处理器可能已经修改了原始元数据，例如：替换值中的占位符
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		/* 把当前整个应用程序里面存在的所有BeanPostProcessor的bd，创建其bean对象，注册到spring容器中 */

		// private final List<BeanPostProcessor> beanPostProcessors = new CopyOnWriteArrayList<>();

		// 找到所有实现了BeanPostProcessor接口的类，返回的是类的全限定类名
		/**
		 * 1、如果xml配置文件中加了<context:component-scan>标签，那么可以读取到这两个BeanPostProcessor：
		 * >>> org.springframework.context.annotation.internalAutowiredAnnotationProcessor
		 * >>> org.springframework.context.annotation.internalCommonAnnotationProcessor
		 * 2、如果配置了aop，那么会有：
		 * >>> org.springframework.aop.config.internalAutoProxyCreator，对应AspectJAwareAdvisorAutoProxyCreator
		 */
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// 记录下BeanPostProcessor的目标计数
		/**
		 * 此处为什么要+1呢？因为，下面一行代码会添加一个BeanPostProcessorChecker类
		 * ⚠️注意：此方法的最后会添加的一个ApplicationListenerDetector类，也是BeanPostProcessor，但是+1的目的不是为了它
		 * 因为在invokeBeanFactoryPostProcessors(beanFactory)中添加过ApplicationListenerDetector类，
		 * 所以beanFactory.getBeanPostProcessorCount()的获取到的数量，已经存在ApplicationListenerDetector类的计数；
		 * 虽然后面会重复添加ApplicationListenerDetector，但是这个类已经被计数了！
		 */
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		/**
		 * BeanPostProcessorChecker：用来完成日志的记录功能。
		 * 主要用于记录信息(以log.info()日志的形式进行记录)，没有任何的实际处理意义
		 */
		// 添加BeanPostProcessorChecker到beanFactory中
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		/* 定义不同的集合来区分不同的BPP */

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.

		// 存放实现了PriorityOrdered接口的BeanPostProcessor bean
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 存放spring内部的BeanPostProcessor(实现了PriorityOrdered和MergedBeanDefinitionPostProcessor接口的BPP)
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		// 存放实现了Ordered接口的BeanPostProcessor name
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 存放普通的BeanPostProcessor name（既没有实现PriorityOrdered，也没有实现Ordered）
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		// 遍历beanFactory中存在的BeanPostProcessor的集合postProcessorNames.
		for (String ppName : postProcessorNames) {
			// 如果ppName对应的BeanPostProcessor实例实现了PriorityOrdered接口，则获取到ppName对应的BeanPostProcessor的实例添加到priorityOrderedPostProcessor
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				// 如果ppName对应的BeanPostProcessor实例也实现了MergedBeanDefinitionPostProcessor接口，那么则将ppName对应的bean实例添加到internalPostProcessor
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			// 如果ppName对应的BeanPostProcessor实例没有实现PriorityOrdered接口，但是实现了Ordered接口，那么将ppName添加到orderedPostProcessorNames
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			// 如果ppName对应的BeanPostProcessor实例，即没有实现PriorityOrdered，也没有实现Ordered接口，那么将ppName添加到nonOrderedPostProcessorNames
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		/* 1、注册实现了PriorityOrdered接口的BeanPostProcessor实例，添加到beanFactory中 */

		// First, register the BeanPostProcessors that implement PriorityOrdered. —— 首先，注册实现 PriorityOrdered 的 BeanPostProcessor。
		// 首先，对实现了PriorityOrdered接口的BeanPostProcessor实例进行排序操作
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 注册实现了PriorityOrdered接口的BeanPostProcessor实例(添加到beanFactory中)
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		/* 2、注册实现了Ordered接口的BeanPostProcessor实例，添加到beanFactory中 */

		// Next, register the BeanPostProcessors that implement Ordered. —— 接下来，注册实现 Ordered 的 BeanPostProcessor。
		// 注册所有实现Ordered的beanPostProcessor
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			// 根据ppName找到对应的BeanPostProcessor实例对象
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			// 将实现了Ordered接口的BeanPostProcessor添加到orderedPostProcessors集合中
			orderedPostProcessors.add(pp);
			// 如果ppName对应的BeanPostProcessor实例也实现了MergedBeanDefinitionPostProcessor接口，那么则将ppName对应的bean实例添加到internalPostProcessors集合中
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 对实现了Ordered接口的BeanPostProcessor进行排序操作
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 注册实现了Ordered接口的BeanPostProcessor实例添加到beanFactory中
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		/* 3、注册没有实现PriorityOrdered和Ordered接口的BeanPostProcessor实例，添加到beanFactory中 */

		// Now, register all regular BeanPostProcessors. —— 现在，注册所有常规的 BeanPostProcessor。
		// 创建存放没有实现PriorityOrdered和Ordered接口的BeanPostProcessor的集合
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		// 遍历集合
		for (String ppName : nonOrderedPostProcessorNames) {
			// 根据ppName找到对应的BeanPostProcessor实例对象
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			// 将没有实现PriorityOrdered和Ordered接口的BeanPostProcessor添加到nonOrderedPostProcessors集合中
			nonOrderedPostProcessors.add(pp);
			// 如果ppName对应的BeanPostProcessor实例也实现了MergedBeanDefinitionPostProcessor接口，那么则将ppName对应的bean实例，添加到internalPostProcessors集合当中
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 注册没有实现PriorityOrdered和Ordered接口的BeanPostProcessor实例，添加到beanFactory中
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		/* 4、注册spring内部的BeanPostProcessor，添加到beanFactory中 */

		// Finally, re-register all internal BeanPostProcessors.
		// 将所有实现了MergeBeanDefinitionPostProcessor类型的BeanPostProcessor进行排序操作
		sortPostProcessors(internalPostProcessors, beanFactory);
		// 注册所有实现了MergeBeanDefinitionPostProcessor类型的BeanPostProcessor到beanFactory中
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 上面翻译：重新注册用于将内部 bean 检测为 ApplicationListeners 的后处理器，将其移动到处理器链的末尾（用于拾取代理等）。

		/**
		 * 在AbstractApplicationContext#refresh() ——> AbstractApplicationContext#prepareBeanFactory()中有添加ApplicationListenerDetector，
		 * 为什么这里要重复添加呢？
		 * 原因：为了把ApplicationListenerDetector放到所有BeanPostProcessor的最后面，因为它是一个监听器，方便我之前在进行处理的时候，来进行相关的检测工作
		 */

		// 注册ApplicationListenerDetector到beanFactory中
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort? —— 没有什么可排序的？
		// 如果postProcessors的个数小于等于1，那么不做任何排序
		if (postProcessors.size() <= 1) {
			return;
		}
		// 判断是否是DefaultListableBeanFactory类型
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			// 获取设置的比较器
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			// 如果没有设置比较器，则使用默认的OrderComparator
			comparatorToUse = OrderComparator.INSTANCE;
		}
		// 使用比较器对postProcessor进行排序
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans. - 调用给定的BeanDefinitionRegistryPostProcessor Bean。
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			// 目前postProcessor是ConfigurationClassPostProcessor对象
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * 调用给定的BeanFactoryPostProcessor类型的Bean对象
	 *
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			// ConfigurationClassPostProcessor
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * 当前Bean在BeanPostProcessor实例化过程中被创建时，即当前一个Bean不适合被所有BeanPostProcessor处理时，记录一个信息消息
	 *
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		// BeanPostProcessor目标数量
		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		/**
		 * bean后置处理器的before方法，什么都不做，直接返回对象
		 * @param bean the new bean instance
		 * @param beanName the name of the bean
		 * @return
		 */
		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		/**
		 * bean后置处理器的after方法，用来判断哪些是不需要检测的bean
		 *
		 * @param bean the new bean instance
		 * @param beanName the name of the bean
		 * @return
		 */
		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {

			/* 提前做了一个类型判断，判断一下，当前bean到底是什么样的规则，我给你做一个预记录的东西 */

			// 1、BeanPostProcessor类型的bean不需要检测
			// 2、ROLE_INFRASTRUCTURE这种类型的bean不检测(spring自己的bean)
			/**
			 * 要想if满足条件，则：
			 * bean instanceof BeanPostProcessor 返回false
			 * isInfrastructureBean(beanName) 返回false
			 * this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount 返回true
			 */
			// 如果bean不是BeanPostProcessor实例 && beanName不是完全内部使用 && beanFactory当前注册的BeanPostProcessor
			// 数量小于BeanPostProcessor目标数量
			if (!(bean instanceof BeanPostProcessor) &&
					// 是不是spring里面基础的bean类型
					/**
					 * 在spring里面存在几种类型的bean？
					 * 答：2种：1、程序员自己定义的bean、2、spring内部自己用的bean
					 */
					!isInfrastructureBean/* 是基础设施Bean */(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					// [bean.getClass().getName()]类型的Bean beanName不符合被所有BeanPostProcessors处理的条件（例如：不符合自动代理条件）—— 自动代理不是所有的bean都符合条件，配置了aop之后，才会用动态代理，没配置的话是不会使用的
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				// 存在这个BD，但是类型，不是spring内部使用的bean，就返回false；如果是spring内部使用的bean，则返回true
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE/* spring内部使用的bean类型 */);
			}
			// 不存在这个BD,就返回false
			return false;
		}
	}

}
