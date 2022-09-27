package com.springstudy.Initializethebean.loopReference;

import com.springstudy.config.LoopReferenceConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 测试循坏引用问题
 */
public class LoopReferenceTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LoopReferenceConfig.class);
		//T1 t1 = context.getBean("t1", T1.class);
		//System.out.println(t1);
	}

}