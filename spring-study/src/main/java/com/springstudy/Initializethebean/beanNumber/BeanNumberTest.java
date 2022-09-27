package com.springstudy.Initializethebean.beanNumber;

import com.springstudy.config.BeanNumberConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 2:24 下午
 */
public class BeanNumberTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(BeanNumberConfig.class);
	}



}