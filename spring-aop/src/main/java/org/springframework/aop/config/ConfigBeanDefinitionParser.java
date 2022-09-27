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

package org.springframework.aop.config;

import org.springframework.aop.aspectj.*;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.parsing.ParseState;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BeanDefinitionParser} for the {@code <aop:config>} tag.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @since 2.0
 */
class ConfigBeanDefinitionParser implements BeanDefinitionParser {

	private static final String ASPECT = "aspect";
	private static final String EXPRESSION = "expression";
	private static final String ID = "id";
	private static final String POINTCUT = "pointcut";
	private static final String ADVICE_BEAN_NAME = "adviceBeanName";
	private static final String ADVISOR = "advisor";
	private static final String ADVICE_REF = "advice-ref";
	private static final String POINTCUT_REF = "pointcut-ref";
	private static final String REF = "ref";
	private static final String BEFORE = "before";
	private static final String DECLARE_PARENTS = "declare-parents";
	private static final String TYPE_PATTERN = "types-matching";
	private static final String DEFAULT_IMPL = "default-impl";
	private static final String DELEGATE_REF = "delegate-ref";
	private static final String IMPLEMENT_INTERFACE = "implement-interface";
	private static final String AFTER = "after";
	private static final String AFTER_RETURNING_ELEMENT = "after-returning";
	private static final String AFTER_THROWING_ELEMENT = "after-throwing";
	private static final String AROUND = "around";
	private static final String RETURNING = "returning";
	private static final String RETURNING_PROPERTY = "returningName";
	private static final String THROWING = "throwing";
	private static final String THROWING_PROPERTY = "throwingName";
	private static final String ARG_NAMES = "arg-names";
	private static final String ARG_NAMES_PROPERTY = "argumentNames";
	private static final String ASPECT_NAME_PROPERTY = "aspectName";
	private static final String DECLARATION_ORDER_PROPERTY = "declarationOrder";
	private static final String ORDER_PROPERTY = "order";
	private static final int METHOD_INDEX = 0;
	private static final int POINTCUT_INDEX = 1;
	private static final int ASPECT_INSTANCE_FACTORY_INDEX = 2;

	private ParseState parseState = new ParseState();


	/**
	 * 解析<aop:config>
	 *
	 * 题外：<aop:config>下只有<aop:aspect>、<aop:pointcut>、<aop:advice>这3种子标签
	 *
	 * 参考：
	 * 	<aop:config proxy-target-class="true" expose-proxy="true">
	 * 		<!--	ref：引用配置好的通知类（advisor）bean的id		-->
	 * 		<!--	引入通知类，然后配置"通知类里面的通知"对哪些切入点进行切入	-->
	 * 		<aop:aspect ref="logUtil">
	 * 			<!--	切入点（pointcut）		-->
	 * 			<aop:pointcut id="myPoint" expression="execution(* com.springstudy.msb.s_21.MyCalculator.*(..))"/>
	 * 			<!--	xml是以下标作为排序值的		-->
	 * 			<!--	通知类型（advice）		-->
	 * 			<aop:around method="around" pointcut-ref="myPoint"/>
	 * 			<aop:before method="start" pointcut-ref="myPoint"/>
	 * 			<aop:after-returning method="stop" pointcut-ref="myPoint" returning="result"/>
	 * 			<aop:after-throwing method="logException" pointcut-ref="myPoint" throwing="e"/>
	 * 			<aop:after method="logFinally" pointcut-ref="myPoint"/>
	 * 		</aop:aspect>
	 * 		<!--	题外：<aop:aspect>标签可以配置多个		-->
	 * 		<!--	<aop:aspect ref="global"></aop:aspect>		-->
	 * 	</aop:config>
	 *
	 * 	参考：
	 * 	<!-- 6、配置事务切入的aop -->
	 * 	<aop:config>
	 * 		<!-- 6.1、配置切入点表达式 -->
	 * 		<aop:pointcut id="txPoint" expression="execution(* com.springstudy.msb.s_27.tx_xml.*.*(..))"/>
	 * 		<!-- 6.2、配置事务通知和切入点表达式的关系	-->
	 * 		<aop:advisor advice-ref="txAdvice" pointcut-ref="txPoint"/>
	 * 	</aop:config>
	 *
	 * @param element       <aop:config>
	 * @param parserContext 解析<aop:config>的ParserContext
	 */
	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {

		// 先创建一个对象，这个对象是归属于<aop:config>
		CompositeComponentDefinition/* 复合组件定义 */ compositeDef =
				new CompositeComponentDefinition(element.getTagName()/* 获取当前标签的名称，例如：aop:config */, parserContext.extractSource(element));

		// 添加compositeDef到parserContext.containingComponents中
		parserContext.pushContainingComponent(compositeDef);

		/*

		1、注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")，对应一个<aop:config>标签，以及配置"自动代理创建器bd"

		key = org.springframework.aop.config.internalAutoProxyCreator

		*/
		// 配置和注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")
		configureAutoProxyCreator/* 配置自动代理创建者 */(parserContext, element);

		/* 2、解析<aop:config>下的子标签：<aop:aspect>、<aop:pointcut>、<aop:advice> */

		// 获取<aop:config>下的所有子标签：<aop:aspect>、<aop:pointcut>、<aop:advice>
		List<Element> childElts = DomUtils.getChildElements(element);
		// 遍历<aop:config>下的子标签
		for (Element elt : childElts) {
			String localName = parserContext.getDelegate().getLocalName(elt);
			/* 2.1、解析切点标签<aop:pointcut>：注册AspectJExpressionPointcut bd(切入点bd)，里面保存了切入点表达式(expression) */
			// 切点 —— <aop:pointcut>
			if (POINTCUT/* pointcut */.equals(localName)) {
				parsePointcut(elt, parserContext);
			}
			/* 2.2、解析通知器标签<aop:advice>：注册DefaultBeanFactoryPointcutAdvisor bd（切入点通知bd） */
			// 通知器 —— <aop:advice>
			else if (ADVISOR/* advisor */.equals(localName)) {
				parseAdvisor(elt, parserContext);
			}
			/* 2.3、解析切面标签<aop:aspect> */
			// 切面 —— <aop:aspect>
			// 题外：<aop:aspect>所拥有的子标签：<aop:before>、<aop:after>、<aop:after-throwing>、<aop:after-returning>、<aop:around>
			else if (ASPECT/* aspect */.equals(localName)) {
				// 题外："xml是以下标作为通知方法的排序值"逻辑在里面
				parseAspect(elt, parserContext);
			}
		}

		parserContext.popAndRegisterContainingComponent/* 弹出并注册包含组件 */();
		return null;
	}

	/**
	 * 注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")，以及配置"自动代理创建器bd"
	 *
	 * 题外：AspectJAwareAdvisorAutoProxyCreator bd对应一个<aop:config>标签
	 *
	 * <p>
	 * Configures the auto proxy creator needed to support the {@link BeanDefinition BeanDefinitions}
	 * created by the '{@code <aop:config/>}' tag. Will force class proxying if the
	 * '{@code proxy-target-class}' attribute is set to '{@code true}'.
	 *
	 * @see AopNamespaceUtils
	 *
	 * @param element       <aop:config>
	 * @param parserContext 解析<aop:config>的ParserContext
	 */
	private void configureAutoProxyCreator(ParserContext parserContext, Element element) {
		// 如果工厂中不存在"自动代理创建器bd"，则注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")，以及配置"自动代理创建器bd"
		AopNamespaceUtils.registerAspectJAutoProxyCreatorIfNecessary(parserContext, element);
	}

	/**
	 * 解析通知器标签 —— <aop:advisor>
	 *
	 * 注册DefaultBeanFactoryPointcutAdvisor bd（切入点通知bd），
	 * （1）里面保存了引用的通知的beanName，
	 * 属性名是adviceBeanName，属性值是RuntimeBeanNameReference对象，里面保存了advice-ref属性值，作为引用的通知的beanName
	 * （2）还保存了切入点表达式
	 * 属性名是adviceBeanName
	 * 如果是pointcut属性指定的表达式，属性值就是一个AspectJExpressionPointcut bd(切入点bd)，里面保存了pointcut属性值对应的切入点表达式
	 * 如果pointcut-ref属性引用的表达式，属性值就是一个RuntimeBeanReference对象，里面保存了pointcut-ref属性引用的切入点表达式的id
	 *
	 * 参考：
	 * <aop:config>
	 * 		<aop:pointcut id="txPoint" expression="execution(* com.springstudy.msb.s_27.tx_xml.*.*(..))"/>
	 * 		<aop:advisor advice-ref="txAdvice" pointcut-ref="txPoint"/>
	 * </aop:config>
	 *
	 * 参考：
	 * <aop:advisor advice-ref="txAdvice" pointcut-ref="txPoint" order="" id="" pointcut=""/>
	 *
	 * Parses the supplied {@code <advisor>} element and registers the resulting
	 * {@link org.springframework.aop.Advisor} and any resulting {@link org.springframework.aop.Pointcut}
	 * with the supplied {@link BeanDefinitionRegistry}.
	 */
	private void parseAdvisor(Element advisorElement, ParserContext parserContext) {
		/*

		1、创建DefaultBeanFactoryPointcutAdvisor bd（切入点通知bd），并设置引用的通知的beanName(advice-ref属性值)，
		属性名是adviceBeanName，属性值是RuntimeBeanNameReference对象，RuntimeBeanNameReference对象中保存了advice-ref属性值，作为引用的通知的beanName

		题外：DefaultBeanFactoryPointcutAdvisor bd，对应着一个<aop:advisor>标签

		*/
		// 解析<aop:advisor>节点，最终创建的beanClass为'DefaultBeanFactoryPointcutAdvisor'
		// 另外advice-ref属性必须定义，其与内部属性adviceBeanName对应
		AbstractBeanDefinition advisorDef = createAdvisorBeanDefinition(advisorElement, parserContext);
		// id属性值
		String id = advisorElement.getAttribute(ID);

		try {
			this.parseState.push(new AdvisorEntry(id));

			/* 2、注册DefaultBeanFactoryPointcutAdvisor bd（切入点通知bd） */
			String advisorBeanName = id;
			if (StringUtils.hasText(advisorBeanName)) {
				parserContext.getRegistry().registerBeanDefinition(advisorBeanName, advisorDef);
			} else {
				advisorBeanName = parserContext.getReaderContext().registerWithGeneratedName(advisorDef);
			}

			/*

			3、获取切入点表达式，设置到DefaultBeanFactoryPointcutAdvisor bd中

			属性名是adviceBeanName
			如果是pointcut属性指定的表达式，属性值就是一个AspectJExpressionPointcut bd(切入点bd)，里面保存了pointcut属性值对应的切入点表达式
			如果pointcut-ref属性引用的表达式，属性值就是一个RuntimeBeanReference对象，里面保存了pointcut-ref属性引用的切入点表达式的id

			*/

			// 获取切入点表达式（point、point-cut属性）
			// pointcut属性：创建一个AspectJExpressionPointcut bd(切入点bd)返回，里面保存了pointcut属性值对应的切入点表达式
			// pointcut-ref属性：返回pointcut-ref属性值，代表引用的切入点表达式的id
			Object pointcut = parsePointcutProperty(advisorElement, parserContext);

			// (1)如果配置的是pointcut属性指定的表达式，那么就是BeanDefinition类型 —— 是一个AspectJExpressionPointcut bd(切入点bd)，里面保存了pointcut属性值对应的切入点表达式
			if (pointcut instanceof BeanDefinition) {
				advisorDef.getPropertyValues().add(POINTCUT/* pointcut */, pointcut);
				// 注册组件
				parserContext.registerComponent(
						new AdvisorComponentDefinition(advisorBeanName, advisorDef, (BeanDefinition) pointcut));
			}
			// (2)如果配置的是pointcut-ref属性引用的表达式，那么就是String类型 —— 代表pointcut-ref属性引用的切入点表达式的id
			else if (pointcut instanceof String) {
				// RuntimeBeanReference：提前有了一个引用，方便后续进行属性依赖注入的时候，能够把属性值注入进去
				advisorDef.getPropertyValues().add(POINTCUT/* pointcut */, new RuntimeBeanReference((String) pointcut));
				// 注册组件
				parserContext.registerComponent(
						new AdvisorComponentDefinition(advisorBeanName, advisorDef));
			}
		} finally {
			this.parseState.pop();
		}
	}

	/**
	 * 创建DefaultBeanFactoryPointcutAdvisor bd（切入点通知bd），并设置引用的通知的beanName(advice-ref属性值)，
	 * 属性名是adviceBeanName，属性值是RuntimeBeanNameReference对象，RuntimeBeanNameReference对象中保存了advice-ref属性值，作为引用的通知的beanName
	 *
	 * Create a {@link RootBeanDefinition} for the advisor described in the supplied. Does <strong>not</strong>
	 * parse any associated '{@code pointcut}' or '{@code pointcut-ref}' attributes.
	 *
	 * 为所提供的advisor(顾问)创建一个{@link RootBeanDefinition}。 <strong>not<strong> 是否解析任何关联的“{@code pointcut}”或“{@code pointcut-ref}”属性。
	 *
	 * @param advisorElement      <aop:advisor>
	 * @param parserContext       解析<aop:config>的ParserContext
	 */
	private AbstractBeanDefinition createAdvisorBeanDefinition(Element advisorElement, ParserContext parserContext) {
		/**
		 * 参考：
		 * <aop:config>
		 * 		<aop:pointcut id="txPoint" expression="execution(* com.springstudy.msb.s_27.tx_xml.*.*(..))"/>
		 * 		<aop:advisor advice-ref="txAdvice" pointcut-ref="txPoint"/>
		 * </aop:config>
		 *
		 * 参考：
		 * <aop:advisor advice-ref="txAdvice" pointcut-ref="txPoint" order="" id="" pointcut=""/>
		 */
		/* 1、创建DefaultBeanFactoryPointcutAdvisor bd(切入点通知bd) */
		RootBeanDefinition advisorDefinition = new RootBeanDefinition(DefaultBeanFactoryPointcutAdvisor.class);
		advisorDefinition.setSource(parserContext.extractSource(advisorElement));

		/*

		2、往DefaultBeanFactoryPointcutAdvisor bd中，设置引用的通知的beanName(advice-ref属性值)，
		属性名是adviceBeanName，属性值是RuntimeBeanNameReference对象，RuntimeBeanNameReference对象中保存了advice-ref属性值，作为引用的通知的beanName

		*/
		/* advice-ref属性 */
		String adviceRef = advisorElement.getAttribute(ADVICE_REF/* advice-ref */);
		// 没有配置advice-ref属性，就报错
		if (!StringUtils.hasText(adviceRef)) {
			parserContext.getReaderContext().error(
					"'advice-ref' attribute contains empty value.", advisorElement, this.parseState.snapshot());
		}
		// 配置了advice-ref属性
		else {
			advisorDefinition.getPropertyValues().add(
					ADVICE_BEAN_NAME/* adviceBeanName */, new RuntimeBeanNameReference(adviceRef));
		}

		/* 3、往DefaultBeanFactoryPointcutAdvisor bd中，设置order属性值 */
		/* order属性 */
		// 如果配置了order属性
		if (advisorElement.hasAttribute(ORDER_PROPERTY/* order */)) {
			// 往DefaultBeanFactoryPointcutAdvisor bd中，设置order属性值
			advisorDefinition.getPropertyValues().add(
					ORDER_PROPERTY, advisorElement.getAttribute(ORDER_PROPERTY));
		}

		return advisorDefinition;
	}

	/**
	 * 解析切面标签 —— <aop:aspect>
	 *
	 * 完整参考：<aop:aspect ref="logUtil" id="" order="">
	 *
	 * 参考：
	 * 	<aop:config proxy-target-class="true" expose-proxy="true">
	 * 		<!--	ref：引用配置好的通知类（advisor）bean的id		-->
	 * 		<!--	引入通知类，然后配置"通知类里面的通知"对哪些切入点进行切入	-->
	 * 		<aop:aspect ref="logUtil">
	 * 			<!--	切入点（pointcut）		-->
	 * 			<aop:pointcut id="myPoint" expression="execution(* com.springstudy.msb.s_21.MyCalculator.*(..))"/>
	 * 			<!--	xml是以下标作为排序值的		-->
	 * 			<!--	通知类型（advice）		-->
	 * 			<aop:around method="around" pointcut-ref="myPoint"/>
	 * 			<aop:before method="start" pointcut-ref="myPoint"/>
	 * 			<aop:after-returning method="stop" pointcut-ref="myPoint" returning="result"/>
	 * 			<aop:after-throwing method="logException" pointcut-ref="myPoint" throwing="e"/>
	 * 			<aop:after method="logFinally" pointcut-ref="myPoint"/>
	 * 		</aop:aspect>
	 * 		<!--	题外：<aop:aspect>标签可以配置多个		-->
	 * 		<!--	<aop:aspect ref="global"></aop:aspect>		-->
	 * 	</aop:config>
	 *
	 * @param aspectElement
	 * @param parserContext
	 */
	private void parseAspect(Element aspectElement, ParserContext parserContext) {
		/*

		1、获取<aop:aspect>标签中的属性

		 */
		// 获取切面id
		// <aop:aspect id="logger" ref="logger">中的id属性
		String aspectId = aspectElement.getAttribute(ID/* id */);
		// 获取切面名称（引用的切面类的名称）
		// <aop:aspect id="logger" ref="logger">中的ref属性（必须配置，代表切面🤔️）
		String aspectName = aspectElement.getAttribute(REF/* ref */);

		try {
			this.parseState.push(new AspectEntry(aspectId, aspectName));
			List<BeanDefinition> beanDefinitions = new ArrayList<>();
			List<BeanReference> beanReferences = new ArrayList<>();

			/*

			2、解析<aop:aspect>下的<aop:declare-parents>子标签，创建其对应对象的bd，放入bean工厂中

			 */
			// 解析<aop:aspect>下的<aop:declare-parents>子标签
			// 采用的是DeclareParentsAdvisor作为beanClass加载
			List<Element> declareParents = DomUtils.getChildElementsByTagName/* 按标签名称获取子元素 */(aspectElement, DECLARE_PARENTS/* declare-parents */);
			for (int i = METHOD_INDEX; i < declareParents.size(); i++) {
				Element declareParentsElement = declareParents.get(i);
				beanDefinitions.add(parseDeclareParents(declareParentsElement, parserContext));
			}

			/*

			3、解析<aop:aspect>下的advice(通知)类型子标签，一共有<aop:around>、<aop:before>、<aop:after-returning>、<aop:after-throwing>、<aop:after>标签。
			根据通知类型，生成对应的通知类的bd（advice bd）；并用一个AspectJPointcutAdvisor bd(advisor bd)包裹起来，
			注册到bean工厂的bd容器中(beanDefinitionMap)

			 */

			// We have to parse "advice" and all the advice kinds in one loop, to get the
			// ordering semantics right.
			// 上面的翻译：我们必须在一个循环中解析“advice”和所有的advice种类，以得到正确的排序语义。

			// 解析<aop:aspect>剩下的子标签
			// 解析其下的advice节点
			NodeList nodeList = aspectElement.getChildNodes();
			boolean adviceFoundAlready = false;
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				/**
				 * 判断是不是advice节点(通知节点)，是的话就进行解析 —— 其实也就是判断，当前的标签是不是这五个标签
				 * <aop:before>、<aop:after>、<aop:after-throwing>、<aop:after-returning>、<aop:around>
				 *
				 *
				 */
				// 是否为advice:before/advice:after/advice:after-returning/advice:after-throwing/advice:around节点
				if (isAdviceNode(node, parserContext)) {
					// 检验aop:aspect必须有ref属性，否则无法对切入点进行观察操作
					if (!adviceFoundAlready) {
						adviceFoundAlready = true;
						if (!StringUtils.hasText(aspectName)) {
							parserContext.getReaderContext().error(
									"<aspect> tag needs aspect bean reference via 'ref' attribute when declaring advices.",
									aspectElement, this.parseState.snapshot());
							return;
						}
						beanReferences.add(new RuntimeBeanReference(aspectName));
					}
					// ⚠️解析通知节点。
					// 根据通知类型，生成对应的通知类的bd（advice bd）；并用一个AspectJPointcutAdvisor bd(advisor bd)包裹起来，注册到bean工厂的bd容器中(beanDefinitionMap)
					AbstractBeanDefinition advisorDefinition = parseAdvice(
							aspectName,
							i/* ⚠️ */,
							aspectElement, (Element) node, parserContext, beanDefinitions, beanReferences);

					beanDefinitions.add(advisorDefinition);
				}
			}

			/*

			4、注册一个组件，AspectComponentDefinition

			 */

			AspectComponentDefinition/* 切面组件定义 */ aspectComponentDefinition = createAspectComponentDefinition(
					aspectElement, aspectId, beanDefinitions, beanReferences, parserContext);
			parserContext.pushContainingComponent(aspectComponentDefinition);

			/*

			5、解析<aop:aspect>下的<aop:pointcut>子标签，创建一个包裹"切入点表达式"的bd（AspectJExpressionPointcut bd），
			简称创建切入点对象的bd，注册到bean工厂中， key=id属性，value=AspectJExpressionPointcut bd，包含了expression属性

			例如：<aop:pointcut id="myPoint" expression="execution(* com.springstudy.mashibing.s_21.MyCalculator.*(..))" />

			 */

			// 解析<aop:aspect>下的<aop:pointcut>子标签，并注册到bean工厂
			List<Element> pointcuts = DomUtils.getChildElementsByTagName(aspectElement, POINTCUT);
			for (Element pointcutElement : pointcuts) {
				parsePointcut(pointcutElement, parserContext);
			}

			parserContext.popAndRegisterContainingComponent/* 弹出并注册包含组件 */();
		} finally {
			this.parseState.pop();
		}
	}

	private AspectComponentDefinition createAspectComponentDefinition(
			Element aspectElement, String aspectId, List<BeanDefinition> beanDefs,
			List<BeanReference> beanRefs, ParserContext parserContext) {

		BeanDefinition[] beanDefArray = beanDefs.toArray(new BeanDefinition[0]);
		BeanReference[] beanRefArray = beanRefs.toArray(new BeanReference[0]);
		Object source = parserContext.extractSource(aspectElement);
		return new AspectComponentDefinition(aspectId, beanDefArray, beanRefArray, source);
	}

	/**
	 * Return {@code true} if the supplied node describes an advice type. May be one of:
	 * '{@code before}', '{@code after}', '{@code after-returning}',
	 * '{@code after-throwing}' or '{@code around}'.
	 */
	private boolean isAdviceNode(Node aNode, ParserContext parserContext) {
		if (!(aNode instanceof Element)) {
			return false;
		} else {
			// 获取标签名称
			String name = parserContext.getDelegate().getLocalName(aNode);
			return (BEFORE/* before */.equals(name)
					|| AFTER/* after */.equals(name)
					|| AFTER_RETURNING_ELEMENT/* after-returning */.equals(name)
					|| AFTER_THROWING_ELEMENT/* after-throwing */.equals(name)
					|| AROUND/* around */.equals(name));
		}
	}

	/**
	 * Parse a '{@code declare-parents}' element and register the appropriate
	 * DeclareParentsAdvisor with the BeanDefinitionRegistry encapsulated in the
	 * supplied ParserContext.
	 */
	private AbstractBeanDefinition parseDeclareParents(Element declareParentsElement, ParserContext parserContext) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(DeclareParentsAdvisor.class);
		builder.addConstructorArgValue(declareParentsElement.getAttribute(IMPLEMENT_INTERFACE));
		builder.addConstructorArgValue(declareParentsElement.getAttribute(TYPE_PATTERN));

		String defaultImpl = declareParentsElement.getAttribute(DEFAULT_IMPL);
		String delegateRef = declareParentsElement.getAttribute(DELEGATE_REF);

		if (StringUtils.hasText(defaultImpl) && !StringUtils.hasText(delegateRef)) {
			builder.addConstructorArgValue(defaultImpl);
		} else if (StringUtils.hasText(delegateRef) && !StringUtils.hasText(defaultImpl)) {
			builder.addConstructorArgReference(delegateRef);
		} else {
			parserContext.getReaderContext().error(
					"Exactly one of the " + DEFAULT_IMPL + " or " + DELEGATE_REF + " attributes must be specified",
					declareParentsElement, this.parseState.snapshot());
		}

		AbstractBeanDefinition definition = builder.getBeanDefinition();
		definition.setSource(parserContext.extractSource(declareParentsElement));
		parserContext.getReaderContext().registerWithGeneratedName(definition);
		return definition;
	}

	/**
	 * 解析<aop:before>、<aop:after>、<aop:after-throwing>、<aop:after-returning>、<aop:around>标签，注册bd
	 *
	 * Parses one of '{@code before}', '{@code after}', '{@code after-returning}',
	 * '{@code after-throwing}' or '{@code around}' and registers the resulting
	 * BeanDefinition with the supplied BeanDefinitionRegistry.
	 *
	 * @return the generated advice RootBeanDefinition
	 */
	private AbstractBeanDefinition parseAdvice(
			String aspectName/* <aop:aspect id="logger" ref="logger">中的ref属性 */,
			int order/* 这个值是xml中的标签下标，所以xml是以下标作为通知方法的排序值 */,
			Element aspectElement, Element adviceElement, ParserContext parserContext,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {

		try {
			this.parseState.push(new AdviceEntry(parserContext.getDelegate().getLocalName(adviceElement)));

			/*

			 1、解析通知节点中的"method"属性，并包装为MethodLocatingFactoryBean bd

			 例如：<aop:before method=""/>中的method属性

			 ⚠️Advice类的构造器需要三个参数，第一个参数是Method对象

			 */

			// create the method factory bean —— 创建方法工厂bean
			RootBeanDefinition methodDefinition = new RootBeanDefinition(MethodLocatingFactoryBean.class);
			methodDefinition.getPropertyValues().add("targetBeanName", aspectName/* 切面 */);
			methodDefinition.getPropertyValues().add("methodName", adviceElement.getAttribute("method")/* ⚠️增强器 */);
			methodDefinition.setSynthetic/* 合成的 */(true);

			/*

			2、根据<aop:aspect id="logger" ref="logger">中的ref属性，构建一个SimpleBeanFactoryAwareAspectInstanceFactory bd

			⚠️Advice类的构造器需要三个参数，第二个参数是AspectInstanceFactory对象

			 */

			// create instance factory definition —— 创建实例工厂定义
			// 关联aspectName，包装为SimpleBeanFactoryAwareAspectInstanceFactory对象
			RootBeanDefinition aspectFactoryDef =
					new RootBeanDefinition(SimpleBeanFactoryAwareAspectInstanceFactory.class);
			// aspectName：<aop:aspect id="logger" ref="logger">中的ref属性
			aspectFactoryDef.getPropertyValues().add("aspectBeanName", aspectName);
			aspectFactoryDef.setSynthetic(true);

			/*

			3、根据当前的通知类型(advice)，创建出对应的通知类的bd。

			例如我们当前的通知类型是<aop:before>

			题外：⚠️里面包含了具体的三个对象属性值：MethodLocatingFactoryBean、表达式、SimpleBeanFactoryAwareAspectInstanceFactory

			题外：表达式的获取(还未解析)在这里面，这里面只是获取了表达式，然后用一个对象包裹而已，
			>>> 例如：<aop:before method="recordBefore" pointcut-ref="method"/>，那么pointcut=method，表达式就是一个"method"字符串，
			>>> 然后创建RuntimeBeanReference对象，把"method"字符串存储起来，表达式还没有

			题外：⚠️Advice类的构造器需要三个参数，第三个参数是AspectJExpressionPointcut对象

			 */
			/**
			 * 通知类型，所对应的Advice类型，查看：{@link ConfigBeanDefinitionParser#getAdviceClass(Element, ParserContext)}
			 */
			// register the pointcut —— 注册切入点
			// 涉及pointcut属性的解析，并结合上述的两个bean最终包装为AbstractAspectJAdvice通知对象
			// ⚠️创建出我们当前advice的定义信息
			AbstractBeanDefinition adviceDef = createAdviceDefinition(
					adviceElement, parserContext, aspectName, order, methodDefinition/* ⚠️ */, aspectFactoryDef/* ⚠️ */,
					beanDefinitions, beanReferences);

			/*

			4、生成AspectJPointcutAdvisor bd，包裹了当前的advice bd

			题外：上面只是获取到了一个advice bd，advice对象外面需要包一个advisor对象，所以用AspectJPointcutAdvisor bd包裹当前的advice bd

			 */

			// configure the advisor —— 配置通知
			// 最终包装为AspectJPointcutAdvisor对象
			RootBeanDefinition advisorDefinition = new RootBeanDefinition(AspectJPointcutAdvisor.class);
			// 设置属性
			advisorDefinition.setSource(parserContext.extractSource(adviceElement));
			// 设置构造参数
			advisorDefinition.getConstructorArgumentValues().addGenericArgumentValue(adviceDef/* ⚠️ */);
			if (aspectElement.hasAttribute(ORDER_PROPERTY/* order */)) {
				advisorDefinition.getPropertyValues().add(
						ORDER_PROPERTY, aspectElement.getAttribute(ORDER_PROPERTY));
			}

			/*

			5、注册AspectJPointcutAdvisor bd到bean工厂中(beanDefinitionMap)

			 */

			// register the final advisor —— 注册最终顾问
			parserContext.getReaderContext().registerWithGeneratedName/* 使用生成的名称注册 */(advisorDefinition);

			return advisorDefinition;
		} finally {
			this.parseState.pop();
		}
	}

	/**
	 * 创建Advice bd
	 *
	 * Creates the RootBeanDefinition for a POJO advice bean. Also causes pointcut
	 * parsing to occur so that the pointcut may be associate with the advice bean.
	 * This same pointcut is also configured as the pointcut for the enclosing
	 * Advisor definition using the supplied MutablePropertyValues.
	 */
	private AbstractBeanDefinition createAdviceDefinition(
			Element adviceElement, ParserContext parserContext, String aspectName/* 切面 */, int order,
			RootBeanDefinition methodDef, RootBeanDefinition aspectFactoryDef,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {

		/* 1、根据当前通知类型，创建对应的Advice bd */

		// 首先根据adviceElement节点分析出是什么类型的Advice
		RootBeanDefinition adviceDefinition =
				new RootBeanDefinition(getAdviceClass(adviceElement, parserContext)/* ⚠️根据当前advice标签类型，获取具体的advice类 */);

		/* 1、往当前通知类的bd中，设置属性值 */

		adviceDefinition.setSource(parserContext.extractSource(adviceElement));

		// 设置aspectName属性和declarationOrder属性
		adviceDefinition.getPropertyValues().add(ASPECT_NAME_PROPERTY/* aspectName */, aspectName);
		adviceDefinition.getPropertyValues().add(DECLARATION_ORDER_PROPERTY/* declarationOrder */, order);

		// 解析节点是否含有'returning'/'throwing'/'arg-names'，有则设置
		if (adviceElement.hasAttribute(RETURNING/* returning */)) {
			adviceDefinition.getPropertyValues().add(
					RETURNING_PROPERTY/* returningName */, adviceElement.getAttribute(RETURNING));
		}
		if (adviceElement.hasAttribute(THROWING/* throwing */)) {
			adviceDefinition.getPropertyValues().add(
					THROWING_PROPERTY/* throwingName */, adviceElement.getAttribute(THROWING));
		}
		if (adviceElement.hasAttribute(ARG_NAMES/* arg-names */)) {
			adviceDefinition.getPropertyValues().add(
					ARG_NAMES_PROPERTY/* argumentNames */, adviceElement.getAttribute(ARG_NAMES));
		}

		/* 2、往当前通知类的bd中，设置构造函数的入参变量 */

		// Method/AspectJExpressionPointcut/AspectInstanceFactory三个入参
		ConstructorArgumentValues cav = adviceDefinition.getConstructorArgumentValues();
		cav.addIndexedArgumentValue/* 为构造器的指定索引设置属性值 */(METHOD_INDEX/* 0 */, methodDef);

		// ⚠️解析pointcut-ref和pointcut属性，准备对应的表达式对象的bd
		// 例如：<aop:before method="recordBefore" pointcut-ref="method"/>，那么pointcut=method
		Object pointcut/* ⚠️切入点表达式 */ = parsePointcutProperty(adviceElement, parserContext);

		if (pointcut instanceof BeanDefinition) {
			cav.addIndexedArgumentValue(POINTCUT_INDEX/* 1 */, pointcut);
			beanDefinitions.add((BeanDefinition) pointcut);
		} else if (pointcut instanceof String) {
			// 如果是字符串，就创建一个RuntimeBeanReference
			RuntimeBeanReference pointcutRef = new RuntimeBeanReference((String) pointcut);
			cav.addIndexedArgumentValue(POINTCUT_INDEX/* 1 */, pointcutRef);
			beanReferences.add(pointcutRef);
		}

		cav.addIndexedArgumentValue(ASPECT_INSTANCE_FACTORY_INDEX/* 2 */, aspectFactoryDef);

		return adviceDefinition;
	}

	/**
	 * 根据通知类型，获取对应的Advice类
	 *
	 * Gets the advice implementation class corresponding to the supplied {@link Element}.
	 * 获取与提供的 {@link Element} 对应的建议实现类。
	 */
	private Class<?> getAdviceClass(Element adviceElement, ParserContext parserContext) {
		// 标签名称
		String elementName = parserContext.getDelegate().getLocalName(adviceElement);

		// 根据通知类型(标签名称决定了通知类型)，获取对应的Advice类
		if (BEFORE/* before */.equals(elementName)) {
			return AspectJMethodBeforeAdvice.class;
		} else if (AFTER/* after */.equals(elementName)) {
			return AspectJAfterAdvice.class;
		} else if (AFTER_RETURNING_ELEMENT/* after-returning */.equals(elementName)) {
			return AspectJAfterReturningAdvice.class;
		} else if (AFTER_THROWING_ELEMENT/* after-throwing */.equals(elementName)) {
			return AspectJAfterThrowingAdvice.class;
		} else if (AROUND/* around */.equals(elementName)) {
			return AspectJAroundAdvice.class;
		} else {
			throw new IllegalArgumentException("Unknown advice kind [" + elementName + "].");
		}
	}

	/**
	 * 解析切入点标签 —— <aop:pointcut>
	 *
	 * 注册AspectJExpressionPointcut bd(切入点bd)，里面保存了切入点表达式(expression)
	 *
	 * 题外：AspectJExpressionPointcut bd(切入点bd)，对应着一个<aop:pointcut>标签
	 *
	 * <p>
	 * Parses the supplied {@code <pointcut>} and registers the resulting
	 * Pointcut with the BeanDefinitionRegistry.
	 */
	private AbstractBeanDefinition parsePointcut(Element pointcutElement, ParserContext parserContext) {
		/* 1、获取<aop:pointcut>标签中的id、expression属性值 */
		/**
		 * 参考：<aop:pointcut id="myPoint" expression="execution(* com.springstudy.mashibing.s_21.MyCalculator.*(..))" />
		 */
		// 切入点表达式id
		String id = pointcutElement.getAttribute(ID/* id */);
		// 获取切入点表达式
		String expression = pointcutElement.getAttribute(EXPRESSION/* expression */);

		AbstractBeanDefinition pointcutDefinition = null;

		try {
			// 采用栈保存切入点
			this.parseState.push(new PointcutEntry(id));

			/* 2、️创建AspectJExpressionPointcut bd(切入点bd)，里面保存了切入点表达式 */
			// 创建切入点对象的bd
			// ⚠️创建一个包裹"切入点表达式"的bd —— AspectJExpressionPointcut bd
			pointcutDefinition = createPointcutDefinition(expression);
			// beanClass为AspectJExpressionPointcut.class，并且设置属性expression到该beanClass中
			pointcutDefinition.setSource(parserContext.extractSource(pointcutElement));

			/* 3、注册AspectJExpressionPointcut bd(切入点bd) */
			// id作为AspectJExpressionPointcut bd(切入点bd)的beanName
			String pointcutBeanName = id;
			if (StringUtils.hasText(pointcutBeanName)) {
				// 注册AspectJExpressionPointcut bd(切入点bd)到bean工厂中
				parserContext.getRegistry().registerBeanDefinition(pointcutBeanName, pointcutDefinition);
			} else {
				// 注册AspectJExpressionPointcut bd(切入点bd)到bean工厂中
				pointcutBeanName = parserContext.getReaderContext().registerWithGeneratedName/* 使用生成的名称注册 */(pointcutDefinition);
			}

			/* 4、注册一个组件，PointcutComponentDefinition */
			// 注册一个组件，PointcutComponentDefinition
			parserContext.registerComponent(
					new PointcutComponentDefinition(pointcutBeanName, pointcutDefinition, expression));
		} finally {
			// 创建后移除
			this.parseState.pop();
		}

		return pointcutDefinition;
	}

	/**
	 * 获取切入点表达式（point、point-cut属性）
	 *
	 * 参考：<aop:advisor advice-ref="txAdvice" pointcut-ref="txPoint" order="" id="" pointcut=""/>
	 *
	 * Parses the {@code pointcut} or {@code pointcut-ref} attributes of the supplied
	 * {@link Element} and add a {@code pointcut} property as appropriate. Generates a
	 * {@link org.springframework.beans.factory.config.BeanDefinition} for the pointcut if  necessary
	 * and returns its bean name, otherwise returns the bean name of the referred pointcut.
	 */
	@Nullable
	private Object parsePointcutProperty(Element element, ParserContext parserContext) {
		/* 1、如果同时配置了pointcut、pointcut-ref属性，就报错 */
		if (element.hasAttribute(POINTCUT/* pointcut */) && element.hasAttribute(POINTCUT_REF/* pointcut-ref */)) {
			parserContext.getReaderContext().error(
					"Cannot define both 'pointcut' and 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());
			return null;
		}
		/* 2、处理pointcut属性：创建一个AspectJExpressionPointcut bd(切入点bd)返回，里面保存了pointcut属性值对应的切入点表达式 */
		else if (element.hasAttribute(POINTCUT)) {
			// Create a pointcut for the anonymous pc and register it.
			// 获取pointcut属性值
			String expression = element.getAttribute(POINTCUT);
			// ⚠️
			AbstractBeanDefinition pointcutDefinition = createPointcutDefinition(expression);
			pointcutDefinition.setSource(parserContext.extractSource(element));
			return pointcutDefinition;
		}
		/* 3、处理pointcut-ref属性：返回pointcut-ref属性值，代表引用的切入点表达式的id */
		else if (element.hasAttribute(POINTCUT_REF)) {
			// 获取pointcut-ref属性值
			String pointcutRef = element.getAttribute(POINTCUT_REF);
			if (!StringUtils.hasText(pointcutRef)) {
				parserContext.getReaderContext().error(
						"'pointcut-ref' attribute contains empty value.", element, this.parseState.snapshot());
				return null;
			}
			return pointcutRef;
		}
		/* 4、如果没有配置pointcut、pointcut-ref属性中的任何一个属性，也报错 */
		else {
			parserContext.getReaderContext().error(
					"Must define one of 'pointcut' or 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());
			return null;
		}
	}

	/**
	 * 创建AspectJExpressionPointcut bd(切入点bd)，里面保存了切入点表达式
	 *
	 * Creates a {@link BeanDefinition} for the {@link AspectJExpressionPointcut} class using
	 * the supplied pointcut expression.
	 */
	protected AbstractBeanDefinition createPointcutDefinition(String expression/* 切入点表达式 */) {
		// 创建AspectJExpressionPointcut bd(切入点bd)
		RootBeanDefinition beanDefinition = new RootBeanDefinition(AspectJExpressionPointcut.class);

		// 设置作用域为原型
		beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE/* prototype */);
		// 设置bd是合成的
		beanDefinition.setSynthetic(true);
		// ⚠️设置切入点表达式
		beanDefinition.getPropertyValues().add(EXPRESSION/* expression */, expression);

		return beanDefinition;
	}

}
