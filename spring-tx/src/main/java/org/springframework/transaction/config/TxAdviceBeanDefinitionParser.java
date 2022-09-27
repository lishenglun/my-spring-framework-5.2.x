/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.transaction.config;

import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.transaction.interceptor.*;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.LinkedList;
import java.util.List;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser
 * BeanDefinitionParser} for the {@code <tx:advice/>} tag.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @author Chris Beams
 * @since 2.0
 */
class TxAdviceBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	private static final String METHOD_ELEMENT = "method";

	private static final String METHOD_NAME_ATTRIBUTE = "name";

	private static final String ATTRIBUTES_ELEMENT = "attributes";

	private static final String TIMEOUT_ATTRIBUTE = "timeout";

	private static final String READ_ONLY_ATTRIBUTE = "read-only";

	private static final String PROPAGATION_ATTRIBUTE = "propagation";

	private static final String ISOLATION_ATTRIBUTE = "isolation";

	private static final String ROLLBACK_FOR_ATTRIBUTE = "rollback-for";

	private static final String NO_ROLLBACK_FOR_ATTRIBUTE = "no-rollback-for";


	@Override
	protected Class<?> getBeanClass(Element element) {
		return TransactionInterceptor.class;
	}

	/**
	 * 参考：
	 * 	<!--	5、配置事务独特的advice	-->
	 * 	<tx:advice id="txAdvice" transaction-manager="transactionManager">
	 * 		<!--	5.1、配置事务的属性	-->
	 * 		<tx:attributes>
	 * 			<tx:method name="updateBalanceInService" propagation="REQUIRED"/>
	 * 			<tx:method name="updateBalanceInDao" propagation="REQUIRED"/>
	 * 		</tx:attributes>
	 * 	</tx:advice>
	 *
	 * @param element       the XML element being parsed
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @param builder       used to define the {@code BeanDefinition}
	 */
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		/*

		1、获取使用的事务管理器名称，然后往builder中添加transactionManager属性值，属性值是RuntimeBeanReference对象。RuntimeBeanReference对象里面保存了beanName=事务管理器名称。

		*/
		// TxNamespaceHandler.getTransactionManagerName(element)：获取<tx:advice transaction-manager="transactionManager">中的transaction-manager属性
		builder.addPropertyReference("transactionManager", TxNamespaceHandler.getTransactionManagerName(element));

		/* 2、解析<tx:attributes>标签 */
		// 获取所有的<tx:attributes>标签
		List<Element> txAttributes = DomUtils.getChildElementsByTagName(element, ATTRIBUTES_ELEMENT/* attributes */);

		/*（1）如果<tx:attributes>标签大于1个，则报错 */
		if (txAttributes.size() > 1) {
			parserContext.getReaderContext().error(
					"Element <attributes> is allowed at most once inside element <advice>", element);
		}
		/*（2）如果<tx:attributes>标签等于1个，则获取这一个<tx:attributes>标签开始解析 */
		else if (txAttributes.size() == 1) {
			// Using attributes source. —— 使用属性源。
			// 获取<tx:attributes>标签对应的Element对象
			Element attributeSourceElement = txAttributes.get(0);

			/*

			（2.1）解析<tx:attributes>下的所有<tx:method>标签
			>>>（1）解析<tx:attributes>下的所有<tx:method>标签，为每一个<tx:method>标签创建一个RuleBasedTransactionAttribute对象(事务属性规则对象)
			>>> RuleBasedTransactionAttribute里面保存了<tx:method>标签当中配置的方法对应的事务特性，例如：传播特性、隔离级别、超时时间、是否只读、回滚及不回滚的规则，
			>>>（2）所有的RuleBasedTransactionAttribute收录在transactionAttributeMap(事务属性集合)中
			>>>（3）然后会创建NameMatchTransactionAttributeSource bd，里面保存了一个nameMap属性，值是transactionAttributeMap
			>>>（4）最终返回NameMatchTransactionAttributeSource bd

			*/
			RootBeanDefinition attributeSourceDefinition = parseAttributeSource(attributeSourceElement, parserContext);

			/* (2.2)往builder中添加transactionAttributeSource属性，属性值是NameMatchTransactionAttributeSource bd */
			builder.addPropertyValue("transactionAttributeSource", attributeSourceDefinition);
		}
		/* (3)不存在一个<tx:attributes>标签，则假设采用的是注解的方式配置的事务，往builder中添加transactionAttributeSource属性，属性值是AnnotationTransactionAttributeSource bd，用于解析事务注解 */
		else {
			// Assume annotations source. —— 假设注解源
			builder.addPropertyValue("transactionAttributeSource",
					new RootBeanDefinition("org.springframework.transaction.annotation.AnnotationTransactionAttributeSource"));
		}
	}

	/**
	 * 1、解析<tx:attributes>下的所有<tx:method>标签，为每一个<tx:method>标签创建一个RuleBasedTransactionAttribute对象(事务属性规则对象)
	 *
	 * RuleBasedTransactionAttribute里面保存了<tx:method>标签当中配置的方法对应的事务特性，例如：传播特性、隔离级别、超时时间、是否只读、回滚及不回滚的规则，
	 *
	 * 2、所有的RuleBasedTransactionAttribute收录在transactionAttributeMap(事务属性集合)中
	 *
	 * 3、然后会创建NameMatchTransactionAttributeSource bd，里面保存了一个nameMap属性，值是transactionAttributeMap
	 *
	 * 4、最终返回NameMatchTransactionAttributeSource bd
	 *
	 * 参考：
	 * 	<tx:advice id="txAdvice" transaction-manager="transactionManager">
	 * 		<tx:attributes>
	 * 			<tx:method name="updateBalanceInService" propagation="REQUIRED"/>
	 * 			<tx:method name="updateBalanceInDao" propagation="REQUIRED"/>
	 * 		</tx:attributes>
	 * 	</tx:advice>
	 *
	 * @param attrEle									<tx:attributes>
	 * @param parserContext								ParserContext
	 * @return
	 */
	private RootBeanDefinition parseAttributeSource(Element attrEle, ParserContext parserContext) {
		// 获取<tx:attributes>标签下的所有的<tx:method>标签
		List<Element> methods = DomUtils.getChildElementsByTagName(attrEle, METHOD_ELEMENT/* method */);

		/**
		 * transactionAttributeMap：事务属性集合，对应所有的<tx:method>标签
		 */
		// 事务属性集合，对应所有的<tx:method>标签
		// key：方法名称（<tx:method>标签当中配置的方法名称，可以是表达式，例如：find*）
		// value：RuleBasedTransactionAttribute，里面保存了<tx:method>标签当中配置的方法对应的事务特性，例如：传播特性、隔离级别、超时时间、是否只读、回滚及不回滚的规则、等一些属性，都会保存到RuleBasedTransactionAttribute
		ManagedMap<TypedStringValue, RuleBasedTransactionAttribute> transactionAttributeMap =
				new ManagedMap<>(methods.size());

		transactionAttributeMap.setSource(parserContext.extractSource(attrEle));
		/*


		1、会解析<tx:attributes>标签下的所有<tx:method>标签，为每一个<tx:method>标签创建一个RuleBasedTransactionAttribute对象(事务属性规则对象)。

		RuleBasedTransactionAttribute里面保存了<tx:method>标签当中配置的方法对应的事务特性，例如：传播特性、隔离级别、超时时间、是否只读、回滚及不回滚的规则

		 */
		// 遍历<tx:method>标签
		for (Element methodEle : methods) {
			// 获取方法名称（<tx:method name="">中的name属性）
			//
			String name = methodEle.getAttribute(METHOD_NAME_ATTRIBUTE/* name */);
			// 包装存放一下
			TypedStringValue nameHolder = new TypedStringValue(name);
			nameHolder.setSource(parserContext.extractSource(methodEle));

			/**
			 * RuleBasedTransactionAttribute：事务属性规则对象，对应一个<tx:method>标签
			 */
			// 为每一个<tx:method>标签创建一个RuleBasedTransactionAttribute（事务属性规则对象），
			// 里面保存了<tx:method>标签当中配置的方法对应的事务特性，例如：传播特性、隔离级别、超时时间、是否只读、回滚及不回滚的规则、等一些属性
			RuleBasedTransactionAttribute/* 基于规则的事务属性 */ attribute = new RuleBasedTransactionAttribute();

			// 传播特性
			String propagation = methodEle.getAttribute(PROPAGATION_ATTRIBUTE/* propagation */);
			// 隔离级别
			String isolation = methodEle.getAttribute(ISOLATION_ATTRIBUTE/* isolation */);
			// 超时时间
			String timeout = methodEle.getAttribute(TIMEOUT_ATTRIBUTE/* timeout */);
			// 是否只读
			String readOnly = methodEle.getAttribute(READ_ONLY_ATTRIBUTE/* read-only */);

			// 设置传播特性（判断有没有值，有的话就进行设置）
			if (StringUtils.hasText(propagation)) {
				attribute.setPropagationBehaviorName(RuleBasedTransactionAttribute.PREFIX_PROPAGATION/* PROPAGATION_ */ + propagation);
			}
			// 设置隔离级别
			if (StringUtils.hasText(isolation)) {
				attribute.setIsolationLevelName(RuleBasedTransactionAttribute.PREFIX_ISOLATION/* ISOLATION_ */ + isolation);
			}
			// 设置超时时间
			if (StringUtils.hasText(timeout)) {
				try {
					attribute.setTimeout(Integer.parseInt(timeout));
				}
				catch (NumberFormatException ex) {
					parserContext.getReaderContext().error("Timeout must be an integer value: [" + timeout + "]", methodEle);
				}
			}
			// 设置是否只读
			if (StringUtils.hasText(readOnly)) {
				attribute.setReadOnly(Boolean.parseBoolean(methodEle.getAttribute(READ_ONLY_ATTRIBUTE/* read-only */)));
			}

			// RollbackRuleAttribute集合（回滚规则属性集合）
			// RollbackRuleAttribute：代表回滚的异常；有个子类NoRollbackRuleAttribute，代表不回滚的异常
			List<RollbackRuleAttribute>/* 回滚规则属性 */ rollbackRules = new LinkedList<>();

			/* 回滚异常 */
			if (methodEle.hasAttribute(ROLLBACK_FOR_ATTRIBUTE/* rollback-for */)) {
				String rollbackForValue = methodEle.getAttribute(ROLLBACK_FOR_ATTRIBUTE);
				addRollbackRuleAttributesTo/* 将回滚规则属性添加到 */(rollbackRules,rollbackForValue);
			}

			/* 不回滚的异常 */
			if (methodEle.hasAttribute(NO_ROLLBACK_FOR_ATTRIBUTE/* no-rollback-for */)) {
				String noRollbackForValue = methodEle.getAttribute(NO_ROLLBACK_FOR_ATTRIBUTE);
				addNoRollbackRuleAttributesTo/* 添加无回滚规则属性到 */(rollbackRules,noRollbackForValue);
			}

			// 添加回滚规则
			attribute.setRollbackRules(rollbackRules);

			/* 2、然后会将所有的RuleBasedTransactionAttribute收录在transactionAttributeMap(事务属性集合)中 */

			transactionAttributeMap.put(nameHolder, attribute);
		}

		/* 3、然后会创建NameMatchTransactionAttributeSource bd，里面保存了一个nameMap属性，值是transactionAttributeMap */
		/**
		 * NameMatchTransactionAttributeSource：名称匹配事务属性源对象，对应一个<tx:method>标签
		 */
		// NameMatchTransactionAttributeSource bd：保存了事务相关的一些属性值
		RootBeanDefinition attributeSourceDefinition = new RootBeanDefinition(NameMatchTransactionAttributeSource/* 名称匹配事务属性源 */.class);
		attributeSourceDefinition.setSource(parserContext.extractSource(attrEle));
		attributeSourceDefinition.getPropertyValues().add("nameMap", transactionAttributeMap);

		/* 4、返回NameMatchTransactionAttributeSource bd */
		return attributeSourceDefinition;
	}

	/**
	 * 添加回滚异常
	 *
	 * @param rollbackRules					RollbackRuleAttribute集合
	 * @param rollbackForValue				回滚的异常类
	 */
	private void addRollbackRuleAttributesTo(List<RollbackRuleAttribute> rollbackRules, String rollbackForValue) {
		// 逗号分割成数组，回滚异常数组
		String[] exceptionTypeNames = StringUtils.commaDelimitedListToStringArray/* 逗号分隔列表到字符串数组 */(rollbackForValue);
		for (String typeName/* 类全限定名 */ : exceptionTypeNames) {
			// 添加回滚异常
			rollbackRules.add(new RollbackRuleAttribute(StringUtils.trimWhitespace(typeName)));
		}
	}

	/**
	 * 添加不回滚的异常
	 *
	 * @param rollbackRules					RollbackRuleAttribute集合
	 * @param noRollbackForValue			不回滚的异常类
	 */
	private void addNoRollbackRuleAttributesTo(List<RollbackRuleAttribute> rollbackRules, String noRollbackForValue) {
		// 逗号分割成数组，不回滚的异常数组
		String[] exceptionTypeNames = StringUtils.commaDelimitedListToStringArray(noRollbackForValue);
		for (String typeName/* 类全限定名 */ : exceptionTypeNames) {
			// NoRollbackRuleAttribute extends RollbackRuleAttribute
			// 添加不回滚的异常
			rollbackRules.add(new NoRollbackRuleAttribute(StringUtils.trimWhitespace(typeName)));
		}
	}

}
