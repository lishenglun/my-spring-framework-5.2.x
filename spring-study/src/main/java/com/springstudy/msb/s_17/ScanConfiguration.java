package com.springstudy.msb.s_17;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/5/15 5:28 下午
 */
@ComponentScan("com.springstudy.msb.s_17")
public class ScanConfiguration {

	/**
	 * 1、测试：
	 *
	 * A当中引入B对象，B当中引入A对象。A的初始化方法中调用了B的初始化方法，B的初始化方法中调用了A的初始化方法，看下会有什么问题！
	 *
	 * 2、结果：
	 *
	 * 先创建A对象，然后发现引用B对象，然后去创建B对象
	 *
	 * 在创建B对象的过程中，会注入A对象，然后B会调用初始化方法
	 *
	 * 调用B初始方法内部，去调用了A对象，因为已经注入了A对象，所以可以调用A的初始化方法
	 *
	 * 调用A的初始化方法，在其内部，调用了B的初始化方法，由于当前还是在执行B的整个初始化过程，所以A当中并没有B对象，
	 * 所以在A的初始化方法里面调用B的初始化方法会报空指针错误！
	 *
	 * 题外：即使A当中有了B对象，这样A的初始化方法中调用了B方法，B的初始化方法中调用了A方法，也将是一个死循环！
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(ScanConfiguration.class);
	}

}