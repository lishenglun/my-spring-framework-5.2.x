package com.springstudy.msb.s_07.selfEditor;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 自定义属性性编辑器，步骤：
 * 1、自定义一个实现了PropertyEditorSupport接口的编辑器
 * 2、让spring能够识别到此编辑器，自定义实现一个属性编辑器的注册器，实现了PropertyEditorRegistrar接口
 * 3、让spring能够识别到对应的注册器
 * @date 2022/4/15 11:33 下午
 */
public class Main {

	public static void main(String[] args) {
		ApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("msb/spring-07-selfEditor.xml");
		Customer customer = (Customer) classPathXmlApplicationContext.getBean("customer");
		System.out.println(customer);
	}

}