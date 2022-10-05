package com.springstudy.msb.other.condition;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/5 17:36
 */
public class ConditionMain {

	public static void main(String[] args) {
		ApplicationContext ac = new AnnotationConfigApplicationContext(ConditionConfiguration.class);
	}

}