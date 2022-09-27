package com.springstudy.msb.s_06.selftag;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 命名空间处理器
 * @date 2021/12/16 12:30 上午
 */
public class UserNamespaceHandler extends NamespaceHandlerSupport {

	@Override
	public void init() {
		// 参数1：标签
		// 参数2：解析器
		// 例如：<msb:user id="haha" username="zhangsan" email="183qq.com" password="123456"></msb:user>
		registerBeanDefinitionParser("user"/* 标签 */, new UserBeanDefinitionPaper()/* 标签解析器 */);
	}

}