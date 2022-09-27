package com.springstudy.msb.s_06.selftag;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 标签解析器
 *
 * 1、可以继承AbstractSingleBeanDefinitionParser
 *
 * 重写doParse()是专注于解析标签元素，形成一个bd
 *
 * 2、也可以继承AbstractBeanDefinitionParser
 *
 * 重写parse()，自定义整个解析逻辑。
 *
 * 题外：固有的逻辑是：parse()包含doParse()
 * 题外：AbstractSingleBeanDefinitionParser继承AbstractBeanDefinitionParser
 *
 * @date 2021/12/16 12:31 上午
 */
public class UserBeanDefinitionPaper extends AbstractSingleBeanDefinitionParser {

	/**
	 * ️将解析好的属性放入哪一个对象里面，这个对象是什么类型 —— 返回属性值所对应的对象！
	 * @param element the {@code Element} that is being parsed
	 * @return
	 */
	@Override
	protected Class<?> getBeanClass(Element element) {
		return User.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder/* 通过builder构建一个bd */) {
		/* 解析标签元素值，注入到bd中 */

		String username = element.getAttribute("username");
		String email = element.getAttribute("email");
		String password = element.getAttribute("password");

		if (StringUtils.hasText(username)) {
			builder.addPropertyValue("username", username);
		}
		if (StringUtils.hasText(email)) {
			builder.addPropertyValue("email", email);
		}
		if (StringUtils.hasText(password)) {
			builder.addPropertyValue("password", password);
		}
	}

}